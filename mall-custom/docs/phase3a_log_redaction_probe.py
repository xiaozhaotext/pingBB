"""Local only: verify login works without password or JWT in application logs."""
import json
import pathlib
import urllib.request

root = pathlib.Path(__file__).resolve().parents[1]
password = (root / ".tools" / "admin-test-password.txt").read_text().strip()
payload = json.dumps({"username": "phase3a_admin", "password": password}).encode()
request = urllib.request.Request(
    "http://127.0.0.1:8080/admin/login",
    data=payload,
    headers={"Content-Type": "application/json"},
    method="POST",
)
with urllib.request.urlopen(request, timeout=5) as response:
    body = json.load(response)
token = body.get("data", {}).get("token", "")
log_text = (root / "docs" / "Phase3A-admin-redaction.stdout.log").read_text(errors="replace")
print("login_code", body.get("code"))
print("password_logged", password in log_text)
print("token_logged", bool(token and token in log_text))
if body.get("code") != 200 or not token or password in log_text or token in log_text:
    raise SystemExit(1)
