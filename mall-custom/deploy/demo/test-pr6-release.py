"""Extend wallet acceptance with PR 6 invitation, cap, credit and reporting checks."""
import runpy
from pathlib import Path
import uuid

g = runpy.run_path(str(Path(__file__).with_name('test-wallet-auth-release.py')))
globals().update({k:v for k,v in g.items() if not k.startswith('__')})
import uuid

def register(invite=None):
    name = 'pr6_qa_' + secrets.token_hex(6)
    phone = '198' + ''.join(str(secrets.randbelow(10)) for _ in range(8))
    password = secrets.token_urlsafe(24)
    code = data(request(PORTAL,'/sso/getAuthCode?telephone='+phone))
    body = dict(username=name,password=password,telephone=phone,authCode=code)
    if invite: body['inviteCode']=invite
    data(request(PORTAL,'/sso/register',form=body))
    login=data(request(PORTAL,'/sso/login',form=dict(username=name,password=password)))
    t=login['tokenHead']+login['token']
    return int(data(request(PORTAL,'/sso/info',token=t))['id']),t

def partner():
    s=data(request(PORTAL,'/member/tri-pools',token=token))
    return s,next(p for p in s['pools'] if p['code']=='PARTNER')

s,p=partner()
check('member_initial_partner_cap_1200_one_share', Decimal(str(p['cap']))==1200 and p['shares']==1)
invite=data(request(PORTAL,'/member/invitation/myCode',token=token))
for count,cap in [(1,2400),(2,3600),(3,8000)]:
    child,child_token=register(invite.lower())
    order=data(request(PORTAL,'/member/membership/orders',payload=dict(memberId=child,level='MEMBER',payChannel='ALIPAY',payMode='ALIPAY_WAP',requestId='PR6'+secrets.token_hex(12)),token=child_token))
    data(request(PORTAL,f"/mockPayment/membership/confirm?orderId={order['orderId']}",form={},token=child_token))
    s,p=partner()
    check(f'direct_paid_{count}_partner_cap_{cap}',s['directPaidCount']==count and p['shares']==count+1 and Decimal(str(p['cap']))==cap)
    check(f'inviter_cannot_be_rebound_{count}',request(PORTAL,'/member/invitation/bind',payload={'code':invite},token=child_token)['code']!=200)

admin_id=None
admin_name='pr6_qa_admin_'+secrets.token_hex(6)
admin_password=secrets.token_urlsafe(24)
encoded=bcrypt.hashpw(admin_password.encode(),bcrypt.gensalt()).decode()
try:
    admin_id=int(mysql(f"INSERT INTO ums_admin(username,password,status,create_time,note) VALUES('{admin_name}','{encoded}',1,NOW(),'Temporary PR6 QA'); SELECT LAST_INSERT_ID();"))
    mysql(f'INSERT INTO ums_admin_role_relation(admin_id,role_id) VALUES({admin_id},5)')
    login=data(request(ADMIN,'/admin/login',payload=dict(username=admin_name,password=admin_password)))
    at=login['tokenHead']+login['token']
    snapshot=lambda:data(request(ADMIN,'/dashboard/overview',token=at))
    before=snapshot()
    trend_before=data(request(ADMIN,'/dashboard/fund-trend',token=at))
    credit_member,credit_token=register()
    body=dict(memberId=credit_member,amount='100.10',requestId=str(uuid.uuid4()),reason='PR6沙盒发布验收')
    credit=lambda:data(request(ADMIN,'/p0/wallet/manual-recharge',payload=body,token=at))
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool: list(pool.map(lambda _:credit(),range(4)))
    wallet=data(request(ADMIN,f'/p0/wallet?memberId={credit_member}&tab=ledger',token=at))
    check('manual_credit_concurrent_exactly_once_audited',wallet['total']==1 and Decimal(wallet['summary']['balance'])==Decimal('100.10') and Decimal(wallet['summary']['manualCredited'])==Decimal('100.10') and Decimal(wallet['summary']['recharged'])==0 and wallet['rows'][0]['biz_id']=='ADMIN:'+str(admin_id))
    altered=dict(body,amount='101.10')
    check('manual_credit_changed_replay_rejected',request(ADMIN,'/p0/wallet/manual-recharge',payload=altered,token=at)['code']!=200)
    after=snapshot()
    fields=['totalCashPaidAmount','totalBalancePaidAmount','totalTradeAmount']
    check('manual_credit_excluded_from_trade_and_cash',all(before[k]==after[k] for k in fields) and trend_before==data(request(ADMIN,'/dashboard/fund-trend',token=at)))
    # Use the existing member/address/cart helpers with this newly credited wallet.
    original_token=token
    token=credit_token
    data(request(PORTAL,'/member/address/add',payload={'name':'PR6验收','phoneNumber':'19900000000','defaultStatus':1,'province':'测试省','city':'测试市','region':'测试区','detailAddress':'沙盒无需发货'},token=token))
    address=int(data(request(PORTAL,'/member/address/list',token=token))[-1]['id'])
    data(request(PORTAL,'/cart/add?zoneCode=PICK',payload={'productId':product,'productSkuId':sku,'quantity':1},token=token))
    cart_id=int(data(request(PORTAL,'/cart/list',token=token))[-1]['id'])
    purchase=data(request(PORTAL,'/order/generateOrder',payload=dict(payType=0,cartIds=[cart_id],memberReceiveAddressId=address,useBalance=True),token=token,key='PR6'+secrets.token_hex(12)))
    expected=Decimal('100.10')-price
    check('manual_credit_spendable_and_exactly_debited',purchase['paidImmediately'] and Decimal(data(request(PORTAL,'/member/recharge/account',token=token))['balance'])==expected)
    after=snapshot()
    check('dashboard_cash_balance_trade_match_checkout',Decimal(str(after['totalCashPaidAmount']))==Decimal(str(before['totalCashPaidAmount'])) and Decimal(str(after['totalBalancePaidAmount']))==Decimal(str(before['totalBalancePaidAmount']))+price and Decimal(str(after['totalTradeAmount']))==Decimal(str(before['totalTradeAmount']))+price)
    trend=data(request(ADMIN,'/dashboard/fund-trend',token=at))
    for key,fmt in [('days','%Y-%m-%d'),('months','%Y-%m')]:
        period=mysql(f"SELECT DATE_FORMAT(payment_time,'{fmt}') FROM oms_order WHERE id={int(purchase['order']['id'])}")
        old=next((r for r in trend_before[key] if r['period']==period),{})
        new=next(r for r in trend[key] if r['period']==period)
        check(key+'_report_cash_balance_trade',Decimal(str(new['cashAmount']))==Decimal(str(old.get('cashAmount',0))) and Decimal(str(new['balanceAmount']))==Decimal(str(old.get('balanceAmount',0)))+price and Decimal(str(new['tradeAmount']))==Decimal(str(old.get('tradeAmount',0)))+price)
    request(ADMIN,'/admin/logout',form={},token=at)
finally:
    if admin_id: mysql(f'DELETE FROM ums_admin_role_relation WHERE admin_id={admin_id}; DELETE FROM ums_admin WHERE id={admin_id}')

Path(__file__).with_name('pr6-acceptance-result.json').write_text(json.dumps({'checks':len(checks),'passed':checks,'partnerMemberId':member,'manualCreditMemberId':credit_member,'manualCredit':'100.10','purchasePrice':str(price),'balanceAfterPurchase':str(expected)},ensure_ascii=False,indent=2))
print('PR6_ACCEPTANCE_PASSED',len(checks),flush=True)
