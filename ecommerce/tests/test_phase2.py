import os, sys
from pathlib import Path
import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine, select, event
from sqlalchemy.orm import sessionmaker
from alembic.config import Config
from alembic import command
sys.path.insert(0,str(Path(__file__).parents[1]/'apps'/'api'))
from app.db import Base, get_db
from app.main import app, pw_hash, pw_verify
from app.models import AdminUser, User, Merchant, MerchantUser, Role, Permission, RolePermission, UserRole, AuditLog, SessionToken
from app.config import settings
from app.runtime import redis_client, rate_limit
from app import runtime

@pytest.fixture()
def ctx(tmp_path, monkeypatch):
    runtime._local.clear()
    db_url=f"sqlite:///{tmp_path/'test.db'}"
    monkeypatch.setattr(settings,"database_url",db_url)
    engine=create_engine(db_url,connect_args={"check_same_thread":False})
    @event.listens_for(engine,"connect")
    def enable_sqlite_foreign_keys(connection,_):connection.execute("PRAGMA foreign_keys=ON")
    api_dir=Path(__file__).parents[1]/"apps"/"api"
    alembic_cfg=Config(str(api_dir/"alembic.ini"))
    alembic_cfg.set_main_option("script_location",str(api_dir/"alembic"))
    command.upgrade(alembic_cfg,"head")
    command.upgrade(alembic_cfg,"head")
    TestingSession=sessionmaker(bind=engine,autoflush=False,expire_on_commit=False)
    def override():
        db=TestingSession()
        try:yield db
        finally:db.close()
    app.dependency_overrides[get_db]=override
    monkeypatch.setattr(settings,"bootstrap_token","a-test-bootstrap-token-with-at-least-32-bytes")
    with TestClient(app) as client:
        with TestingSession() as db:
            for code,name,scope in [("MERCHANT_ADMIN","Merchant administrator","MERCHANT"),("MERCHANT_STAFF","Merchant staff","MERCHANT")]:db.add(Role(code=code,name=name,scope=scope))
            for code,res,action in [("merchant:profile:read","merchant","read"),("merchant:profile:write","merchant","write"),("merchant:employees:manage","employees","manage")]:db.add(Permission(code=code,name=code,resource=res,action=action))
            db.commit()
        yield client,TestingSession
    app.dependency_overrides.clear();Base.metadata.drop_all(engine);engine.dispose()

def setup_world(client,Session):
    token="a-test-bootstrap-token-with-at-least-32-bytes"
    r=client.post('/api/v1/admin/bootstrap',json={"bootstrap_token":token,"username":"rootadmin","password":"a-very-strong-admin-password","display_name":"Root"});assert r.status_code==201,r.text
    ar=client.post('/api/v1/admin/auth/login',json={"username":"rootadmin","password":"a-very-strong-admin-password"});assert ar.status_code==200
    auth={"Authorization":"Bearer "+ar.json()['data']['access_token']}
    with Session() as db:
        a=Merchant(name="Merchant A",type="THIRD_PARTY");b=Merchant(name="Merchant B",type="THIRD_PARTY");db.add_all([a,b]);db.flush()
        perms=list(db.scalars(select(Permission).where(Permission.code.like("merchant:%"))))
        users=[]
        for merchant,username in [(a,"merchantA"),(b,"merchantB")]:
            role=Role(code="MERCHANT_ADMIN",name="Merchant administrator",scope="MERCHANT",merchant_id=merchant.id);db.add(role);db.flush()
            for p in perms:db.add(RolePermission(role_id=role.id,permission_id=p.id))
            u=AdminUser(username=username,password_hash=pw_hash("a-strong-password-for-test"),display_name=username);db.add(u);db.flush();db.add(MerchantUser(merchant_id=merchant.id,user_id=u.id));db.add(UserRole(user_kind="MERCHANT",user_id=u.id,role_id=role.id,merchant_id=merchant.id));users.append((merchant.id,u.id))
        db.commit();return auth,users

def mlogin(client,username):
    r=client.post('/api/v1/merchant/auth/login',json={"username":username,"password":"a-strong-password-for-test"});assert r.status_code==200,r.text
    return {"Authorization":"Bearer "+r.json()['data']['access_token']}

def test_health_and_readiness(ctx):
    c,_=ctx;assert c.get('/healthz').json()['data']['status']=='ok';assert c.get('/readyz').status_code==200

def test_bootstrap_and_admin_login(ctx):
    c,S=ctx;setup_world(c,S);assert c.post('/api/v1/admin/bootstrap',json={"bootstrap_token":"a-test-bootstrap-token-with-at-least-32-bytes","username":"otheradmin","password":"a-very-strong-admin-password"}).status_code==409

def test_registration_login_and_bad_password(ctx):
    c,_=ctx;r=c.post('/api/v1/auth/register',json={"email":"person@example.test","password":"a-very-strong-user-password","display_name":"Person"});assert r.status_code==201
    assert c.get('/api/v1/auth/me',headers={"Authorization":"Bearer "+r.json()['data']['access_token']}).status_code==200
    assert c.post('/api/v1/auth/login',json={"username":"person@example.test","password":"wrong-password"}).status_code==401
    assert c.post('/api/v1/auth/register',json={"email":"person@example.test","password":"a-very-strong-user-password"}).status_code==409

def test_password_hash_is_salted_and_not_reversible():
    first=pw_hash("correct horse battery staple");second=pw_hash("correct horse battery staple")
    assert first!="correct horse battery staple" and first!=second
    assert pw_verify("correct horse battery staple",first) and not pw_verify("wrong",first)

def test_invalid_registration_input_has_uniform_validation_error(ctx):
    c,_=ctx;r=c.post('/api/v1/auth/register',json={"email":"not-an-email","password":"short"})
    assert r.status_code==422 and r.json()['error']['code']=="VALIDATION_ERROR"

def test_logout_revokes_session(ctx,monkeypatch):
    c,_=ctx;r=c.post('/api/v1/auth/register',json={"email":"logout@example.test","password":"a-very-strong-user-password"});h={"Authorization":"Bearer "+r.json()['data']['access_token']};assert c.post('/api/v1/auth/logout',headers=h).status_code==200
    monkeypatch.setattr(settings,"redis_required",True);monkeypatch.setattr(redis_client,"client",None)
    assert c.get('/api/v1/auth/me',headers=h).status_code==401

def test_sqlite_migration_enables_foreign_keys_and_utf8(ctx):
    _,Session=ctx
    with Session() as db:
        assert db.connection().exec_driver_sql("PRAGMA foreign_keys").scalar()==1
        assert db.connection().exec_driver_sql("PRAGMA encoding").scalar().upper()=="UTF-8"

def test_admin_user_api_requires_auth(ctx):
    c,_=ctx;assert c.get('/api/v1/admin/users').status_code==401

def test_merchant_a_cannot_read_or_modify_b(ctx):
    c,S=ctx;admin,(a,b)=setup_world(c,S);ah=mlogin(c,"merchantA");hb=mlogin(c,"merchantB")
    own=c.get('/api/v1/merchant/profile',headers=ah);assert own.status_code==200 and own.json()['data']['id']==a[0]
    # Future cross-resource contract: caller-provided B identity is never used as authority.
    assert c.patch('/api/v1/merchant/profile',headers=ah,json={"merchant_id":b[0],"name":"Hacked"}).status_code==422
    assert c.get('/api/v1/merchant/profile',headers=hb).json()['data']['id']==b[0]
    with S() as db:assert db.get(Merchant,b[0]).name=="Merchant B"

def test_merchant_employee_list_is_scoped(ctx):
    c,S=ctx;_,(a,b)=setup_world(c,S);ha=mlogin(c,"merchantA");hb=mlogin(c,"merchantB")
    assert {x['username'] for x in c.get('/api/v1/merchant/employees',headers=ha).json()['data']}=={"merchantA"}
    assert {x['username'] for x in c.get('/api/v1/merchant/employees',headers=hb).json()['data']}=={"merchantB"}

def test_merchant_cannot_call_platform_admin_api(ctx):
    c,S=ctx;setup_world(c,S);ha=mlogin(c,"merchantA");assert c.get('/api/v1/admin/merchants',headers=ha).status_code==403

def test_merchant_role_bound_to_merchant_id(ctx):
    c,S=ctx;_,(a,b)=setup_world(c,S);ha=mlogin(c,"merchantA")
    # Tenant identity comes from the session; a forged header cannot switch it.
    r=c.get('/api/v1/merchant/profile',headers={**ha,"X-Merchant-ID":b[0]});assert r.json()['data']['id']==a[0]

def test_denied_platform_permission_is_audited(ctx):
    c,S=ctx;setup_world(c,S);ha=mlogin(c,"merchantA")
    r=c.get('/api/v1/admin/merchants',headers=ha);assert r.status_code==403
    with S() as db:assert db.scalar(select(AuditLog).where(AuditLog.action=="AUTHORIZATION_DENIED")) is not None

def test_disabled_user_session_is_rejected(ctx):
    c,S=ctx;admin,_=setup_world(c,S);r=c.post('/api/v1/auth/register',json={"email":"disabled@example.test","password":"a-very-strong-user-password"});h={"Authorization":"Bearer "+r.json()['data']['access_token']}
    uid=r.json()['data']['user']['id']
    disable=c.patch(f'/api/v1/admin/users/{uid}/status',headers=admin,json={"status":"DISABLED"})
    assert disable.status_code==200 and c.get('/api/v1/auth/me',headers=h).status_code==401

def test_platform_cannot_grant_platform_permission_to_merchant_role(ctx):
    c,S=ctx;admin,(a,b)=setup_world(c,S)
    with S() as db:
        role=db.scalar(select(Role).where(Role.merchant_id==a[0]));platform_perm=db.scalar(select(Permission).where(Permission.code=="admin:merchants:read"))
    r=c.put(f'/api/v1/admin/roles/{role.id}/permissions',headers=admin,json={"permission_ids":[platform_perm.id]})
    assert r.status_code==422

def test_duplicate_merchant_employee_is_rejected(ctx):
    c,S=ctx;admin,(a,b)=setup_world(c,S)
    r=c.post(f'/api/v1/admin/merchants/{a[0]}/employees',headers=admin,json={"username":"merchantA","password":"a-strong-password-for-test","role_code":"MERCHANT_ADMIN"})
    assert r.status_code==409

def test_merchant_a_cannot_manage_b_employee(ctx):
    c,S=ctx;_,(a,b)=setup_world(c,S);ha=mlogin(c,"merchantA")
    with S() as db:b_user=db.scalar(select(MerchantUser.user_id).where(MerchantUser.merchant_id==b[0]))
    r=c.patch(f'/api/v1/merchant/employees/{b_user}/status',headers=ha,json={"status":"DISABLED"});assert r.status_code==404
    with S() as db:assert db.scalar(select(MerchantUser).where(MerchantUser.user_id==b_user)).status=="ACTIVE"



def test_redis_cache_adapter_roundtrip_with_fake_client(monkeypatch):
    class FakeRedis:
        values={}
        def ping(self):return True
        def set(self,key,value,ex=None):self.values[key]=value;return True
        def get(self,key):return self.values.get(key)
        def delete(self,key):return int(self.values.pop(key,None) is not None)
    monkeypatch.setattr(redis_client,"client",FakeRedis())
    assert redis_client.cache_set("test",{"value":7},60)
    assert redis_client.cache_get("test")=={"value":7}
    assert redis_client.cache_delete("test") and redis_client.cache_get("test") is None

def test_rate_limit_development_fallback_and_required_redis_fail_closed(monkeypatch):
    import uuid
    key="phase2-test:"+str(uuid.uuid4())
    monkeypatch.setattr(settings,"redis_required",False)
    assert rate_limit(key,2,60) and rate_limit(key,2,60) and not rate_limit(key,2,60)
    monkeypatch.setattr(settings,"redis_required",True)
    monkeypatch.setattr(redis_client,"client",None)
    assert not rate_limit(key+":required",2,60)

def test_readiness_fails_when_required_redis_is_missing(ctx,monkeypatch):
    c,_=ctx;monkeypatch.setattr(settings,"redis_required",True);monkeypatch.setattr(redis_client,"client",None)
    r=c.get('/readyz');assert r.status_code==503


def test_consumer_and_unprivileged_admin_are_denied_platform_apis(ctx):
    c,S=ctx;root,_=setup_world(c,S)
    consumer=c.post('/api/v1/auth/register',json={"email":"rbac-user@example.test","password":"a-very-strong-user-password"}).json()
    consumer_header={"Authorization":"Bearer "+consumer['data']['access_token']}
    assert c.get('/api/v1/admin/merchants',headers=consumer_header).status_code==403
    with S() as db:role=db.scalar(select(Role).where(Role.code=="PLATFORM_OPS",Role.merchant_id.is_(None)))
    created=c.post('/api/v1/admin/admin-users',headers=root,json={"username":"limitedops","password":"a-limited-admin-password","display_name":"Limited","role_ids":[role.id]})
    assert created.status_code==201
    login=c.post('/api/v1/admin/auth/login',json={"username":"limitedops","password":"a-limited-admin-password"})
    limited={"Authorization":"Bearer "+login.json()['data']['access_token']}
    assert c.get('/api/v1/admin/merchants',headers=limited).status_code==403
