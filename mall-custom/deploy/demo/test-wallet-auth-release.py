"""Post-deploy acceptance on the isolated local sandbox. Never prints credentials.
Creates labelled QA member/order rows, uses one live sandbox SKU, and removes its temporary admin.
"""
import concurrent.futures
from decimal import Decimal
import json
import os
from pathlib import Path
import secrets
import subprocess
import urllib.error
import urllib.parse
import urllib.request
import bcrypt

PORTAL = 'http://127.0.0.1:8086'
ADMIN = 'http://127.0.0.1:8087'
checks = []

def mysql(sql, database='mall_demo_sandbox'):
    return subprocess.run(['mysql', '-N', '-B', database, '-e', sql], check=True,
                          text=True, capture_output=True).stdout.strip()

def request(base, path, *, payload=None, form=None, token=None, key=None):
    headers, data = {}, None
    if payload is not None:
        data = json.dumps(payload).encode(); headers['Content-Type'] = 'application/json'
    if form is not None:
        data = urllib.parse.urlencode(form).encode(); headers['Content-Type'] = 'application/x-www-form-urlencoded'
    if token:
        headers['Authorization'] = token
    if key:
        headers['Idempotency-Key'] = key
    try:
        response = urllib.request.urlopen(urllib.request.Request(base+path, data=data, headers=headers), timeout=30)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        return json.load(response)

def data(result):
    assert result.get('code') == 200, (result.get('code'), result.get('message'))
    return result.get('data')

def check(label, condition):
    assert condition, label
    checks.append(label)
    print('PASS', label, flush=True)

assert mysql("SELECT config_value FROM sys_config WHERE config_key='membership.purchase.enabled'") == '1'
assert mysql("SELECT config_value FROM sys_config WHERE config_key='membership.purchase.enabled'", 'mall_demo') == '0'
assert mysql("SELECT config_value FROM sys_config WHERE config_key='dividend.distribution.enabled'") == '0'
suffix = secrets.token_hex(6)
username, password = 'release_qa_' + suffix, secrets.token_urlsafe(24)
phone = '199' + ''.join(str(secrets.randbelow(10)) for _ in range(8))
code = data(request(PORTAL, '/sso/getAuthCode?' + urllib.parse.urlencode({'telephone':phone})))
check('sandbox_demo_code_returned', isinstance(code,str) and len(code) == 6 and code.isdigit())
data(request(PORTAL,'/sso/register',form=dict(username=username,password=password,telephone=phone,authCode=code)))
check('wrong_password_rejected', request(PORTAL,'/sso/login',form=dict(username=username,password='wrong-password'))['code'] != 200)
login = data(request(PORTAL,'/sso/login',form=dict(username=username,password=password)))
token = login['tokenHead'] + login['token']
profile = data(request(PORTAL,'/sso/info',token=token)); member = int(profile['id'])
check('new_member_registered_and_logged_in', profile['username'] == username)
options = data(request(PORTAL,'/member/recharge/payment-options',token=token))
check('only_mock_recharge_enabled_in_sandbox', options == {'sandbox':True,'channels':['MOCK']})
order = data(request(PORTAL,'/member/recharge/orders',payload={'amount':'100.10','channel':'MOCK','requestId':'QA'+suffix+'recharge'},token=token))
no = order['orderNo']
confirm = lambda: data(request(PORTAL,f'/member/recharge/orders/{no}/sandbox-confirm',form={},token=token))
with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
    list(pool.map(lambda _:confirm(),range(4)))
balance = lambda: Decimal(data(request(PORTAL,'/member/recharge/account',token=token))['balance'])
check('concurrent_recharge_credited_once', balance() == Decimal('100.10'))
row = mysql("SELECT p.id,s.id,s.price FROM pms_product p JOIN pms_sku_stock s ON s.product_id=p.id JOIN pms_product_zone z ON z.product_id=p.id WHERE z.zone_code='PICK' AND p.publish_status=1 AND p.delete_status=0 AND p.promotion_type=0 AND s.stock>10 AND s.price>50 AND s.price<100 ORDER BY s.price,s.id LIMIT 1").split('\t')
assert len(row) == 3, 'No suitable non-promotional sandbox SKU'
product, sku, price = int(row[0]), int(row[1]), Decimal(row[2])
stock = lambda:int(mysql(f'SELECT stock FROM pms_sku_stock WHERE id={sku}'))
stock_before = stock()
data(request(PORTAL,'/member/address/add',payload={'name':'发布验收','phoneNumber':phone,'defaultStatus':1,'province':'测试省','city':'测试市','region':'测试区','detailAddress':'沙盒验收地址，无需真实发货'},token=token))
address = int(data(request(PORTAL,'/member/address/list',token=token))[-1]['id'])

def cart():
    data(request(PORTAL,'/cart/add?zoneCode=PICK',payload={'productId':product,'productSkuId':sku,'quantity':1},token=token))
    return int(data(request(PORTAL,'/cart/list',token=token))[-1]['id'])

cart_id = cart()
body = {'payType':0,'cartIds':[cart_id],'memberReceiveAddressId':address,'useBalance':True}
checkout_key = 'QA'+suffix+'checkout'
purchase = data(request(PORTAL,'/order/generateOrder',payload=body,token=token,key=checkout_key))
purchase_id = int(purchase['order']['id'])
expected = Decimal('100.10') - price
check('wallet_purchase_paid_and_debited_exactly', purchase['paidImmediately'] and balance() == expected)
data(request(PORTAL,'/order/generateOrder',payload=body,token=token,key=checkout_key))
details = data(request(PORTAL,f'/order/detail/{purchase_id}',token=token))
check('order_and_stock_settled_once', int(details['status']) == 1 and Decimal(str(details['balanceAmount'])) == price and int(details['payType']) == 4 and stock() == stock_before-1)
body['cartIds'] = [cart()]
failure = request(PORTAL,'/order/generateOrder',payload=body,token=token,key='QA'+suffix+'insufficient')
check('insufficient_balance_has_no_debit_order_or_stock_change', failure['code'] != 200 and balance() == expected and stock() == stock_before-1 and mysql(f'SELECT COUNT(*) FROM oms_order WHERE member_id={member}') == '1')
membership = data(request(PORTAL,'/member/membership/orders',payload={'memberId':member,'level':'MEMBER','payChannel':'ALIPAY','payMode':'ALIPAY_WAP','requestId':'QA'+suffix+'membership'},token=token))
data(request(PORTAL,f"/mockPayment/membership/confirm?orderId={membership['orderId']}",form={},token=token))
data(request(PORTAL,f"/mockPayment/membership/confirm?orderId={membership['orderId']}",form={},token=token))
check('membership_confirmed_once_and_both_points_credited', data(request(PORTAL,'/member/tri-pools',token=token))['tier'] == 'MEMBER' and mysql(f'SELECT GROUP_CONCAT(available ORDER BY points_type) FROM ums_points_account WHERE member_id={member}') == '3999,3999')
withdraw_key = 'QA'+suffix+'withdraw'
available = Decimal(mysql(f'SELECT available_earnings FROM ums_earnings WHERE member_id={member}'))
withdraw_amount = min(Decimal('100.00'),available)
assert withdraw_amount >= Decimal('1.00'), 'Not enough new-member earnings for withdrawal acceptance'
withdraw = lambda:data(request(PORTAL,'/member/withdraw/apply',payload={'amount':str(withdraw_amount),'idempotencyKey':withdraw_key},token=token))
with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
    withdrawals = list(pool.map(lambda _:withdraw(),range(4)))
net = Decimal(str(withdrawals[0]['actualAmount']))
check('earnings_withdrawal_transferred_once', all(w['status']==3 for w in withdrawals) and balance() == expected+net and mysql(f'SELECT COUNT(*) FROM ums_withdraw_apply WHERE member_id={member}') == '1')
admin_name, admin_password = 'release_qa_admin_'+suffix, secrets.token_urlsafe(24)
encoded = bcrypt.hashpw(admin_password.encode(),bcrypt.gensalt()).decode()
admin_id = None
try:
    admin_id = int(mysql(f"INSERT INTO ums_admin(username,password,status,create_time,note) VALUES('{admin_name}','{encoded}',1,NOW(),'Temporary release QA; removed after acceptance'); SELECT LAST_INSERT_ID();"))
    mysql(f'INSERT INTO ums_admin_role_relation(admin_id,role_id) VALUES({admin_id},5)')
    login = data(request(ADMIN,'/admin/login',payload={'username':admin_name,'password':admin_password}))
    admin_token = login['tokenHead']+login['token']
    wallet = data(request(ADMIN,f'/p0/wallet?memberId={member}&tab=ledger',token=admin_token))
    check('admin_wallet_matches_portal_and_all_movements', Decimal(wallet['summary']['balance'])==balance() and Decimal(wallet['summary']['recharged'])==Decimal('100.10') and Decimal(wallet['summary']['spent'])==price and Decimal(wallet['summary']['withdrawalTransferred'])==net and wallet['total']==3)
    recharges=data(request(ADMIN,f'/p0/wallet?memberId={member}&tab=recharges',token=admin_token))
    check('admin_recharge_order_paid', recharges['total']==1 and recharges['rows'][0]['status']=='PAID')
    info=data(request(ADMIN,'/admin/info',token=admin_token))
    check('wallet_menu_granted_to_platform_admin', any(m['name']=='financeSpendableWallet' for m in info['menus']))
    request(ADMIN,'/admin/logout',form={},token=admin_token)
finally:
    if admin_id:
        mysql(f'DELETE FROM ums_admin_role_relation WHERE admin_id={admin_id}; DELETE FROM ums_admin WHERE id={admin_id}')
check('temporary_admin_removed', mysql(f"SELECT COUNT(*) FROM ums_admin WHERE username='{admin_name}'")=='0')
check('private_profile_requires_authentication', request(PORTAL,'/sso/info')['code'] != 200)
result = {'checks':len(checks),'passed':checks,'memberId':member,'username':username,
          'recharged':'100.10','goodsPrice':str(price),'balanceAfterPurchase':str(expected),
          'withdrawalGross':str(withdraw_amount),'withdrawalNet':str(net),'finalBalance':str(balance()),'orderId':purchase_id}
output = Path(__file__).parent / 'acceptance-result.json'
output.write_text(json.dumps(result,ensure_ascii=False,indent=2))
private = Path(__file__).parent / 'qa-browser-account.json'
private.write_text(json.dumps({'username':username,'password':password,'memberId':member}))
private.chmod(0o600)
print(json.dumps(result,ensure_ascii=False),flush=True)
