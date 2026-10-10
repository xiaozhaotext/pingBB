import hashlib, hmac, secrets, json
from datetime import timedelta
from fastapi import Depends, FastAPI, HTTPException, Request
from fastapi.exceptions import RequestValidationError
from starlette.exceptions import HTTPException as StarletteHTTPException
from starlette.responses import JSONResponse
from fastapi.middleware.cors import CORSMiddleware
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from pydantic import BaseModel, Field, field_validator, ConfigDict
from sqlalchemy import select, func
from sqlalchemy.orm import Session
from .config import settings
from .db import Base, engine, get_db, new_id, now_utc
from .models import User, AdminUser, Merchant, MerchantUser, Role, Permission, RolePermission, UserRole, SessionToken, AuditLog, LoginLog
from .runtime import redis_client, rate_limit

bearer=HTTPBearer(auto_error=False)
app=FastAPI(title=settings.app_name,version="1.0.0",openapi_url="/api/v1/openapi.json",docs_url="/api/v1/docs")
app.add_middleware(CORSMiddleware,allow_origins=[x.strip() for x in settings.cors_origins.split(',')],allow_credentials=True,allow_methods=["*"],allow_headers=["*"])

def audit(db, request, action, actor=None, target_type=None, target_id=None, details=None):
    db.add(AuditLog(actor_id=getattr(actor,"id",None),actor_kind=getattr(actor,"kind","SYSTEM"),merchant_id=getattr(actor,"merchant_id",None),action=action,target_type=target_type,target_id=target_id,request_id=request.state.request_id,details=json.dumps(details or {},separators=(",",":"))))

def pw_hash(password:str)->str:
    salt=secrets.token_bytes(16); digest=hashlib.scrypt(password.encode(),salt=salt,n=2**14,r=8,p=1)
    return "scrypt$"+salt.hex()+"$"+digest.hex()
def pw_verify(password, encoded):
    try:
        algo,salt,expected=encoded.split('$'); actual=hashlib.scrypt(password.encode(),salt=bytes.fromhex(salt),n=2**14,r=8,p=1)
        return algo=="scrypt" and hmac.compare_digest(actual.hex(),expected)
    except Exception:return False
def token_digest(token):return hashlib.sha256(token.encode()).hexdigest()

class RegisterIn(BaseModel):
    email: str=Field(min_length=5,max_length=254)
    password: str=Field(min_length=12,max_length=128)
    display_name: str=Field(default="",max_length=120)
    @field_validator("email")
    @classmethod
    def valid_email(cls,v):
        import re
        if not re.fullmatch(r"[^\s@]+@[^\s@]+\.[^\s@]+",v): raise ValueError("Invalid email")
        return v

class ProfileUpdate(BaseModel):
    model_config=ConfigDict(extra="forbid")
    display_name: str=Field(max_length=120)

class LoginIn(BaseModel):
    username: str=Field(min_length=3,max_length=254)
    password: str=Field(min_length=1,max_length=128)
    merchant_id: str|None=None
class BootstrapIn(BaseModel):
    bootstrap_token: str=Field(min_length=32)
    username: str=Field(min_length=4,max_length=80)
    password: str=Field(min_length=16,max_length=128)
    display_name: str=Field(default="Platform administrator",max_length=120)
class MerchantIn(BaseModel):
    name: str=Field(min_length=2,max_length=160)
    type: str
    status: str="ACTIVE"
    review_status: str="NOT_REQUIRED"
class MerchantUpdate(BaseModel):
    model_config=ConfigDict(extra="forbid")
    name: str|None=Field(default=None,min_length=2,max_length=160)
    status: str|None=None
class EmployeeIn(BaseModel):
    username: str=Field(min_length=4,max_length=80)
    password: str=Field(min_length=16,max_length=128)
    display_name: str=Field(default="",max_length=120)
    role_code: str=Field(default="MERCHANT_STAFF")

class Actor:
    def __init__(self, id, kind, merchant_id=None, superuser=False):self.id=id;self.kind=kind;self.merchant_id=merchant_id;self.superuser=superuser

def current_actor(creds:HTTPAuthorizationCredentials|None=Depends(bearer),db:Session=Depends(get_db)):
    if not creds:raise HTTPException(401,"Authentication required")
    sess=db.scalar(select(SessionToken).where(SessionToken.token_hash==token_digest(creds.credentials)))
    expiry=sess.expires_at if sess and sess.expires_at.tzinfo else (sess.expires_at.replace(tzinfo=__import__("datetime").timezone.utc) if sess else None)
    if not sess or sess.revoked_at or expiry<=now_utc():raise HTTPException(401,"Invalid or expired session")
    if sess.subject_kind=="USER":
        obj=db.get(User,sess.subject_id)
    else:obj=db.get(AdminUser,sess.subject_id)
    if not obj or obj.status!="ACTIVE":raise HTTPException(401,"Invalid or expired session")
    return Actor(obj.id,sess.subject_kind,sess.merchant_id,getattr(obj,"is_superuser",False))

def require_permission(code):
    def dep(request:Request,actor:Actor=Depends(current_actor),db:Session=Depends(get_db)):
        if actor.superuser:return actor
        q=select(Permission.id).join(RolePermission,RolePermission.permission_id==Permission.id).join(Role,Role.id==RolePermission.role_id).join(UserRole,UserRole.role_id==Role.id).where(Permission.code==code,UserRole.user_id==actor.id,UserRole.user_kind==actor.kind)
        if actor.kind=="MERCHANT":q=q.where(UserRole.merchant_id==actor.merchant_id,Role.merchant_id==actor.merchant_id,Permission.code.like("merchant:%"))
        if not db.scalar(q):
            audit(db,request,"AUTHORIZATION_DENIED",actor,"permission",None,{"permission":code,"path":request.url.path})
            db.commit()
            raise HTTPException(403,"Permission denied")
        return actor
    return dep

def issue_session(db, subject, kind, merchant_id=None):
    raw=secrets.token_urlsafe(48); sess=SessionToken(token_hash=token_digest(raw),subject_id=subject.id,subject_kind=kind,merchant_id=merchant_id,expires_at=now_utc()+timedelta(seconds=settings.session_ttl_seconds));db.add(sess);db.flush();return raw

@app.middleware("http")
async def request_context(request:Request,call_next):
    import logging,time
    request.state.request_id=request.headers.get("x-request-id") or new_id();started=time.perf_counter()
    response=await call_next(request);response.headers["X-Request-ID"]=request.state.request_id
    logging.getLogger("api.access").info("request_complete request_id=%s method=%s path=%s status=%s duration_ms=%.2f",request.state.request_id,request.method,request.url.path,response.status_code,(time.perf_counter()-started)*1000)
    return response
@app.exception_handler(StarletteHTTPException)
async def http_error(request, exc):
    return JSONResponse(status_code=exc.status_code,content={"ok":False,"error":{"code":"HTTP_ERROR","message":str(exc.detail)},"request_id":getattr(request.state,"request_id",None)},headers=getattr(exc,"headers",None))

@app.exception_handler(RequestValidationError)
async def validation_error(request, exc):
    return JSONResponse(status_code=422,content={"ok":False,"error":{"code":"VALIDATION_ERROR","message":"Request validation failed"},"request_id":getattr(request.state,"request_id",None)})

@app.exception_handler(Exception)
async def unknown_error(request, exc):
    import logging
    logging.getLogger("api").exception("unhandled_error request_id=%s",getattr(request.state,"request_id","-"))
    return JSONResponse(status_code=500,content={"ok":False,"error":{"code":"INTERNAL_ERROR","message":"Internal server error"},"request_id":getattr(request.state,"request_id",None)})
def ok(request,data):return {"ok":True,"data":data,"request_id":request.state.request_id}

@app.get("/healthz")
def health(request:Request):return ok(request,{"status":"ok"})
@app.get("/readyz")
def ready(request:Request,db:Session=Depends(get_db)):
    try:db.execute(select(1))
    except Exception:raise HTTPException(503,"Database unavailable")
    if settings.redis_required and not redis_client.ping():raise HTTPException(503,"Redis unavailable")
    return ok(request,{"status":"ready","database":"ok","redis":"ok" if redis_client.ping() else "degraded"})

@app.post("/api/v1/auth/register",status_code=201)
def register(data:RegisterIn,request:Request,db:Session=Depends(get_db)):
    if not rate_limit("register:"+request.client.host if request.client else "register:unknown",5,3600):raise HTTPException(429,"Try again later")
    if db.scalar(select(User).where(func.lower(User.email)==data.email.lower())):raise HTTPException(409,"Account unavailable")
    obj=User(email=data.email.lower(),password_hash=pw_hash(data.password),display_name=data.display_name);db.add(obj);db.flush();token=issue_session(db,obj,"USER");db.add(LoginLog(subject_id=obj.id,subject_kind="USER",login_hint_hash=token_digest(data.email.lower()),success=True,request_id=request.state.request_id));audit(db,request,"USER_REGISTER",Actor(obj.id,"USER"),"user",obj.id);db.commit();return ok(request,{"access_token":token,"token_type":"bearer","user":{"id":obj.id,"email":obj.email,"display_name":obj.display_name}})

def login_common(db,request,username,password,kind,merchant_id=None):
    key=f"login:{kind}:{request.client.host if request.client else 'unknown'}:{token_digest(username.lower())}"
    if not rate_limit(key,settings.login_rate_limit,settings.login_rate_window_seconds):raise HTTPException(429,"Try again later")
    if kind=="USER":obj=db.scalar(select(User).where(func.lower(User.email)==username.lower()))
    else:obj=db.scalar(select(AdminUser).where(func.lower(AdminUser.username)==username.lower()))
    good=bool(obj and obj.status=="ACTIVE" and pw_verify(password,obj.password_hash))
    db.add(LoginLog(subject_id=obj.id if obj else None,subject_kind=kind,login_hint_hash=token_digest(username.lower()),success=good,request_id=request.state.request_id))
    if not good: audit(db,request,"LOGIN_FAILED",None,"identity",None,{"kind":kind});db.commit();raise HTTPException(401,"Invalid credentials")
    if kind=="MERCHANT":
        link=db.scalar(select(MerchantUser).where(MerchantUser.user_id==obj.id,MerchantUser.merchant_id==merchant_id,MerchantUser.status=="ACTIVE"))
        if not link:db.commit();raise HTTPException(401,"Invalid credentials")
    token=issue_session(db,obj,kind,merchant_id);actor=Actor(obj.id,kind,merchant_id,getattr(obj,"is_superuser",False));audit(db,request,"LOGIN_SUCCESS",actor,"identity",obj.id);db.commit();return ok(request,{"access_token":token,"token_type":"bearer","identity":{"id":obj.id,"kind":kind,"merchant_id":merchant_id,"display_name":obj.display_name}})

@app.post("/api/v1/auth/login")
def user_login(data:LoginIn,request:Request,db:Session=Depends(get_db)):return login_common(db,request,data.username,data.password,"USER")
@app.post("/api/v1/admin/auth/login")
def admin_login(data:LoginIn,request:Request,db:Session=Depends(get_db)):return login_common(db,request,data.username,data.password,"ADMIN")
@app.post("/api/v1/merchant/auth/login")
def merchant_login(data:LoginIn,request:Request,db:Session=Depends(get_db)):
    obj=db.scalar(select(AdminUser).where(func.lower(AdminUser.username)==data.username.lower()))
    links=list(db.scalars(select(MerchantUser).where(MerchantUser.user_id==obj.id,MerchantUser.status=="ACTIVE"))) if obj else []
    if data.merchant_id:
        link=next((x for x in links if x.merchant_id==data.merchant_id),None)
    else:
        link=links[0] if len(links)==1 else None
    if not link:
        db.add(LoginLog(subject_id=obj.id if obj else None,subject_kind="MERCHANT",login_hint_hash=token_digest(data.username.lower()),success=False,request_id=request.state.request_id));db.commit();raise HTTPException(401,"Invalid credentials")
    return login_common(db,request,data.username,data.password,"MERCHANT",link.merchant_id)
@app.get("/api/v1/auth/me")
def me(request:Request,actor:Actor=Depends(current_actor),db:Session=Depends(get_db)):
    obj=db.get(User if actor.kind=="USER" else AdminUser,actor.id)
    return ok(request,{"id":actor.id,"kind":actor.kind,"merchant_id":actor.merchant_id,"username":getattr(obj,"username",None),"email":getattr(obj,"email",None),"display_name":obj.display_name,"is_superuser":actor.superuser})
@app.patch("/api/v1/users/me")
def update_user_profile(data:ProfileUpdate,request:Request,actor:Actor=Depends(current_actor),db:Session=Depends(get_db)):
    if actor.kind!="USER":raise HTTPException(403,"Consumer identity required")
    obj=db.get(User,actor.id);obj.display_name=data.display_name
    audit(db,request,"USER_PROFILE_UPDATED",actor,"user",actor.id);db.commit();return ok(request,{"id":obj.id,"email":obj.email,"display_name":obj.display_name})

@app.post("/api/v1/auth/logout")
def logout(request:Request,creds:HTTPAuthorizationCredentials=Depends(bearer),actor:Actor=Depends(current_actor),db:Session=Depends(get_db)):
    sess=db.scalar(select(SessionToken).where(SessionToken.token_hash==token_digest(creds.credentials)));sess.revoked_at=now_utc();audit(db,request,"LOGOUT",actor,"session",sess.id);db.commit();return ok(request,{"logged_out":True})
@app.post("/api/v1/auth/change-password")
def change_password(data:dict,request:Request,creds:HTTPAuthorizationCredentials=Depends(bearer),actor:Actor=Depends(current_actor),db:Session=Depends(get_db)):
    old=data.get("old_password","");new=data.get("new_password","")
    if len(new)<12 or len(new)>128:raise HTTPException(422,"New password must contain 12 to 128 characters")
    obj=db.get(User if actor.kind=="USER" else AdminUser,actor.id)
    if not pw_verify(old,obj.password_hash):raise HTTPException(400,"Current password incorrect")
    obj.password_hash=pw_hash(new)
    for sess in db.scalars(select(SessionToken).where(SessionToken.subject_id==actor.id,SessionToken.revoked_at.is_(None))):sess.revoked_at=now_utc()
    audit(db,request,"PASSWORD_CHANGED",actor,"identity",actor.id);db.commit();return ok(request,{"changed":True})

@app.post("/api/v1/admin/bootstrap",status_code=201)
def bootstrap(data:BootstrapIn,request:Request,db:Session=Depends(get_db)):
    if not settings.bootstrap_token or not hmac.compare_digest(settings.bootstrap_token,data.bootstrap_token):raise HTTPException(404,"Not found")
    if db.scalar(select(AdminUser.id).limit(1)):raise HTTPException(409,"Bootstrap already completed")
    admin=AdminUser(username=data.username,password_hash=pw_hash(data.password),display_name=data.display_name,is_superuser=True);db.add(admin);db.flush()
    for code,name,scope in [("SUPER_ADMIN","超级管理员","PLATFORM"),("PLATFORM_OPS","平台运营","PLATFORM"),("PLATFORM_FINANCE","平台财务","PLATFORM"),("MERCHANT_ADMIN","商家管理员","MERCHANT"),("MERCHANT_STAFF","商家员工","MERCHANT")]:
        if not db.scalar(select(Role).where(Role.code==code)):db.add(Role(code=code,name=name,scope=scope))
    for code,resource,action,name,kind in [("admin:users:read","users","read","用户管理","API"),("admin:users:write","users","write","用户状态管理","API"),("admin:admins:manage","admins","manage","管理员账户管理","API"),("admin:roles:manage","roles","manage","角色权限管理","API"),("admin:merchants:read","merchants","read","商家查看","API"),("admin:merchants:write","merchants","write","商家管理","API"),("admin:logs:read","logs","read","日志查看","API"),("merchant:profile:read","merchant","read","商家资料查看","API"),("merchant:profile:write","merchant","write","商家资料修改","API"),("merchant:employees:manage","employees","manage","商家员工管理","API")]:
        if not db.scalar(select(Permission).where(Permission.code==code)):db.add(Permission(code=code,resource=resource,action=action,name=name,kind=kind))
    db.flush();r=db.scalar(select(Role).where(Role.code=="SUPER_ADMIN"));
    for p in db.scalars(select(Permission)):db.add(RolePermission(role_id=r.id,permission_id=p.id))
    db.add(UserRole(user_kind="ADMIN",user_id=admin.id,role_id=r.id));audit(db,request,"BOOTSTRAP_SUPERADMIN",Actor(admin.id,"ADMIN",superuser=True),"admin_user",admin.id);db.commit();return ok(request,{"created":True,"username":admin.username})

@app.get("/api/v1/admin/users",dependencies=[Depends(require_permission("admin:users:read"))])
def admin_users(request:Request,db:Session=Depends(get_db)):
    rows=db.scalars(select(User).order_by(User.created_at.desc()).limit(200)).all();return ok(request,[{"id":x.id,"email":x.email,"display_name":x.display_name,"status":x.status,"created_at":x.created_at.isoformat()} for x in rows])
@app.patch("/api/v1/admin/users/{user_id}/status",dependencies=[Depends(require_permission("admin:users:write"))])
def set_user_status(user_id:str,data:dict,request:Request,actor:Actor=Depends(current_actor),db:Session=Depends(get_db)):
    obj=db.get(User,user_id)
    if not obj:raise HTTPException(404,"Not found")
    status_value=data.get("status")
    if status_value not in ("ACTIVE","DISABLED"):raise HTTPException(422,"Invalid status")
    obj.status=status_value
    if status_value=="DISABLED":
        for s in db.scalars(select(SessionToken).where(SessionToken.subject_id==user_id,SessionToken.subject_kind=="USER",SessionToken.revoked_at.is_(None))):s.revoked_at=now_utc()
    audit(db,request,"USER_STATUS_CHANGED",actor,"user",user_id,{"status":status_value});db.commit();return ok(request,{"id":obj.id,"status":obj.status})


@app.put("/api/v1/admin/users/{user_id}/roles",dependencies=[Depends(require_permission("admin:roles:manage"))])
def bind_user_roles(user_id:str,data:dict,request:Request,actor:Actor=Depends(current_actor),db:Session=Depends(get_db)):
    user=db.get(User,user_id)
    if not user:raise HTTPException(404,"Not found")
    role_ids=set(data.get("role_ids",[]));roles=list(db.scalars(select(Role).where(Role.id.in_(role_ids),Role.scope=="PLATFORM",Role.merchant_id.is_(None)))) if role_ids else []
    if len(roles)!=len(role_ids):raise HTTPException(422,"Unknown or out-of-scope role")
    db.query(UserRole).filter_by(user_kind="USER",user_id=user_id,merchant_id=None).delete()
    for role in roles:db.add(UserRole(user_kind="USER",user_id=user_id,role_id=role.id))
    audit(db,request,"USER_ROLES_CHANGED",actor,"user",user_id,{"role_ids":list(role_ids)});db.commit();return ok(request,{"updated":True})

@app.get("/api/v1/admin/me/profile")
def admin_profile(request:Request,actor:Actor=Depends(current_actor),db:Session=Depends(get_db)):
    if actor.kind!="ADMIN":raise HTTPException(403,"Platform identity required")
    obj=db.get(AdminUser,actor.id);return ok(request,{"id":obj.id,"username":obj.username,"display_name":obj.display_name})
@app.patch("/api/v1/admin/me/profile")
def update_admin_profile(data:dict,request:Request,actor:Actor=Depends(current_actor),db:Session=Depends(get_db)):
    if actor.kind!="ADMIN":raise HTTPException(403,"Platform identity required")
    obj=db.get(AdminUser,actor.id);name=data.get("display_name")
    if not isinstance(name,str) or len(name)>120:raise HTTPException(422,"Invalid display name")
    obj.display_name=name;audit(db,request,"ADMIN_PROFILE_UPDATED",actor,"admin_user",actor.id);db.commit();return ok(request,{"id":obj.id,"username":obj.username,"display_name":obj.display_name})

@app.post("/api/v1/admin/roles",status_code=201,dependencies=[Depends(require_permission("admin:roles:manage"))])
def create_role(data:dict,request:Request,actor:Actor=Depends(current_actor),db:Session=Depends(get_db)):
    code=data.get("code","");name=data.get("name","");scope=data.get("scope","PLATFORM")
    if not isinstance(code,str) or not code.replace("_","").isalnum() or len(code)>80 or not isinstance(name,str) or not name or len(name)>120 or scope!="PLATFORM":raise HTTPException(422,"Invalid platform role")
    if db.scalar(select(Role.id).where(Role.code==code,Role.merchant_id.is_(None))):raise HTTPException(409,"Role code unavailable")
    role=Role(code=code,name=name,scope="PLATFORM");db.add(role);db.flush();audit(db,request,"ROLE_CREATED",actor,"role",role.id);db.commit();return ok(request,{"id":role.id,"code":role.code,"name":role.name})

@app.get("/api/v1/admin/admin-users",dependencies=[Depends(require_permission("admin:admins:manage"))])
def list_admin_users(request:Request,db:Session=Depends(get_db)):
    rows=db.scalars(select(AdminUser).order_by(AdminUser.created_at.desc()).limit(200)).all()
    return ok(request,[{"id":x.id,"username":x.username,"display_name":x.display_name,"status":x.status,"is_superuser":x.is_superuser,"created_at":x.created_at.isoformat()} for x in rows])
@app.post("/api/v1/admin/admin-users",status_code=201,dependencies=[Depends(require_permission("admin:admins:manage"))])
def create_admin_user(data:dict,request:Request,actor:Actor=Depends(current_actor),db:Session=Depends(get_db)):
    username=data.get("username","");password=data.get("password","");name=data.get("display_name","");role_ids=set(data.get("role_ids",[]))
    if not isinstance(username,str) or len(username)<4 or len(username)>80 or not isinstance(password,str) or len(password)<16 or len(password)>128 or not isinstance(name,str) or len(name)>120:raise HTTPException(422,"Invalid administrator data")
    roles=list(db.scalars(select(Role).where(Role.id.in_(role_ids),Role.scope=="PLATFORM",Role.merchant_id.is_(None)))) if role_ids else []
    if not roles or len(roles)!=len(role_ids):raise HTTPException(422,"At least one valid platform role is required")
    if db.scalar(select(AdminUser.id).where(AdminUser.username==username)):raise HTTPException(409,"Account unavailable")
    obj=AdminUser(username=username,password_hash=pw_hash(password),display_name=name,is_superuser=False);db.add(obj);db.flush()
    for role in roles:db.add(UserRole(user_kind="ADMIN",user_id=obj.id,role_id=role.id))
    audit(db,request,"ADMIN_USER_CREATED",actor,"admin_user",obj.id,{"role_ids":list(role_ids)});db.commit();return ok(request,{"id":obj.id,"username":obj.username,"display_name":obj.display_name})

@app.get("/api/v1/admin/roles",dependencies=[Depends(require_permission("admin:roles:manage"))])
def roles(request:Request,db:Session=Depends(get_db)):
    return ok(request,[{"id":r.id,"code":r.code,"name":r.name,"scope":r.scope,"merchant_id":r.merchant_id} for r in db.scalars(select(Role).order_by(Role.code))])
@app.get("/api/v1/admin/permissions",dependencies=[Depends(require_permission("admin:roles:manage"))])
def permissions(request:Request,db:Session=Depends(get_db)):
    return ok(request,[{"id":p.id,"code":p.code,"name":p.name,"kind":p.kind} for p in db.scalars(select(Permission).order_by(Permission.code))])
@app.get("/api/v1/admin/roles/{role_id}/permissions",dependencies=[Depends(require_permission("admin:roles:manage"))])
def get_role_permissions(role_id:str,request:Request,db:Session=Depends(get_db)):
    role=db.get(Role,role_id)
    if not role:raise HTTPException(404,"Not found")
    ids=list(db.scalars(select(RolePermission.permission_id).where(RolePermission.role_id==role_id)))
    return ok(request,{"permission_ids":ids})

@app.put("/api/v1/admin/roles/{role_id}/permissions",dependencies=[Depends(require_permission("admin:roles:manage"))])
def set_role_permissions(role_id:str,data:dict,request:Request,actor:Actor=Depends(current_actor),db:Session=Depends(get_db)):
    role=db.get(Role,role_id);ids=set(data.get("permission_ids",[]))
    if not role:raise HTTPException(404,"Not found")
    if role.code=="SUPER_ADMIN":raise HTTPException(409,"Super administrator permissions are immutable")
    perms=list(db.scalars(select(Permission).where(Permission.id.in_(ids)))) if ids else []
    if len(perms)!=len(ids):raise HTTPException(422,"Unknown permission")
    if role.scope=="MERCHANT" and any(not p.code.startswith("merchant:") for p in perms):raise HTTPException(422,"Merchant roles cannot receive platform permissions")
    db.query(RolePermission).filter_by(role_id=role_id).delete()
    for p in perms:db.add(RolePermission(role_id=role_id,permission_id=p.id))
    audit(db,request,"ROLE_PERMISSIONS_CHANGED",actor,"role",role_id,{"permission_ids":list(ids)});db.commit();return ok(request,{"updated":True})

@app.get("/api/v1/admin/merchants",dependencies=[Depends(require_permission("admin:merchants:read"))])
def list_merchants(request:Request,db:Session=Depends(get_db)):
    return ok(request,[{"id":m.id,"name":m.name,"type":m.type,"status":m.status,"review_status":m.review_status,"created_at":m.created_at.isoformat()} for m in db.scalars(select(Merchant).order_by(Merchant.created_at.desc()))])
@app.post("/api/v1/admin/merchants",status_code=201,dependencies=[Depends(require_permission("admin:merchants:write"))])
def create_merchant(data:MerchantIn,request:Request,actor:Actor=Depends(current_actor),db:Session=Depends(get_db)):
    if data.type not in ("PLATFORM_SELF","THIRD_PARTY"):raise HTTPException(422,"Invalid merchant type")
    obj=Merchant(**data.model_dump());db.add(obj);db.flush()
    perms=list(db.scalars(select(Permission).where(Permission.code.in_(["merchant:profile:read","merchant:profile:write","merchant:employees:manage"]))))
    for code,name in [("MERCHANT_ADMIN","商家管理员"),("MERCHANT_STAFF","商家员工")]:
        role=Role(code=code,name=name,scope="MERCHANT",merchant_id=obj.id);db.add(role);db.flush()
        for perm in perms:
            if code=="MERCHANT_ADMIN" or perm.code=="merchant:profile:read":db.add(RolePermission(role_id=role.id,permission_id=perm.id))
    audit(db,request,"MERCHANT_CREATED",actor,"merchant",obj.id);db.commit();return ok(request,{"id":obj.id,"name":obj.name,"type":obj.type,"status":obj.status})
@app.patch("/api/v1/admin/merchants/{merchant_id}",dependencies=[Depends(require_permission("admin:merchants:write"))])
def update_merchant(merchant_id:str,data:MerchantUpdate,request:Request,actor:Actor=Depends(current_actor),db:Session=Depends(get_db)):
    obj=db.get(Merchant,merchant_id)
    if not obj:raise HTTPException(404,"Not found")
    for k,v in data.model_dump(exclude_unset=True).items():
        if v is not None:setattr(obj,k,v)
    audit(db,request,"MERCHANT_UPDATED",actor,"merchant",obj.id);db.commit();return ok(request,{"id":obj.id,"name":obj.name,"type":obj.type,"status":obj.status})

@app.post("/api/v1/admin/merchants/{merchant_id}/employees",status_code=201,dependencies=[Depends(require_permission("admin:merchants:write"))])
def admin_add_merchant_employee(merchant_id:str,data:EmployeeIn,request:Request,actor:Actor=Depends(current_actor),db:Session=Depends(get_db)):
    merchant=db.get(Merchant,merchant_id)
    if not merchant:raise HTTPException(404,"Not found")
    role=db.scalar(select(Role).where(Role.code==data.role_code,Role.scope=="MERCHANT",Role.merchant_id==merchant_id))
    if not role:raise HTTPException(422,"Merchant role not configured")
    if db.scalar(select(AdminUser.id).where(AdminUser.username==data.username)):raise HTTPException(409,"Account unavailable")
    obj=AdminUser(username=data.username,password_hash=pw_hash(data.password),display_name=data.display_name);db.add(obj);db.flush()
    db.add(MerchantUser(merchant_id=merchant_id,user_id=obj.id));db.add(UserRole(user_kind="MERCHANT",user_id=obj.id,role_id=role.id,merchant_id=merchant_id))
    audit(db,request,"MERCHANT_EMPLOYEE_CREATED",actor,"admin_user",obj.id,{"merchant_id":merchant_id});db.commit();return ok(request,{"id":obj.id,"username":obj.username,"merchant_id":merchant_id})

@app.get("/api/v1/merchant/profile")
def merchant_profile(request:Request,actor:Actor=Depends(require_permission("merchant:profile:read")),db:Session=Depends(get_db)):
    obj=db.get(Merchant,actor.merchant_id)
    if not obj:raise HTTPException(404,"Not found")
    return ok(request,{"id":obj.id,"name":obj.name,"type":obj.type,"status":obj.status,"review_status":obj.review_status})
@app.patch("/api/v1/merchant/profile")
def merchant_profile_update(data:MerchantUpdate,request:Request,actor:Actor=Depends(require_permission("merchant:profile:write")),db:Session=Depends(get_db)):
    obj=db.get(Merchant,actor.merchant_id)
    if not obj:raise HTTPException(404,"Not found")
    for k,v in data.model_dump(exclude_unset=True).items():
        if v is not None:setattr(obj,k,v)
    audit(db,request,"MERCHANT_PROFILE_UPDATED",actor,"merchant",obj.id);db.commit();return ok(request,{"id":obj.id,"name":obj.name,"status":obj.status})
@app.get("/api/v1/merchant/employees")
def employees(request:Request,actor:Actor=Depends(require_permission("merchant:employees:manage")),db:Session=Depends(get_db)):
    q=select(AdminUser,MerchantUser).join(MerchantUser,MerchantUser.user_id==AdminUser.id).where(MerchantUser.merchant_id==actor.merchant_id)
    return ok(request,[{"id":a.id,"username":a.username,"display_name":a.display_name,"status":l.status} for a,l in db.execute(q)])
@app.patch("/api/v1/merchant/employees/{employee_id}/role")
def update_employee_role(employee_id:str,data:dict,request:Request,actor:Actor=Depends(require_permission("merchant:employees:manage")),db:Session=Depends(get_db)):
    link=db.scalar(select(MerchantUser).where(MerchantUser.user_id==employee_id,MerchantUser.merchant_id==actor.merchant_id,MerchantUser.status=="ACTIVE"))
    if not link:raise HTTPException(404,"Not found")
    role=db.scalar(select(Role).where(Role.code==data.get("role_code"),Role.scope=="MERCHANT",Role.merchant_id==actor.merchant_id))
    if not role:raise HTTPException(422,"Merchant role not configured")
    db.query(UserRole).filter_by(user_kind="MERCHANT",user_id=employee_id,merchant_id=actor.merchant_id).delete()
    db.add(UserRole(user_kind="MERCHANT",user_id=employee_id,role_id=role.id,merchant_id=actor.merchant_id));audit(db,request,"MERCHANT_EMPLOYEE_ROLE_CHANGED",actor,"admin_user",employee_id,{"role_code":role.code});db.commit();return ok(request,{"updated":True})
@app.patch("/api/v1/merchant/employees/{employee_id}/status")
def update_employee_status(employee_id:str,data:dict,request:Request,actor:Actor=Depends(require_permission("merchant:employees:manage")),db:Session=Depends(get_db)):
    link=db.scalar(select(MerchantUser).where(MerchantUser.user_id==employee_id,MerchantUser.merchant_id==actor.merchant_id))
    if not link:raise HTTPException(404,"Not found")
    new_status=data.get("status")
    if new_status not in ("ACTIVE","DISABLED"):raise HTTPException(422,"Invalid status")
    link.status=new_status
    if new_status=="DISABLED":
        for sess in db.scalars(select(SessionToken).where(SessionToken.subject_id==employee_id,SessionToken.subject_kind=="MERCHANT",SessionToken.merchant_id==actor.merchant_id,SessionToken.revoked_at.is_(None))):sess.revoked_at=now_utc()
    audit(db,request,"MERCHANT_EMPLOYEE_STATUS_CHANGED",actor,"admin_user",employee_id,{"status":new_status});db.commit();return ok(request,{"updated":True})

@app.post("/api/v1/merchant/employees",status_code=201)
def add_employee(data:EmployeeIn,request:Request,actor:Actor=Depends(require_permission("merchant:employees:manage")),db:Session=Depends(get_db)):
    role=db.scalar(select(Role).where(Role.code==data.role_code,Role.scope=="MERCHANT",Role.merchant_id==actor.merchant_id))
    if not role:raise HTTPException(422,"Merchant role not configured")
    if db.scalar(select(AdminUser.id).where(AdminUser.username==data.username)):raise HTTPException(409,"Account unavailable")
    obj=AdminUser(username=data.username,password_hash=pw_hash(data.password),display_name=data.display_name);db.add(obj);db.flush();link=MerchantUser(merchant_id=actor.merchant_id,user_id=obj.id);db.add(link);db.flush();db.add(UserRole(user_kind="MERCHANT",user_id=obj.id,role_id=role.id,merchant_id=actor.merchant_id));audit(db,request,"MERCHANT_EMPLOYEE_CREATED",actor,"admin_user",obj.id);db.commit();return ok(request,{"id":obj.id,"username":obj.username,"merchant_id":actor.merchant_id})
@app.get("/api/v1/admin/login-logs",dependencies=[Depends(require_permission("admin:logs:read"))])
def login_logs(request:Request,db:Session=Depends(get_db)):
    rows=db.scalars(select(LoginLog).order_by(LoginLog.created_at.desc()).limit(200)).all();return ok(request,[{"subject_id":x.subject_id,"subject_kind":x.subject_kind,"success":x.success,"request_id":x.request_id,"created_at":x.created_at.isoformat()} for x in rows])
@app.get("/api/v1/admin/audit-logs",dependencies=[Depends(require_permission("admin:logs:read"))])
def audit_logs(request:Request,db:Session=Depends(get_db)):
    rows=db.scalars(select(AuditLog).order_by(AuditLog.created_at.desc()).limit(200)).all();return ok(request,[{"id":x.id,"actor_id":x.actor_id,"actor_kind":x.actor_kind,"action":x.action,"target_type":x.target_type,"target_id":x.target_id,"request_id":x.request_id,"created_at":x.created_at.isoformat()} for x in rows])
@app.get("/api/v1/admin/me")
def admin_me(request:Request,actor:Actor=Depends(current_actor)):
    if actor.kind!="ADMIN":raise HTTPException(403,"Platform identity required")
    return ok(request,{"id":actor.id,"is_superuser":actor.superuser})

# Generic ownership contract for future modules. No product/order/etc. endpoints are fabricated here.
def assert_merchant_scope(actor:Actor, resource_merchant_id:str):
    if actor.kind!="MERCHANT" or not actor.merchant_id or actor.merchant_id!=resource_merchant_id:raise HTTPException(404,"Not found")







