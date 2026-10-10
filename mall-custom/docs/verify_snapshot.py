"""Check every regular file in the recovered official mall worktree against HEAD blobs."""
from pathlib import Path
import hashlib
import subprocess

repo = Path(__file__).resolve().parents[1] / "mall"
listing = subprocess.check_output(
    ["git", "-C", str(repo), "ls-tree", "-r", "-z", "HEAD"]
)
checked = 0
missing = []
different = []
for record in listing.split(b"\0"):
    if not record:
        continue
    metadata, rel = record.split(b"\t", 1)
    mode, kind, expected = metadata.split(b" ")
    if kind != b"blob":
        continue
    path = repo / rel.decode("utf-8")
    if not path.is_file():
        missing.append(rel.decode("utf-8"))
        continue
    content = path.read_bytes()
    actual = hashlib.sha1(
        b"blob " + str(len(content)).encode() + b"\0" + content
    ).hexdigest().encode()
    checked += 1
    if actual != expected:
        different.append(rel.decode("utf-8"))
print(f"checked={checked} missing={len(missing)} different={len(different)}")
for label, paths in (("missing", missing), ("different", different)):
    for path in paths[:10]:
        print(f"{label}: {path}")
raise SystemExit(bool(missing or different))
