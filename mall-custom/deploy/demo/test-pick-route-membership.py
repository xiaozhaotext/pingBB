"""Sandbox-only acceptance: PICK catalog and a visitor's paid travel-route order.

Run on the demo host after applying 54_sandbox_pick_tours_only.sql and deploying
the matching portal/H5 build. Creates one disposable sandbox account and order.
"""
import json
import secrets
import subprocess
import urllib.parse
import urllib.request
from decimal import Decimal

BASE = "http://127.0.0.1:8086"


def call(path, *, form=None, payload=None, token=None, headers=None):
    request_headers = dict(headers or {})
    data = None
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
    return subprocess.run(["sudo", "mysql", "-N", "-B", "mall_demo_sandbox", "-e", sql],
                          check=True, text=True, capture_output=True).stdout.strip()


catalog = call("/product/zone/PICK")
assert catalog, "PICK catalog is empty"
assert all(Decimal(str(item["price"])) == Decimal("3999") for item in catalog), catalog
ids = [int(item["id"]) for item in catalog]
assert mysql("SELECT COUNT(*) FROM pms_product_zone z JOIN pms_product p ON p.id=z.product_id "
             "LEFT JOIN tri_membership_product t ON t.product_id=p.id AND t.enabled=1 "
             "WHERE z.zone_code='PICK' AND (p.price<>3999 OR t.product_id IS NULL)") == "0"
assert mysql("SELECT COUNT(*) FROM pms_product_zone WHERE zone_code='PICK'") == str(len(ids))

product = ids[0]
sku = int(mysql(f"SELECT id FROM pms_sku_stock WHERE product_id={product} AND price=3999 "
                "AND stock>0 ORDER BY id LIMIT 1"))
username = "pickqa_" + secrets.token_hex(6)
password = secrets.token_urlsafe(18)
phone = "199" + "".join(str(secrets.randbelow(10)) for _ in range(8))
code = call("/sso/getAuthCode?" + urllib.parse.urlencode({"telephone": phone}))
assert code, "Sandbox SMS did not return a test code"
call("/sso/register", form={"username": username, "password": password,
                             "telephone": phone, "authCode": code})
login = call("/sso/login", form={"username": username, "password": password})
token = login["tokenHead"] + login["token"]
member = int(call("/sso/info", token=token)["id"])
before = call("/member/tri-pools", token=token)
assert before["tier"] == "VISITOR", before


def points(point_type):
    return int(mysql("SELECT COALESCE((SELECT balance FROM ums_points_account "
                     f"WHERE member_id={member} AND points_type={point_type}),0)"))


prior_product, prior_repurchase = points(1), points(2)
call("/member/address/add", payload={"name": "沙箱路线验收", "phoneNumber": "19900000000",
                                     "defaultStatus": 1, "province": "测试省", "city": "测试市",
                                     "region": "测试区", "detailAddress": "沙箱验收地址"}, token=token)
address = int(call("/member/address/list", token=token)[-1]["id"])
call("/cart/add?zoneCode=PICK", payload={"productId": product,
                                           "productSkuId": sku, "quantity": 1}, token=token)
cart = next(int(row["id"]) for row in call("/cart/list", token=token)
            if int(row["productId"]) == product and int(row["productSkuId"]) == sku)
created = call("/order/generateOrder", payload={"payType": 0, "cartIds": [cart],
                                                 "memberReceiveAddressId": address}, token=token,
               headers={"Idempotency-Key": "PICKQA" + secrets.token_hex(12)})
order = int(created["order"]["id"])
assert Decimal(str(created["order"]["payAmount"])) == Decimal("3999"), created
assert call(f"/mockPayment/confirm?orderId={order}", form={}, token=token) == "CONFIRMED"
assert call(f"/mockPayment/confirm?orderId={order}", form={}, token=token) == "ALREADY_CONFIRMED"
after = call("/member/tri-pools", token=token)
partner = next(pool for pool in after["pools"] if pool["code"] == "PARTNER")
assert after["tier"] == "MEMBER" and int(partner["shares"]) == 1, after
assert Decimal(str(partner["cap"])) == Decimal("1200"), partner
assert (points(1) - prior_product, points(2) - prior_repurchase) == (3999, 3999)
assert mysql(f"SELECT COUNT(*) FROM tri_payment_event WHERE source_key='ORDER:{order}' "
             f"AND member_id={member} AND paid_amount=3999") == "1"
funding = mysql("SELECT pool_code,amount FROM tri_pool_ledger "
                f"WHERE source_key='ORDER:{order}' AND entry_type='FUND' ORDER BY pool_code")
assert funding.splitlines() == ["COFOUNDER\t200.00", "OPERATIONS\t200.00", "PARTNER\t500.00"], funding
print(f"PICK_ROUTE_MEMBERSHIP_PASSED member={member} product={product} order={order} "
      f"routes={len(catalog)} tier=MEMBER points=3999+3999 partnerCap=1200")
