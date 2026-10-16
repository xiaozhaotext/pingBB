"""Deploy verified wallet/auth artifacts while preserving the host's service and nginx config.
Run as root: python3 deploy-wallet-auth.py /opt/mall-demo/releases/<release>.
Financial data is never restored automatically; only an additive menu migration is applied.
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
required = {'admin.jar', 'portal.jar', 'admin.tar.gz', 'storefront.tar.gz', 'sandbox.tar.gz',
            'sandbox-admin.tar.gz', '53_spendable_wallet_menu.sql'}
assert required.issubset(manifest['files'])
for name, digest in manifest['files'].items():
    assert Path(name).name == name and '\\' not in name
    assert hashlib.sha256((release / name).read_bytes()).hexdigest() == digest, name
root = Path('/opt/mall-demo')
services = ['mall-admin', 'mall-portal', 'mall-admin-sandbox', 'mall-portal-sandbox']
assert all(subprocess.run(['systemctl', 'is-active', '--quiet', name]).returncode == 0 for name in services)
backup = root / 'backups' / ('wallet-auth-' + time.strftime('%Y%m%d-%H%M%S'))
backup.mkdir(mode=0o700)
targets = {root / 'mall-admin.jar': release / 'admin.jar',
           root / 'mall-admin-sandbox.jar': release / 'admin.jar',
           root / 'mall-portal.jar': release / 'portal.jar',
           root / 'mall-portal-sandbox.jar': release / 'portal.jar'}
for name in ['admin', 'storefront', 'sandbox', 'sandbox-admin']:
    destination = release / name
    destination.mkdir()
    with tarfile.open(release / (name + '.tar.gz')) as archive:
        archive.extractall(destination, filter='data')
    targets[root / name] = destination
snapshot = []
for index, target in enumerate(targets):
    saved = backup / str(index)
    entry = {'path': str(target), 'saved': str(saved), 'symlink': target.is_symlink(),
             'existed': target.exists() or target.is_symlink()}
    if target.is_symlink():
        entry['link'] = os.readlink(target)
    elif target.is_dir():
        shutil.copytree(target, saved)
    elif target.exists():
        shutil.copy2(target, saved)
    snapshot.append(entry)
for database in ['mall_demo', 'mall_demo_sandbox']:
    dump = backup / (database + '.sql')
    with dump.open('wb') as output:
        subprocess.run(['mysqldump', '--single-transaction', '--no-tablespaces', database], stdout=output, check=True)
    dump.chmod(0o600)
(backup / 'snapshot.json').write_text(json.dumps(snapshot, indent=2))
print('BACKUP', backup, flush=True)

def probe(port):
    try:
        with urllib.request.urlopen(f'http://127.0.0.1:{port}/home/announcements', timeout=3) as response:
            return response.status == 200 and json.load(response).get('code') == 200
    except Exception:
        return False

def switch(target, source):
    pending = target.with_name(target.name + '.wallet-auth-next')
    if pending.is_symlink():
        pending.unlink()
    pending.symlink_to(source, target_is_directory=source.is_dir())
    if target.is_dir() and not target.is_symlink():
        target.rename(backup / (target.name + '-previous-directory'))
    os.replace(pending, target)

try:
    # Stop all four processes before replacing jars, including the sandbox admin.
    subprocess.run(['systemctl', 'stop', *services], check=True)
    for database in ['mall_demo', 'mall_demo_sandbox']:
        with (release / '53_spendable_wallet_menu.sql').open('rb') as sql:
            subprocess.run(['mysql', database], stdin=sql, check=True, stdout=subprocess.DEVNULL)
    for target, source in targets.items():
        switch(target, source)
    subprocess.run(['nginx', '-t'], check=True)
    subprocess.run(['systemctl', 'start', *services], check=True)
    for _ in range(180):
        healthy = all(subprocess.run(['systemctl', 'is-active', '--quiet', name]).returncode == 0 for name in services)
        if healthy and probe(8085) and probe(8086):
            import socket
            try:
                for port in [8080, 8087]:
                    with socket.create_connection(('127.0.0.1', port), timeout=1):
                        pass
                break
            except OSError:
                pass
        time.sleep(1)
    else:
        raise RuntimeError('Service readiness failed')
    subprocess.run(['systemctl', 'reload', 'nginx'], check=True)
except Exception:
    subprocess.run(['systemctl', 'stop', *services])
    for entry in reversed(snapshot):
        target, saved = Path(entry['path']), Path(entry['saved'])
        if target.is_symlink():
            target.unlink()
        if entry['existed']:
            if entry['symlink']:
                target.symlink_to(entry['link'], target_is_directory=True)
            elif saved.is_dir():
                previous = backup / (target.name + '-previous-directory')
                target.symlink_to(previous if previous.exists() else saved, target_is_directory=True)
            else:
                shutil.copy2(saved, target)
    subprocess.run(['systemctl', 'start', *services], check=True)
    subprocess.run(['nginx', '-t'], check=True)
    subprocess.run(['systemctl', 'reload', 'nginx'], check=True)
    raise
result = {'sourceCommit': manifest['sourceCommit'], 'backup': str(backup), 'status': 'SERVICES_READY_ACCEPTANCE_PENDING'}
(release / 'deployment-result.json').write_text(json.dumps(result, indent=2))
print(json.dumps(result), flush=True)
