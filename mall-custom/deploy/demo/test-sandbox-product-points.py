"""Buy two real catalog SKUs using sandbox product and repurchase points.

Runs on the demo host only. All writes stay inside mall_demo_sandbox.
"""
import json
import secrets
import subprocess
import urllib.parse
import urllib.request


BASE = "http://127.0.0.1:8086"


def call(path, *, form=None, payload=None, token=None, headers=None):
    data = None
    request_headers = dict(headers or {})
    if form is not None:
        data = urllib.parse.urlencode(form).encode()
        request_headers["Content-Type"] = "application/x-www-form-urlencoded"
    if payload is not None:
        data = json.dumps(payload).encode()
        request_headers["Content-Type"] = "application/json"
    if token:
        request_headers["Authorization"] = token
    request = urllib.request.Request(BASE + path, data=data, headers=request_headers)
    with urllib.request.urlopen(request, timeout=20) as response:
        result = json.load(response)
    if result.get("code") != 200:
        raise AssertionError(f"{path}: {result.get('code')} {result.get('message')}")
    return result.get("data")


def mysql(sql):
    return subprocess.run(
        ["sudo", "mysql", "-N", "-B", "mall_demo_sandbox", "-e", sql],
        check=True, text=True, capture_output=True,
    ).stdout.strip()


def register():
    username = "item_sbx_" + secrets.token_hex(5)
    password = secrets.token_urlsafe(18)
    phone = "199" + "".join(str(secrets.randbelow(10)) for _ in range(8))
    code = call("/sso/getAuthCode?" + urllib.parse.urlencode({"telephone": phone}))
    assert code, "Sandbox SMS test code was not echoed"
    call("/sso/register", form={"username": username, "password": password,
                                 "telephone": phone, "authCode": code})
    login = call("/sso/login", form={"username": username, "password": password})
    token = login["tokenHead"] + login["token"]
    return int(call("/sso/info", token=token)["id"]), token


def balance(member_id, point_type):
    return int(mysql(f"SELECT balance FROM ums_points_account WHERE member_id={member_id} AND points_type={point_type}"))


def add_product(token, member_id, product_id, sku_id):
    call("/cart/add", payload={"productId": product_id, "productSkuId": sku_id, "quantity": 1}, token=token)
    items = call("/cart/list", token=token)
    return next(int(item["id"]) for item in items
                if item["productId"] == product_id and item["productSkuId"] == sku_id)


def order(token, address_id, cart_id, product_points=0, rebuy_points=0):
    result = call("/order/generateOrder", payload={
        "payType": 0, "cartIds": [cart_id], "memberReceiveAddressId": address_id,
        "useIntegration": product_points, "useRepurchasePoints": rebuy_points,
    }, token=token, headers={"Idempotency-Key": "item" + secrets.token_hex(14)})
    return int(result["order"]["id"])


assert mysql("SELECT config_value FROM sys_config WHERE config_key='product.points.yuan_per_point'") == "1.00"
assert mysql("SELECT config_value FROM sys_config WHERE config_key='membership.purchase.enabled'") == "1"
inviter, _ = register()
buyer, token = register()
mysql(f"INSERT INTO ums_member_recommend(member_id,inviter_id) VALUES({buyer},{inviter})")
membership = call("/member/membership/orders", payload={
    "memberId": buyer, "level": "PARTNER", "payChannel": "ALIPAY",
    "payMode": "ALIPAY_WAP", "requestId": "PR" + secrets.token_hex(13),
}, token=token)
call(f"/mockPayment/membership/confirm?orderId={membership['orderId']}", form={}, token=token)
assert (balance(buyer, 1), balance(buyer, 2)) == (3999, 3999)

call("/member/address/add", payload={
    "name": "沙箱收件人", "phoneNumber": "19900000000", "defaultStatus": 1,
    "province": "测试省", "city": "测试市", "region": "测试区", "detailAddress": "沙箱订单地址",
}, token=token)
addresses = call("/member/address/list", token=token)
address_id = int(addresses[-1]["id"])

# Self-selection SKU 110 lists at CNY 3788 with CNY 89 promotion deduction.
cart_id = add_product(token, buyer, 26, 110)
preview = call("/order/generateConfirmOrder", payload=[cart_id], token=token)
assert int(preview["memberIntegration"]) == 3999 and str(preview["productPointYuanPerPoint"]) in ("1", "1.0", "1.00")
product_order = order(token, address_id, cart_id, product_points=3699)
details = call(f"/order/detail/{product_order}", token=token)
assert float(details["integrationAmount"]) == 3699 and float(details["payAmount"]) == 0, details
assert balance(buyer, 1) == 300
assert mysql(f"SELECT used_points FROM oms_order_point_usage WHERE order_id={product_order} AND point_type='PRODUCT'") == "3699"
assert mysql(f"SELECT change_amount FROM member_point_ledger WHERE source_id={product_order} AND source_type='PURCHASE_DEDUCTION' AND point_type='PRODUCT'") == "-3699"
assert call(f"/mockPayment/confirm?orderId={product_order}", form={}, token=token) == "CONFIRMED"
assert call(f"/mockPayment/confirm?orderId={product_order}", form={}, token=token) == "ALREADY_CONFIRMED"
print(f"PRODUCT: order={product_order}, listed=3788.00, promotion_deduction=89.00, points=3699, cash=0.00, balance=300, duplicate=ALREADY_CONFIRMED")

# REBUY SKU 163 costs CNY 100, precisely 30 points and CNY 70 cash.
cart_id = add_product(token, buyer, 36, 163)
preview = call("/order/generateConfirmOrder", payload=[cart_id], token=token)
assert preview["rebuyEligible"] is True and int(preview["repurchasePointsBalance"]) == 3999
rebuy_order = order(token, address_id, cart_id, rebuy_points=30)
details = call(f"/order/detail/{rebuy_order}", token=token)
assert float(details["totalAmount"]) == 100 and float(details["payAmount"]) == 70, details
assert balance(buyer, 2) == 3969
assert mysql(f"SELECT used_points FROM oms_order_point_usage WHERE order_id={rebuy_order} AND point_type='REPURCHASE'") == "30"
assert call(f"/mockPayment/confirm?orderId={rebuy_order}", form={}, token=token) == "CONFIRMED"
print(f"REBUY: order={rebuy_order}, price=100.00, points=30, cash=70.00, balance=3969")

# An unpaid order must release its reserved product points exactly once.
cart_id = add_product(token, buyer, 26, 110)
cancel_order = order(token, address_id, cart_id, product_points=1)
assert balance(buyer, 1) == 299
call("/order/cancelUserOrder?orderId=" + str(cancel_order), form={}, token=token)
assert balance(buyer, 1) == 300
assert mysql(f"SELECT change_amount FROM member_point_ledger WHERE source_id={cancel_order} AND source_type='REFUND' AND point_type='PRODUCT'") == "1"
print(f"CANCEL: order={cancel_order}, reserved=1, refunded=1, product_balance=300")

# Ordinary merchandise is not a membership purchase; it must not award another referral bonus.
assert mysql(f"SELECT COUNT(*) FROM ums_earnings_ledger WHERE member_id={inviter} AND change_type='share_bonus'") == "0"
print(f"buyer={buyer}, inviter={inviter}; normal-item referral bonus=0 (mind-map scope)")
