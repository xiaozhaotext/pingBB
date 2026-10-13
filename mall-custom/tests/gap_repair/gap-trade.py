import json,urllib.request,urllib.error,subprocess,uuid
from urllib.parse import urlencode
base='http://127.0.0.1';passed=0
def req(path,body=None,token=None,form=False,key=None):
 headers={'Content-Type':'application/x-www-form-urlencoded' if form else 'application/json'}
 if token:headers['Authorization']=token
 if key:headers['Idempotency-Key']=key
 data=None if body is None else (urlencode(body).encode() if form else json.dumps(body).encode())
 try:
  with urllib.request.urlopen(urllib.request.Request(('http://127.0.0.1:18080'+path.removeprefix('/admin-api') if path.startswith('/admin-api') else 'http://127.0.0.1:18085'+path.removeprefix('/portal')),data=data,headers=headers),timeout=25) as r:return r.status,json.load(r)
 except urllib.error.HTTPError as e:
  try:p=json.load(e)
  except:p={}
  return e.code,p
def check(name,yes):
 global passed
 assert yes,name;passed+=1;print('PASS',name)
def sql(q):return subprocess.check_output(['ssh','-i','.tools/deploy-ssh/id_ed25519','-o','BatchMode=yes','root@122.51.105.107','mysql -N mall_github_audit_20261013'],input=q+';',text=True).strip()
accounts=json.load(open('.tools/consultation-test-accounts.json'));tokens=[]
for account in accounts:
 s,p=req('/portal/sso/login',account,form=True);assert p['code']==200;tokens.append(p['data']['tokenHead']+p['data']['token'])
s,p=req('/admin-api/admin/login',{'username':'admin','password':accounts[0]['password']});admin=p['data']['tokenHead']+p['data']['token']
s,p=req('/portal/member/address/add',{'name':'交易验收测试','phoneNumber':'13900000000','defaultStatus':1,'postCode':'200000','province':'上海市','city':'上海市','region':'浦东新区','detailAddress':'隔离演示地址，请勿真实发货'},tokens[0]);check('real-address-created',p.get('code')==200)
s,p=req('/portal/member/address/list',token=tokens[0]);address=p['data'][-1]['id'];check('address-query',bool(address))
req('/portal/cart/clear',{},tokens[0])
sku=int(sql('SELECT id FROM pms_sku_stock WHERE product_id=27 AND stock-COALESCE(lock_stock,0)>5 AND COALESCE(lock_stock,0)>=0 ORDER BY id LIMIT 1'))
before=sql(f'SELECT CONCAT(stock,",",COALESCE(lock_stock,0)) FROM pms_sku_stock WHERE id={sku}')
s,p=req('/portal/cart/add',{'productId':27,'productSkuId':sku,'quantity':1,'price':0.01,'productName':'篡改名称'},tokens[0]);check('real-cart-add',p.get('code')==200)
s,p=req('/portal/cart/list',token=tokens[0]);cart=next(r for r in p['data'] if r['productSkuId']==sku);cid=cart['id'];check('server-cart-pricing',float(cart['price'])>0.01 and cart['productName']!='篡改名称')
s,p=req('/portal/cart/list',token=tokens[1]);check('cart-user-isolation',all(r['id']!=cid for r in p['data']))
s,p=req('/portal/order/generateConfirmOrder',[cid],tokens[0]);check('real-confirm-order',p.get('code')==200)
key='acceptance_'+uuid.uuid4().hex;body={'memberReceiveAddressId':address,'payType':0,'cartIds':[cid]}
s,p=req('/portal/order/generateOrder',body,tokens[0],key=key)
if p.get('code')!=200:print('ORDER_ERROR',s,p.get('code'),p.get('message'))
check('real-order-created',p.get('code')==200);oid=p['data']['order']['id']
s,p=req('/portal/order/generateOrder',body,tokens[0],key=key);check('repeat-order-idempotent',p.get('code')==200 and p['data']['order']['id']==oid)
check('stock-reserved',int(sql(f'SELECT COALESCE(lock_stock,0) FROM pms_sku_stock WHERE id={sku}'))>=int(before.split(',')[1])+1)
s,p=req(f'/admin-api/order/{oid}',token=admin);check('admin-sees-consumer-order',p.get('code')==200 and p['data']['id']==oid)
s,p=req(f'/portal/order/detail/{oid}',token=tokens[1]);check('other-member-order-denied',s in (403,404) or p.get('code')!=200)
s,p=req('/portal/order/paySuccess?orderId='+str(oid)+'&payType=1',{},tokens[0]);check('fake-client-payment-denied',s==403 or p.get('code')==403)
s,p=req('/portal/order/cancelUserOrder?orderId='+str(oid),{},tokens[0]);check('owner-cancels-order',p.get('code')==200)
check('cancel-releases-stock',sql(f'SELECT CONCAT(stock,",",COALESCE(lock_stock,0)) FROM pms_sku_stock WHERE id={sku}')==before)
check('cancel-state-persisted',sql(f'SELECT status FROM oms_order WHERE id={oid}')=='4')

# A second, separate order exercises the isolated MOCK channel; no real funds.
req('/portal/cart/add',{'productId':27,'productSkuId':sku,'quantity':1},tokens[0])
s,p=req('/portal/cart/list',token=tokens[0]);cid=next(r['id'] for r in p['data'] if r['productSkuId']==sku)
s,p=req('/portal/order/generateOrder',{'memberReceiveAddressId':address,'payType':0,'cartIds':[cid]},tokens[0],key='mock_'+uuid.uuid4().hex)
check('mock-order-created',p.get('code')==200);oid=p['data']['order']['id']
s,p=req('/portal/mockPayment/confirm?orderId='+str(oid),{},tokens[1]);check('other-member-mock-payment-denied',p.get('code')!=200)
s,p=req('/portal/mockPayment/confirm?orderId='+str(oid),{},tokens[0]);check('local-mock-payment-confirmed',p.get('code')==200)
after=sql(f'SELECT CONCAT(stock,",",COALESCE(lock_stock,0)) FROM pms_sku_stock WHERE id={sku}')
s,p=req('/portal/mockPayment/confirm?orderId='+str(oid),{},tokens[0]);check('duplicate-mock-confirmation-idempotent',p.get('code')==200 and p.get('data')=='ALREADY_CONFIRMED')
check('duplicate-payment-never-deducts-inventory-twice',sql(f'SELECT CONCAT(stock,",",COALESCE(lock_stock,0)) FROM pms_sku_stock WHERE id={sku}')==after)
check('one-payment-confirmation',sql(f'SELECT COUNT(*) FROM oms_payment_confirmation WHERE order_id={oid}')=='1')
check('payment-state-persisted',sql(f'SELECT status FROM oms_order WHERE id={oid}')=='1')
mid=int(sql("SELECT id FROM ums_member WHERE username='"+accounts[0]['username']+"'"))
s,p=req('/admin-api/p0/members/'+str(mid),token=admin);check('admin-member-address-order-statistics',p.get('code')==200 and all(k in p['data'] for k in ['addresses','orders','consumption']))
state_before=sql('SELECT CONCAT(pool_balance,",",total_out) FROM dividend_pool WHERE id=1')
for endpoint in ['distribute','allocate','contribution']:
 s,p=req('/portal/member/dividend/'+endpoint,{},tokens[0]);check('consumer-dividend-'+endpoint+'-denied',s==403 or p.get('code')==403)
check('dividend-denial-no-financial-side-effects',state_before==sql('SELECT CONCAT(pool_balance,",",total_out) FROM dividend_pool WHERE id=1'))
marker='consult-'+uuid.uuid4().hex
s,p=req('/portal/member/consultation/submit',{'subject':marker,'content':'Test genuine consultation flow','contact':'test only'},tokens[0]);check('consultation-submit',p.get('code')==200)
s,p=req('/portal/member/consultation/my',token=tokens[0]);consult=next(x for x in p['data'] if x['subject']==marker)
s,p=req('/portal/member/consultation/my',token=tokens[1]);check('consultation-member-isolation',all(x['id']!=consult['id'] for x in p['data']))
s,p=req('/admin-api/p0/consultations/'+str(consult['id'])+'/reply',{'reply':'Reply from authorized administrator'},admin);check('admin-consultation-reply',p.get('code')==200)
s,p=req('/portal/member/consultation/my',token=tokens[0]);check('member-sees-admin-reply',any(x['id']==consult['id'] and x['reply']=='Reply from authorized administrator' for x in p['data']))
s,p=req('/admin-api/p0/notices',{'title':marker,'content':'Notice integration test','type':'SYSTEM'},admin);check('admin-notice-publish',p.get('code')==200)
s,p=req('/portal/member/notice/list',token=tokens[0]);check('member-sees-published-notice',any(x['title']==marker for x in p['data']))
s,p=req('/admin-api/p0/members/'+str(mid)+'/status',{'status':0,'reason':'Isolated account disable test'},admin);check('admin-disables-member',p.get('code')==200)
try:
 s,p=req('/portal/sso/info',token=tokens[0]);check('existing-token-denied-after-disable',s in (401,403) or p.get('code') in (401,403))
 s,p=req('/portal/sso/login',accounts[0],form=True);check('disabled-member-login-denied',p.get('code')!=200)
finally:
 s,p=req('/admin-api/p0/members/'+str(mid)+'/status',{'status':1,'reason':'Restore isolated test account'},admin);check('admin-restores-member',p.get('code')==200)
check('audit-for-disable-and-restore',int(sql(f"SELECT COUNT(*) FROM sys_audit_log WHERE target_id={mid} AND action='status_change'"))>=2)
print('RESULT',passed,'PASS 0 FAIL')
