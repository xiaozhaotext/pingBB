"""Exercise membership purchases against an isolated sandbox portal.

Run on the demo host with BASE_URL=http://127.0.0.1:8086. Creates two disposable
members in mall_demo_sandbox and uses only the sandbox API and database.
"""
import json
import os
import secrets
import subprocess
import urllib.parse
import urllib.request


BASE = os.environ.get("BASE_URL", "http://127.0.0.1:8086").rstrip("/")
if BASE not in ("http://127.0.0.1:8086", "http://localhost:8086"):
    raise SystemExit("Refusing to run against a nonlocal sandbox API")


def call(path, *, form=None, payload=None, auth=None):
    data = None
    headers = {}
    if form is not None:
        data = urllib.parse.urlencode(form).encode()
        headers["Content-Type"] = "application/x-www-form-urlencoded"
    if payload is not None:
        data = json.dumps(payload).encode()
        headers["Content-Type"] = "application/json"
    if auth:
        headers["Authorization"] = auth
    request = urllib.request.Request(BASE + path, data=data, headers=headers)
    with urllib.request.urlopen(request, timeout=20) as response:
        result = json.load(response)
    if result.get("code") != 200:
        raise AssertionError(f"{path}: {result.get('code')} {result.get('message')}")
    return result.get("data")


def mysql(sql):
    result = subprocess.run(
        ["sudo", "mysql", "-N", "-B", "mall_demo_sandbox", "-e", sql],
        check=True, text=True, capture_output=True,
    )
    return result.stdout.strip()


def register():
    suffix = secrets.token_hex(6)
    username = "sbx_" + suffix
    password = secrets.token_urlsafe(18)
    phone = "199" + "".join(str(secrets.randbelow(10)) for _ in range(8))
    code = call("/sso/getAuthCode?" + urllib.parse.urlencode({"telephone": phone}))
    assert code, "Sandbox SMS must echo the test code; no live SMS should be sent"
    call("/sso/register", form={
        "username": username, "password": password,
        "telephone": phone, "authCode": code,
    })
    token = call("/sso/login", form={"username": username, "password": password})
    auth = token["tokenHead"] + token["token"]
    member_id = int(call("/sso/info", auth=auth)["id"])
    return member_id, auth


assert mysql("SELECT config_value FROM sys_config WHERE config_key='membership.purchase.enabled'") == "1"
assert mysql("SELECT config_value FROM sys_config WHERE config_key='dividend.distribution.enabled'") == "0"

inviter, _ = register()
buyer, auth = register()
mysql(f"INSERT INTO ums_member_recommend(member_id,inviter_id) VALUES({buyer},{inviter})")

for level, expected_cash in (("PARTNER", "399.90"), ("COFOUNDER", "200.00")):
    request_id = "SBX" + secrets.token_hex(12)
    order = call("/member/membership/orders", payload={
        "memberId": buyer, "level": level, "payChannel": "ALIPAY",
        "payMode": "ALIPAY_WAP", "requestId": request_id,
    }, auth=auth)
    assert order["payment"]["mock"] == "true"
    order_id = int(order["orderId"])
    first = call(f"/mockPayment/membership/confirm?orderId={order_id}", form={}, auth=auth)
    second = call(f"/mockPayment/membership/confirm?orderId={order_id}", form={}, auth=auth)
    assert first == "CONFIRMED" and second == "ALREADY_PAID", (first, second)
    row = mysql(
        "SELECT o.status,r.reward_amount,r.reward_points,"
        "(SELECT COUNT(*) FROM member_point_ledger WHERE member_id=o.member_id AND source_id=o.id),"
        "(SELECT COUNT(*) FROM member_level_change_log WHERE order_id=o.id) "
        "FROM membership_purchase_order o JOIN member_referral_reward r ON r.order_id=o.id "
        f"WHERE o.id={order_id}"
    ).split("\t")
    assert row[0] == "PAID" and row[1] == expected_cash and row[3:] == ["2", "1"], row
    assert int(row[2]) == (3999 if level == "PARTNER" else 10000), row
    print(f"{level}: order={order_id}, status={row[0]}, cash={row[1]}, "
          f"referral_points={row[2]}, buyer_ledger={row[3]}, level_log={row[4]}, duplicate={second}")

identity = mysql(f"SELECT identity_type FROM ums_member_identity WHERE member_id={buyer}")
assert identity == "2", identity
print(f"buyer={buyer}, inviter={inviter}, final_identity={identity}; sandbox database only")
