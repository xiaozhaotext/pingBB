import json,urllib.request,urllib.error,uuid,subprocess,secrets
from urllib.parse import urlencode
base='http://127.0.0.1:18085'
name='register_'+uuid.uuid4().hex[:16];phone='139000'+str(secrets.randbelow(100000)).zfill(5);password=secrets.token_urlsafe(22)
passed=0
def req(path,body=None,token=None):
 headers={'Content-Type':'application/x-www-form-urlencoded'}
 if token:headers['Authorization']=token
 request=urllib.request.Request(base+path,data=None if body is None else urlencode(body).encode(),headers=headers)
 try:
  with urllib.request.urlopen(request,timeout=15) as r:return json.load(r)
 except urllib.error.HTTPError as e:return json.load(e)
def check(label,ok):
 global passed
 assert ok,label;passed+=1;print('PASS',label,flush=True)
def count():return int(subprocess.check_output(['ssh','-i','.tools/deploy-ssh/id_ed25519','-o','BatchMode=yes','root@122.51.105.107','mysql -N mall_github_audit_20261013'],input="SELECT COUNT(*) FROM ums_member WHERE username='"+name+"';",text=True).strip())
p=req('/sso/getAuthCode?telephone='+phone);code=str(p.get('data'));check('DEMO_AUTH_CODE_AVAILABLE',p.get('code')==200 and code.isdigit() and len(code)==6)
body={'username':name,'password':password,'telephone':phone,'authCode':'x'}
p=req('/sso/register',body);check('WRONG_OTP_REJECTED',p.get('code')!=200);check('WRONG_OTP_NO_MEMBER_WRITE',count()==0)
body['authCode']=code;p=req('/sso/register',body);check('REGISTRATION_SUCCESS',p.get('code')==200);check('MEMBER_PERSISTED_ONCE',count()==1)
p=req('/sso/login',{'username':name,'password':password});check('NEW_MEMBER_CAN_LOGIN',p.get('code')==200)
token=p['data']['tokenHead']+p['data']['token'];p=req('/sso/info',token=token);check('NEW_PROFILE_OMITS_PASSWORD',p.get('code')==200 and 'password' not in p['data'])
p=req('/sso/register',body);check('DUPLICATE_REGISTRATION_REJECTED',p.get('code')!=200);check('DUPLICATE_NO_EXTRA_MEMBER',count()==1)
p=req('/sso/register',{'username':'invalid_short','password':'short','telephone':phone,'authCode':code});check('SHORT_PASSWORD_REJECTED',p.get('code')!=200)
print('RESULT',passed,'PASS 0 FAIL; OTP is simulated, not real SMS')
