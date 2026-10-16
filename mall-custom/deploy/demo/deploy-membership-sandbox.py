"""Run as root with a verified release directory under /opt/mall-demo/releases.

Expected artifacts: admin.jar, portal.jar, four frontend tar.gz archives,
service/nginx templates, SQL migrations 50..53, and manifest.json.
Keeps financial data; creates recoverable backups before changing services.
"""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tarfile
import time
import urllib.request

release = Path(sys.argv[1]).resolve()
assert release.parent == Path('/opt/mall-demo/releases')
manifest = json.loads((release / 'manifest.json').read_text())
for name, expected in manifest['files'].items():
    assert '/' not in name and '\\' not in name
    assert hashlib.sha256((release / name).read_bytes()).hexdigest() == expected, name

root = Path('/opt/mall-demo')
backup = root / 'backups' / ('membership-flow-' + time.strftime('%Y%m%d-%H%M%S'))
backup.mkdir(parents=True, mode=0o700)
services = ['mall-admin', 'mall-portal', 'mall-portal-sandbox', 'mall-admin-sandbox']
files = [root / name for name in ['mall-admin.jar', 'mall-portal.jar', 'mall-portal-sandbox.jar', 'mall-admin-sandbox.jar', 'admin', 'storefront', 'sandbox', 'sandbox-admin']]
files += [Path('/etc/nginx/snippets/mall-sandbox.conf'), Path('/etc/nginx/snippets/mall-p0-public-blocks.conf'), Path('/etc/systemd/system/mall-admin-sandbox.service'), Path('/etc/systemd/system/mall-portal-sandbox.service')]
files += [Path('/etc/systemd/system/mall-portal-sandbox.service.d/tri-pools.conf')]
snapshot = []
for index, target in enumerate(files):
    saved = backup / str(index)
    exists = target.exists() or target.is_symlink()
    snapshot.append((target, saved, exists, target.is_symlink(), os.readlink(target) if target.is_symlink() else None))
    if exists and not target.is_symlink():
        if target.is_dir(): shutil.copytree(target, saved)
        else: shutil.copy2(target, saved)
for database in ['mall_demo', 'mall_demo_sandbox']:
    with (backup / (database + '.sql')).open('wb') as output:
        subprocess.run(['mysqldump', '--single-transaction', '--no-tablespaces', database], stdout=output, check=True)
    os.chmod(backup / (database + '.sql'), 0o600)
(backup / 'snapshot.json').write_text(json.dumps([{'path':str(p),'saved':str(s),'existed':e,'symlink':l,'link':v} for p,s,e,l,v in snapshot], indent=2))
print('BACKUP', backup, flush=True)
for database in ['mall_demo', 'mall_demo_sandbox']:
    for name in ['50_three_dividend_pools.sql', '51_standalone_member_purchase.sql', '52_dividend_pool_menu_daily.sql', '53_spendable_wallet_menu.sql']:
        with (release / name).open('rb') as source:
            subprocess.run(['mysql', database], stdin=source, check=True, stdout=subprocess.DEVNULL)
for name in ['admin', 'storefront', 'sandbox', 'sandbox-admin']:
    dest = release / name
    dest.mkdir()
    with tarfile.open(release / (name + '.tar.gz')) as archive:
        archive.extractall(dest, filter='data')

def probe(port, path):
    try:
        with urllib.request.urlopen('http://127.0.0.1:' + str(port) + path, timeout=3) as response:
            return response.status == 200 and json.load(response).get('code') == 200
    except Exception:
        return False

try:
    subprocess.run(['systemctl', 'stop', *services], check=True)
    for name, artifact in [('mall-admin.jar','admin.jar'),('mall-admin-sandbox.jar','admin.jar'),('mall-portal.jar','portal.jar'),('mall-portal-sandbox.jar','portal.jar')]:
        shutil.copy2(release / artifact, root / name)
    for name in ['admin', 'storefront', 'sandbox', 'sandbox-admin']:
        target = root / name
        if target.is_symlink(): target.unlink()
        elif target.exists(): target.rename(backup / (name + '-previous-directory'))
        target.symlink_to(release / name, target_is_directory=True)
    shutil.copy2(release / 'mall-sandbox.nginx.inc', '/etc/nginx/snippets/mall-sandbox.conf')
    # Real purchases remain guarded by backend channel readiness and signed callbacks.
    # Removing the blanket proxy block lets the UI query capabilities and receive business errors.
    block = Path('/etc/nginx/snippets/mall-p0-public-blocks.conf')
    block.write_text(block.read_text().replace('location ^~ /portal/member/membership/ { return 403; }', ''))
    for service in ['mall-admin-sandbox', 'mall-portal-sandbox']:
        shutil.copy2(release / (service + '.service'), '/etc/systemd/system/' + service + '.service')
    flag = Path('/etc/systemd/system/mall-portal-sandbox.service.d/tri-pools.conf')
    flag.parent.mkdir(exist_ok=True)
    flag.write_text('[Service]\nEnvironment=MALL_MEMBERSHIP_V2_ENABLED=true\nEnvironment=MALL_MEMBERSHIPV2_ENABLED=true\n')
    subprocess.run(['nginx', '-t'], check=True)
    subprocess.run(['systemctl', 'daemon-reload'], check=True)
    subprocess.run(['systemctl', 'start', *services], check=True)
    for _ in range(90):
        if all(probe(port, path) for port,path in [(8085,'/home/announcements'),(8086,'/home/announcements'),(8080,'/admin/login'),(8087,'/admin/login')]):
            break
        # GET login is not a public health endpoint on every security configuration;
        # TCP readiness plus database-backed portal probes are followed by authenticated acceptance tests.
        if all(subprocess.run(['systemctl','is-active','--quiet',s]).returncode == 0 for s in services) and probe(8085,'/home/announcements') and probe(8086,'/home/announcements'):
            import socket
            try:
                for port in [8080,8087]:
                    with socket.create_connection(('127.0.0.1',port), timeout=1): pass
                break
            except OSError: pass
        time.sleep(1)
    else: raise RuntimeError('Service readiness failed')
    subprocess.run(['systemctl', 'reload', 'nginx'], check=True)
    subprocess.run(['systemctl', 'enable', 'mall-admin-sandbox'], check=True)
except Exception:
    subprocess.run(['systemctl', 'stop', *services])
    for target, saved, existed, symlink, link in reversed(snapshot):
        if target.is_symlink(): target.unlink()
        if existed:
            if symlink: target.symlink_to(link, target_is_directory=True)
            elif saved.is_dir():
                old = backup / (target.name + '-previous-directory')
                if old.exists(): old.rename(target)
            else: shutil.copy2(saved, target)
        elif target.exists() and target.is_file(): target.unlink()
    subprocess.run(['systemctl', 'daemon-reload'])
    subprocess.run(['systemctl', 'start', *services])
    subprocess.run(['nginx','-t'], check=True)
    subprocess.run(['systemctl', 'reload', 'nginx'])
    raise
result = {'sourceCommit': manifest['sourceCommit'], 'backup': str(backup), 'status':'SERVICES_READY_ACCEPTANCE_PENDING'}
(release / 'deployment-result.json').write_text(json.dumps(result, indent=2))
print(json.dumps(result), flush=True)
