import json,urllib.request,urllib.error,time
accounts=json.load(open('.tools/consultation-test-accounts.json',encoding='utf8'))
passed=0
def req(path,body=None,token=None,form=False):
 from urllib.parse import urlencode
 headers={'Content-Type':'application/x-www-form-urlencoded' if form else 'application/json'}
 if token:headers['Authorization']=token
 data=None if body is None else (urlencode(body).encode() if form else json.dumps(body).encode())
 base='http://127.0.0.1:18080' if path.startswith('/p0') or path.startswith('/admin') else 'http://127.0.0.1:18085'
 try:
  with urllib.request.urlopen(urllib.request.Request(base+path,data=data,headers=headers),timeout=20) as r:return json.load(r)
 except urllib.error.HTTPError as e:return json.load(e)
def check(name,ok):
 global passed
 assert ok,name;passed+=1;print('PASS',name,flush=True)
p=req('/admin/login',{'username':'admin','password':accounts[0]['password']});check('ADMIN_LOGIN',p.get('code')==200);token=p['data']['tokenHead']+p['data']['token']
p=req('/sso/login',accounts[0],form=True);member=p['data']['tokenHead']+p['data']['token']
check('ANONYMOUS_ADMIN_REJECTED',req('/p0/site-profile').get('code')!=200)
check('CONSUMER_ADMIN_REJECTED',req('/p0/site-profile',token=member).get('code')!=200)
p=req('/p0/site-profile',token=token);check('ADMIN_PROFILE_READ',p.get('code')==200);r=p['data']
body={'revision':r['company_revision'],'companyName':'自动化测试公司','address':'测试地址','telephone':'021-00000000','mobile':'13900000000','email':'test@example.com','qq':'12345','wechatQrUrl':'https://example.com/qr.png'}
p=req('/p0/site-profile/company',body,token);check('COMPANY_SAVED',p.get('code')==200)
p=req('/home/site-profile/company');check('PUBLIC_COMPANY_FROM_DATABASE',p.get('code')==200 and p['data']['company_name']==body['companyName']);check('COMPANY_ALL_SEVEN_FIELDS_PERSISTED',all(p['data'].get(k)==body[v] for k,v in {'company_name':'companyName','address':'address','telephone':'telephone','mobile':'mobile','email':'email','qq':'qq','wechat_qr_url':'wechatQrUrl'}.items()));check('PUBLIC_COMPANY_NO_PRIVATE_DRAFT','privacy_content' not in p['data'])
check('STALE_COMPANY_EDIT_REJECTED',req('/p0/site-profile/company',body,token).get('code')!=200)
bad={**body,'revision':body['revision']+1,'wechatQrUrl':'javascript:alert(1)'};check('UNSAFE_QR_URL_REJECTED',req('/p0/site-profile/company',bad,token).get('code')!=200)
check('INVALID_INPUT_NO_REVISION_CHANGE',req('/p0/site-profile',token=token)['data']['company_revision']==body['revision']+1)
before=req('/home/site-profile/privacy');v=r['privacy_revision'];title='自动化测试政策-'+str(int(time.time()))
p=req('/p0/site-profile/privacy/draft',{'revision':v,'title':title,'content':'测试正文 <script>window.injected=true</script>\n第二行'},token);check('POLICY_DRAFT_SAVED',p.get('code')==200)
check('DRAFT_DOES_NOT_CHANGE_PUBLIC_POLICY',req('/home/site-profile/privacy')==before)
check('PUBLISH_REQUIRES_CONFIRMATION',req('/p0/site-profile/privacy/publish',{'revision':v+1,'confirmed':False},token).get('code')!=200)
check('STALE_POLICY_PUBLISH_REJECTED',req('/p0/site-profile/privacy/publish',{'revision':v,'confirmed':True},token).get('code')!=200)
p=req('/p0/site-profile/privacy/publish',{'revision':v+1,'confirmed':True},token);check('POLICY_PUBLISHED',p.get('code')==200)
p=req('/home/site-profile/privacy');check('PUBLIC_POLICY_MATCHES_PUBLISHED',p.get('code')==200 and p['data']['title']==title);check('PUBLIC_POLICY_NO_DRAFT_FIELDS','privacy_content' not in p['data'])
first=p['data'];check('REPEAT_PUBLISH_ACCEPTED',req('/p0/site-profile/privacy/publish',{'revision':v+1,'confirmed':True},token).get('code')==200);check('REPEAT_PUBLISH_UNCHANGED',req('/home/site-profile/privacy')['data']==first)
check('CONSUMER_WRITE_DENIED',req('/p0/site-profile/company',{**body,'revision':body['revision']+1},member).get('code')!=200)
check('DENIED_WRITE_NO_SIDE_EFFECT',req('/p0/site-profile',token=token)['data']['company_revision']==body['revision']+1)
check('LEGACY_PRIVACY_DIRECT_WRITE_REJECTED',req('/p0/system-settings/content',{'key':'privacy_policy','value':'不应直接公开的草稿'},token).get('code')!=200)
check('LEGACY_AND_NEW_PRIVACY_PUBLIC_CONTENT_MATCH',req('/home/siteInfo')['data']['contents']['privacy_policy']==req('/home/site-profile/privacy')['data']['content'])
previous=req('/p0/site-profile',token=token)['data']['company_revision']
check('EXISTING_COMPANY_EDITOR_WRITES_CANONICAL_STORE',req('/p0/system-settings',{'key':'company.name','value':'同一资料源验收测试'},token).get('code')==200)
check('COMPANY_EDITOR_ALIAS_READS_SAME_RECORD',req('/home/site-profile/company')['data']['company_name']=='同一资料源验收测试')
check('EXISTING_EDITOR_INVALIDATES_STALE_VERSION',req('/p0/site-profile',token=token)['data']['company_revision']==previous+1)
print('RESULT',passed,'PASS 0 FAIL')
