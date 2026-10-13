import urllib.request,urllib.error,json
from pathlib import Path
base='https://122.51.105.107';passed=0
opener=urllib.request.build_opener(urllib.request.ProxyHandler({}))
def request(path,body=None,token=None):
 headers={'Content-Type':'application/json'}
 if token:headers['Authorization']=token
 try:
  with opener.open(urllib.request.Request(base+path,data=None if body is None else json.dumps(body).encode(),headers=headers),timeout=20) as r:
   data=r.read()
   try:return r.status,json.loads(data)
   except:return r.status,data.decode()
 except urllib.error.HTTPError as e:return e.code,json.load(e)
def check(name,ok):
 global passed
 assert ok,name;passed+=1;print('PASS',name,flush=True)
status,p=request('/');check('HTTPS_STOREFRONT_200',status==200)
status,p=request('/admin/');check('HTTPS_ADMIN_200',status==200)
_,p=request('/admin-api/admin/login',{'username':'admin','password':Path('.tools/server-admin-password.txt').read_text().strip()});check('ADMIN_REQUESTED_CREDENTIAL_LOGIN',p.get('code')==200);token=p['data']['tokenHead']+p['data']['token']
_,p=request('/admin-api/p0/site-profile',token=token);check('NEW_ADMIN_PROFILE_REAL_API',p.get('code')==200);profile=p['data']
_,p=request('/portal/home/site-profile/company');check('NEW_PUBLIC_COMPANY_REAL_API',p.get('code')==200 and p['data']['company_name']==profile['company_name']);check('PUBLIC_COMPANY_NO_PRIVATE_DRAFT','privacy_content' not in p['data'])
_,legacy=request('/portal/home/siteInfo');check('CANONICAL_COMPANY_SHARED_WITH_EXISTING_API',legacy.get('code')==200 and legacy['data']['company'].get('name')==profile['company_name'])
_,p=request('/admin-api/p0/site-profile');check('ANONYMOUS_ADMIN_PROFILE_REJECTED',p.get('code')!=200)
_,p=request('/admin-api/p0/system-settings',token=token);check('EXISTING_SETTINGS_RETAINED',p.get('code')==200)
_,p=request('/portal/home/site-profile/privacy');check('PUBLIC_POLICY_ONLY_PUBLISHED_OR_CLEAR_UNPUBLISHED',p.get('code')==200 and p['data']['content']==profile['published_content'] if profile['published_revision'] is not None else p.get('code')!=200)
print('RESULT',passed,'PASS 0 FAIL')
