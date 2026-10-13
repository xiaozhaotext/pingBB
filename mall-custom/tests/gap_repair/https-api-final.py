import urllib.request,urllib.error,json,secrets,time
from urllib.parse import urlencode
from pathlib import Path
base='https://122.51.105.107'
opener=urllib.request.build_opener(urllib.request.ProxyHandler({}))
passed=0
def req(path,body=None,token=None,form=False):
 headers={'Content-Type':'application/x-www-form-urlencoded' if form else 'application/json'}
 if token:headers['Authorization']=token
 data=None if body is None else (urlencode(body).encode() if form else json.dumps(body).encode())
 try:
  with opener.open(urllib.request.Request(base+path,data=data,headers=headers),timeout=25) as r:return r.status,json.load(r)
 except urllib.error.HTTPError as e:return e.code,json.load(e)
def check(name,ok):
 global passed
 assert ok,name
 passed+=1;print('PASS',name,flush=True)
name='https_test_'+str(int(time.time()));password=secrets.token_urlsafe(22);phone='139'+str(secrets.randbelow(10**8)).zfill(8)
_,p=req('/portal/sso/getAuthCode?telephone='+phone);code=str(p.get('data'));check('HTTPS_DEMO_CODE',p.get('code')==200 and len(code)==6)
_,p=req('/portal/sso/register',{'username':name,'telephone':phone,'password':password,'authCode':code},form=True);check('HTTPS_REGISTER',p.get('code')==200)
_,p=req('/portal/sso/login',{'username':name,'password':password},form=True);check('HTTPS_NEW_ACCOUNT_LOGIN',p.get('code')==200);token=p['data']['tokenHead']+p['data']['token']
_,p=req('/portal/sso/info',token=token);check('HTTPS_SAFE_PROFILE',p.get('code')==200 and 'password' not in p['data']);member=p['data']['id']
_,p=req('/portal/sso/register',{'username':name,'telephone':phone,'password':password,'authCode':code},form=True);check('HTTPS_DUPLICATE_REGISTER_REJECTED',p.get('code')!=200)
subject='HTTPS验收测试-'+str(int(time.time()))
_,p=req('/portal/member/consultation/submit',{'subject':subject,'content':'真实HTTPS接口验收测试记录','contact':'测试账号'},token);check('HTTPS_MEMBER_CONSULTATION_CREATE',p.get('code')==200)
_,p=req('/portal/member/consultation/my',token=token);row=next(x for x in p['data'] if x['subject']==subject);check('HTTPS_MEMBER_OWN_CONSULTATION',True)
_,p=req('/admin-api/admin/login',{'username':'admin','password':Path('.tools/server-admin-password.txt').read_text().strip()});check('HTTPS_ADMIN_LOGIN',p.get('code')==200);admin=p['data']['tokenHead']+p['data']['token']
_,p=req('/admin-api/p0/consultations/'+str(row['id'])+'/reply',{'reply':'HTTPS验收已回复'},admin);check('HTTPS_ADMIN_REPLY',p.get('code')==200)
_,p=req('/portal/member/consultation/my',token=token);check('HTTPS_MEMBER_SEES_ADMIN_REPLY',any(x['id']==row['id'] and x.get('reply')=='HTTPS验收已回复' for x in p['data']))
_,p=req('/admin-api/p0/notices',{'title':subject,'content':'明确标注的HTTPS测试公告','type':'SYSTEM'},admin);check('HTTPS_ADMIN_PUBLISH_NOTICE',p.get('code')==200)
_,p=req('/portal/home/announcements');check('HTTPS_PUBLIC_SEES_DB_NOTICE',p.get('code')==200 and any(x.get('title')==subject for x in p['data']))
Path('.tools/https-test-account.json').write_text(json.dumps({'username':name,'password':password,'memberId':member}),encoding='utf8')
print('RESULT',passed,'PASS 0 FAIL')
