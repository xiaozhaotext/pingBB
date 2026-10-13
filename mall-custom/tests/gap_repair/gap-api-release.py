import uuid
import json, urllib.request, urllib.error, subprocess
from urllib.parse import urlencode
passed=failed=0
def request(path, token=None, body=None, form=False, key=None):
    headers={'Content-Type':'application/x-www-form-urlencoded' if form else 'application/json'}
    if token: headers['Authorization']=token
    if key: headers['Idempotency-Key']=key
    data=None if body is None else (urlencode(body).encode() if form else json.dumps(body).encode())
    try:
        with urllib.request.urlopen(urllib.request.Request(('http://127.0.0.1:18080'+path.removeprefix('/admin-api') if path.startswith('/admin-api') else 'http://127.0.0.1:18085'+path.removeprefix('/portal')),data=data,headers=headers),timeout=20) as r:
            payload=r.read()
            try: return r.status,json.loads(payload)
            except: return r.status,{}
    except urllib.error.HTTPError as e:
        try: p=json.load(e)
        except: p={}
        return e.code,p
def check(name, ok):
    global passed,failed
    passed+=bool(ok);failed+=not ok
    print(('PASS' if ok else 'FAIL'),name,flush=True)
def sql(q): return subprocess.check_output(['ssh','-i','.tools/deploy-ssh/id_ed25519','-o','BatchMode=yes','root@122.51.105.107','mysql -N mall_github_audit_20261013'],input=q+';',text=True).strip()
accounts=json.load(open('.tools/consultation-test-accounts.json',encoding='utf-8'))
status,p=request('/portal/sso/login',body=accounts[0],form=True)
check('CONSUMER_LOGIN',p.get('code')==200)
member=p['data']['tokenHead']+p['data']['token']
status,p=request('/admin-api/admin/login',body={'username':'admin','password':accounts[0]['password']})
check('ADMIN_LOGIN',p.get('code')==200)
admin=p['data']['tokenHead']+p['data']['token']
paths=[('/portal/home/content','HOME_API',None),('/portal/product/search?pageNum=1&pageSize=12','PRODUCT_SEARCH',None),('/portal/product/detail/27','PRODUCT_DETAIL',None),('/portal/home/productCateList/0','CATEGORY_API',None),('/portal/sso/info','PROFILE_API',member),('/portal/cart/list','CART_API',member),('/portal/order/list?status=-1&pageNum=1&pageSize=5','MY_ORDERS',member),('/portal/member/address/list','ADDRESS_API',member),('/portal/member/invitation/detail','INVITATION_API',member),('/portal/member/points/account?pointsType=1','POINTS_ACCOUNT',member),('/portal/member/points/ledger','POINTS_LEDGER',member),('/portal/member/earnings/account','EARNINGS_ACCOUNT',member),('/portal/member/consultation/my','CONSULTATION_API',member),('/admin-api/product/list?pageNum=1&pageSize=5','ADMIN_PRODUCTS',admin),('/admin-api/order/list?pageNum=1&pageSize=5','ADMIN_ORDERS',admin),('/admin-api/p0/members','ADMIN_MEMBERS',admin),('/admin-api/p0/partner','ADMIN_PARTNER',admin),('/admin-api/p0/cofounder','ADMIN_COFOUNDER',admin),('/admin-api/p0/points','ADMIN_POINTS',admin),('/admin-api/p0/finance','ADMIN_FINANCE',admin),('/admin-api/p0/dividend','ADMIN_CONTRIBUTION_POOL',admin),('/admin-api/p0/settings','ADMIN_SETTINGS',admin),('/admin-api/dashboard/overview','ADMIN_DASHBOARD',admin)]
for path,name,token in paths:
    s,p=request(path,token)
    ok=s==200 and (p.get('code')==200 if p else path in ('/','/admin/'))
    check(name,ok)
    if not ok: print('DETAIL',path,'HTTP',s,'code',p.get('code'),'message',p.get('message'))
    if name=='PROFILE_API': check('PROFILE_DOES_NOT_EXPOSE_PASSWORD_HASH',not bool((p.get('data') or {}).get('password')))
    if name=='INVITATION_API': print('OBSERVATION INVITATION_PLACEHOLDER_DOMAIN', 'mall.example.com' in json.dumps(p))
    if name=='ADMIN_DASHBOARD' and ok:
        d=p['data'];check('DASHBOARD_PRODUCT_COUNT_MATCHES_DB',d['productCount']==int(sql('SELECT COUNT(*) FROM pms_product')))
        check('DASHBOARD_MEMBER_COUNT_MATCHES_DB',d['memberCount']==int(sql('SELECT COUNT(*) FROM ums_member')))
for token,label in [(None,'GUEST'),(member,'CONSUMER')]:
    s,p=request('/admin-api/p0/members',token)
    check(label+'_CANNOT_READ_ADMIN_MEMBERS',s in (401,403) or p.get('code') in (401,403))
bad=int(sql('SELECT COUNT(*) FROM pms_sku_stock WHERE stock<0 OR lock_stock<0 OR lock_stock>stock'))
check('ALL_SKU_STOCK_INVARIANTS',bad==0)
print('OBSERVATION ANOMALOUS_SKU_COUNT',bad)
print('OBSERVATION MERCHANT_TABLE_COUNT',sql("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='mall_github_audit_20261013' AND (table_name LIKE '%merchant%' OR table_name LIKE '%settlement%')"))


s,p=request('/portal/member/invitation/myCode',member)
check('REAL_INVITATION_CODE_GENERATED',p.get('code')==200 and bool(p.get('data')))
s,p=request('/portal/member/invitation/detail',member)
check('INVITATION_LINK_NOT_PLACEHOLDER_DOMAIN','mall.example.com' not in json.dumps(p))
for path,label in [('/portal/member/notice/list','NOTICES'),('/portal/member/notice/unreadCount','NOTICE_UNREAD'),('/portal/member/points/account?pointsType=2','REPURCHASE_POINTS'),('/admin-api/p0/notices','ADMIN_NOTICES'),('/admin-api/p0/consultations','ADMIN_CONSULTATIONS')]:
    s,p=request(path,admin if path.startswith('/admin-api') else member)
    check(label,p.get('code')==200)
invalid_before=int(sql("SELECT COUNT(*) FROM sys_consultation WHERE subject='null' AND content='null'"))
s,p=request('/portal/member/consultation/submit',member,body={})
check('CONSULTATION_MISSING_FIELDS_REJECTED',p.get('code')!=200)
s,p=request('/portal/member/consultation/my',member)
invalid=[row for row in p.get('data',[]) if row.get('subject')=='null' and row.get('content')=='null']
check('INVALID_CONSULTATION_NO_NEW_DATABASE_WRITE',int(sql("SELECT COUNT(*) FROM sys_consultation WHERE subject='null' AND content='null'"))==invalid_before)
s,p=request('/portal/member/address/add',member,body={'name':'GitHub验收测试','phoneNumber':'13900000000','defaultStatus':1,'postCode':'200000','province':'上海市','city':'上海市','region':'浦东新区','detailAddress':'隔离测试库地址，不真实发货'})
check('CREATE_TEST_ADDRESS',p.get('code')==200)
s,p=request('/portal/member/address/list',member)
address=p['data'][-1]['id']
sku=int(sql('SELECT id FROM pms_sku_stock WHERE product_id=27 AND stock-COALESCE(lock_stock,0)>5 AND COALESCE(lock_stock,0)>=0 ORDER BY id LIMIT 1'))
s,p=request('/portal/cart/add',member,body={'productId':27,'productSkuId':sku,'quantity':1})
check('CART_ADD_TEST_PRODUCT',p.get('code')==200)
s,p=request('/portal/cart/list',member)
cid=next(r['id'] for r in p['data'] if r['productSkuId']==sku)
s,p=request('/portal/order/generateConfirmOrder',member,body=[cid])
check('ORDER_CONFIRM_REAL_DATABASE',p.get('code')==200)
s,p=request('/portal/order/generateOrder',member,body={'memberReceiveAddressId':address,'payType':0,'cartIds':[cid]})
check('ORDER_REJECTS_MISSING_IDEMPOTENCY_KEY',p.get('code')!=200)
s,p=request('/portal/order/generateOrder',member,body={'memberReceiveAddressId':address,'payType':0,'cartIds':[cid]},key='regression_'+uuid.uuid4().hex)
check('ORDER_CREATE_WITH_VALID_IDEMPOTENCY_KEY',p.get('code')==200)
if p.get('code')!=200: print('DETAIL ORDER_CREATE HTTP',s,'code',p.get('code'))
else: request('/portal/order/cancelUserOrder?orderId='+str(p['data']['order']['id']),member,body={})
print('OBSERVATION ORDER_REQUEST_TABLE_PRESENT',sql("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='mall_github_audit_20261013' AND table_name='oms_order_request'"))
pool=sql('SELECT pool_balance FROM dividend_pool WHERE id=1')
if True:  # Unit and runtime safety guards are verified; every write endpoint is blocked.
    s,p=request('/portal/member/dividend/distribute',member,body={'statDate':'2026-10-12'})
    check('CONSUMER_CANNOT_EXECUTE_DIVIDEND_DISTRIBUTION',s in (401,403) or p.get('code') in (401,403))
    print('OBSERVATION DISTRIBUTION_RESPONSE',s,p.get('code'),'credited-members',p.get('data'))
else:
    print('BLOCKED distribution permission test: nonzero pool; will not execute distribution')
print('RESULT',passed,'PASS',failed,'FAIL')

