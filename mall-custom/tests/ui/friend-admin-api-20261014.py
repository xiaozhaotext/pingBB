from pathlib import Path
import urllib.request,urllib.error,json
op=urllib.request.build_opener(urllib.request.ProxyHandler({}));base='https://122.51.105.107/admin-api';passed=0
req=urllib.request.Request(base+'/admin/login',data=json.dumps({'username':'admin','password':Path('.tools/server-admin-password.txt').read_text().strip()}).encode(),headers={'Content-Type':'application/json'})
with op.open(req,timeout=15) as r:login=json.load(r)
assert login['code']==200;token=login['data']['tokenHead']+login['data']['token']
for path in ['/admin/info','/dashboard/overview','/p0/members','/p0/points','/p0/finance','/p0/system-settings/content/register_notice','/product/list?pageNum=1&pageSize=5','/order/list?pageNum=1&pageSize=5','/p0/zones/PICK/products','/p0/zones/REBUY/products']:
 req=urllib.request.Request(base+path,headers={'Authorization':token})
 try:
  with op.open(req,timeout=15) as r:data=json.load(r)
  if data.get('code')!=200:print('FAIL',path,data.get('code'));continue
  passed+=1;print('PASS',path)
 except urllib.error.HTTPError as e:print('FAIL',path,e.code)
try:
 with op.open(base+'/p0/members',timeout=15) as r:d=json.load(r);assert d.get('code') in [401,403]
except urllib.error.HTTPError as e:assert e.code in [401,403]
passed+=1;print('PASS UNAUTHENTICATED_ADMIN_REJECTED');print('RESULT',passed,'PASS',11-passed,'FAIL')
raise SystemExit(0 if passed==11 else 1)
