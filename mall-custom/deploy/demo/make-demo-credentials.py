"""Rotate imported local test accounts before exposing the demo."""

from pathlib import Path
import secrets

import bcrypt


root = Path(__file__).resolve().parents[2]
private = root / ".tools"
private.mkdir(exist_ok=True)

admin_password = secrets.token_urlsafe(24)
member_password = secrets.token_urlsafe(24)
admin_hash = bcrypt.hashpw(admin_password.encode(), bcrypt.gensalt()).decode()
member_hash = bcrypt.hashpw(member_password.encode(), bcrypt.gensalt()).decode()

(private / "deploy-demo-admin-password.txt").write_text(admin_password + "\n", encoding="utf-8")
(private / "deploy-demo-member-password.txt").write_text(member_password + "\n", encoding="utf-8")
(private / "rotate-demo-users.sql").write_text(
    "START TRANSACTION;\n"
    "UPDATE ums_admin SET status=0;\n"
    f"UPDATE ums_admin SET username='demo_admin', password='{admin_hash}', status=1 WHERE id=3;\n"
    "UPDATE ums_member SET status=0;\n"
    f"UPDATE ums_member SET username='demo_member', password='{member_hash}', status=1 WHERE id=12;\n"
    "COMMIT;\n",
    encoding="utf-8",
)
print("Demo credentials and rotation SQL saved under .tools; passwords were not printed.")
