#!/usr/bin/env bash
# Deploy only the isolated membership sandbox from the reviewed Git commit.
set -Eeuo pipefail

expected_sha=6907b67db541ef06b72b789e6c3ba1a8b3e4eb4a
root=/opt/mall-demo
release="$root/releases/sandbox-$expected_sha"
source_dir="$release/source"
backup="$release/backup"
portal_jar="$root/mall-portal-sandbox.jar"
admin_jar="$root/mall-admin-sandbox.jar"
sandbox_site="$root/sandbox"
sandbox_admin_site="$root/sandbox-admin"
changed=0

if [[ $(id -u) -ne 0 ]]; then
  echo 'Run with sudo/root from the server console.' >&2
  exit 1
fi
for tool in git mvn npm node java systemctl curl python3; do
  command -v "$tool" >/dev/null || { echo "Missing $tool" >&2; exit 1; }
done
for service in mall-portal-sandbox mall-admin-sandbox; do
  systemctl is-active --quiet "$service" || { echo "$service is not active; inspect it before deploying" >&2; exit 1; }
done
systemctl cat mall-portal-sandbox | grep -Fq -- '--mall.payment.mock.enabled=true' || {
  echo 'Sandbox mock payment is not enabled in the existing service.' >&2; exit 1;
}
systemctl cat mall-portal-sandbox | grep -Fq -- '--mall.membership-v2.enabled=true' || {
  echo 'Sandbox four-tier membership is not enabled in the existing service.' >&2; exit 1;
}
for path in "$portal_jar" "$admin_jar" "$sandbox_site" "$sandbox_admin_site"; do
  [[ -e "$path" ]] || { echo "Missing existing deployment: $path" >&2; exit 1; }
done
[[ ! -e "$release" ]] || { echo "Release directory already exists: $release" >&2; exit 1; }
mkdir -p "$release" "$backup"

restore() {
  status=$?
  if [[ $status -ne 0 && $changed -eq 1 ]]; then
    echo 'Deployment failed; restoring the previous sandbox release.' >&2
    systemctl stop mall-portal-sandbox mall-admin-sandbox || true
    cp -a "$backup/mall-portal-sandbox.jar" "$portal_jar" || true
    cp -a "$backup/mall-admin-sandbox.jar" "$admin_jar" || true
    for name in sandbox sandbox-admin; do
      if [[ -e "$backup/$name" || -L "$backup/$name" ]]; then
        if [[ -L "$root/$name" ]]; then unlink "$root/$name"; fi
        mv "$backup/$name" "$root/$name" || true
      fi
    done
    systemctl start mall-portal-sandbox mall-admin-sandbox || true
  fi
}
trap restore EXIT

git clone --depth 2 --branch codex/fix-auth-buttons-20261016 \
  https://github.com/xiaozhaotext/pingBB.git "$source_dir"
git -C "$source_dir" checkout --detach "$expected_sha"
actual_sha=$(git -C "$source_dir" rev-parse HEAD)
[[ "$actual_sha" == "$expected_sha" ]] || {
  echo "Unexpected commit: $actual_sha" >&2; exit 1;
}

echo 'Building sandbox backend…'
(cd "$source_dir/mall-custom/mall" && MAVEN_OPTS='-Xmx768m' \
  mvn -B -pl mall-admin,mall-portal -am -DskipTests -Ddocker.skip=true package)
new_portal="$source_dir/mall-custom/mall/mall-portal/target/mall-portal-1.0-SNAPSHOT.jar"
new_admin="$source_dir/mall-custom/mall/mall-admin/target/mall-admin-1.0-SNAPSHOT.jar"
[[ -s "$new_portal" && -s "$new_admin" ]] || { echo 'Backend JAR is missing.' >&2; exit 1; }

echo 'Building sandbox H5…'
(cd "$source_dir/mall-custom/mall-app-web" && npm ci --no-audit --no-fund && \
  NODE_OPTIONS='--max-old-space-size=1024' npm run build:h5:sandbox)
new_site="$source_dir/mall-custom/mall-app-web/dist/build/h5"
[[ -s "$new_site/index.html" ]] || { echo 'Sandbox H5 build is missing.' >&2; exit 1; }

echo 'Building sandbox admin…'
(cd "$source_dir/mall-custom/mall-admin-web" && npm ci --no-audit --no-fund && \
  VITE_BASE_SERVER_URL=/sandbox/admin-api VITE_SANDBOX_MODE=true \
  NODE_OPTIONS='--max-old-space-size=1024' npm run build)
new_admin_site="$source_dir/mall-custom/mall-admin-web/dist"
[[ -s "$new_admin_site/index.html" ]] || { echo 'Sandbox admin build is missing.' >&2; exit 1; }

cp -a "$portal_jar" "$backup/mall-portal-sandbox.jar"
cp -a "$admin_jar" "$backup/mall-admin-sandbox.jar"
journalctl -u mall-portal-sandbox -n 300 --no-pager > "$backup/portal-before.log" || true
echo "Backup: $backup"

changed=1
systemctl stop mall-portal-sandbox mall-admin-sandbox
install -m 0644 "$new_portal" "$portal_jar"
install -m 0644 "$new_admin" "$admin_jar"
mv "$sandbox_site" "$backup/sandbox"
mv "$sandbox_admin_site" "$backup/sandbox-admin"
ln -s "$new_site" "$sandbox_site"
ln -s "$new_admin_site" "$sandbox_admin_site"
systemctl start mall-portal-sandbox mall-admin-sandbox

echo 'Waiting for the sandbox APIs…'
ready=0
for attempt in $(seq 1 60); do
  if systemctl is-active --quiet mall-portal-sandbox && \
     systemctl is-active --quiet mall-admin-sandbox && \
     curl -fsS --max-time 3 http://127.0.0.1:8086/sso/testRegistrationEnabled \
       | python3 -c 'import json,sys; v=json.load(sys.stdin); sys.exit(0 if v.get("code")==200 and v.get("data") is True else 1)' && \
     [[ -s "$sandbox_site/index.html" && -s "$sandbox_admin_site/index.html" ]]; then
    ready=1
    break
  fi
  sleep 2
done
[[ $ready -eq 1 ]] || { echo 'Sandbox readiness check failed.' >&2; exit 1; }
changed=0
printf 'DEPLOYED %s\nBACKUP %s\n' "$actual_sha" "$backup"
echo 'Next: test registration, login and purchase through /sandbox/; inspect portal logs for failed orders.'
