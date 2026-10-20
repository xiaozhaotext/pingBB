"""Deploy a verified main release, retaining host configuration and recoverable backups.

Run on the existing host: python3 deploy.py /opt/mall-demo/releases/<release>.
Manifest contains sourceCommit, expectedTargets and SHA256 files. No credentials in artifacts.
"""
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tarfile
import time
import urllib.error
import urllib.parse
import urllib.request

root = Path('/opt/mall-demo')
release = Path(sys.argv[1]).resolve()
assert release.parent == root / 'releases'
lock = (root / '.repeat-membership-deploy.lock').open('w')
fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
manifest = json.loads((release / 'manifest.json').read_text())
for name, digest in manifest['files'].items():
    assert Path(name).name == name and '\\' not in name
    assert hashlib.sha256((release / name).read_bytes()).hexdigest() == digest, name
expected = manifest['expectedTargets']
services = ['mall-admin', 'mall-portal', 'mall-admin-sandbox', 'mall-portal-sandbox']
for service in services:
    subprocess.run(['systemctl', 'is-active', '--quiet', service], check=True)
for name, target in expected.items():
    assert (root / name).is_symlink(), 'Expected an existing release link: ' + name
    assert str((root / name).resolve()) == target, 'Concurrent deployment: ' + name

# Build outside active releases; failures here leave running services untouched.
with tarfile.open(release / 'backend-source.tar.gz') as archive:
    archive.extractall(release, filter='data')
source = release / 'source/mall-custom/mall'
environment = dict(os.environ, MAVEN_OPTS='-Xmx512m')
with (release / 'backend-build.log').open('w') as log:
    subprocess.run(['mvn', '-B', '-f', str(source / 'pom.xml'), '-pl', 'mall-admin,mall-portal',
                    '-am', 'package', '-DskipTests=true', '-Ddocker.skip=true'], env=environment, stdout=log,
                   stderr=subprocess.STDOUT, check=True)
for module, artifact in [('mall-admin', 'admin'), ('mall-portal', 'portal')]:
    shutil.copy2(source / module / 'target' / (module + '-1.0-SNAPSHOT.jar'), release / (artifact + '.jar'))
for name in ['storefront', 'sandbox', 'admin', 'sandbox-admin']:
    destination = release / name
    destination.mkdir()
    with tarfile.open(release / (name + '.tar.gz')) as archive:
        archive.extractall(destination, filter='data')
    assert (destination / 'index.html').is_file()
    # Retain hashed lazy-load assets for clients that already have the old page open.
    assets = root / name / 'assets'
    if assets.is_dir():
        for old in assets.iterdir():
            target = destination / 'assets' / old.name
            if old.is_file() and not target.exists():
                target.parent.mkdir(exist_ok=True)
                shutil.copy2(old, target)
print('ARTIFACTS_READY', manifest['sourceCommit'], flush=True)

backup = root / 'backups' / ('repeat-membership-' + time.strftime('%Y%m%d-%H%M%S'))
backup.mkdir(mode=0o700)
(backup / 'links.json').write_text(json.dumps(expected, indent=2))
shutil.copytree('/etc/mall-demo', backup / 'environment', symlinks=True)

def sql(database, statement):
    return subprocess.check_output(['mysql', '-N', '-B', database, '-e', statement], text=True).strip()

counts = {}
for database in ['mall_demo', 'mall_demo_sandbox']:
    counts[database] = sql(database, 'SELECT COUNT(*) FROM tri_payment_event')
    with (backup / (database + '.sql')).open('wb') as output:
        subprocess.run(['mysqldump', '--single-transaction', '--routines', '--triggers', database],
                       stdout=output, check=True)
    (backup / (database + '.sql')).chmod(0o600)
print('BACKUP_READY', str(backup), flush=True)

def switch(name, target):
    temporary = root / (name + '.repeat-next')
    assert not temporary.exists() and not temporary.is_symlink(), str(temporary)
    temporary.symlink_to(target, target_is_directory=Path(target).is_dir())
    os.replace(temporary, root / name)

def call(path):
    try:
        with urllib.request.urlopen('https://zhiyoumei.com.cn' + path, timeout=5) as response:
            return response.status, response.read()
    except urllib.error.HTTPError as error:
        return error.code, error.read()

def ready():
    try:
        for path in ['/portal/home/announcements', '/sandbox/portal/home/announcements']:
            status, body = call(path)
            if status != 200 or json.loads(body).get('code') != 200:
                return False
        for path in ['/admin-api/admin/info', '/sandbox/admin-api/admin/info']:
            status, body = call(path)
            if status != 401 and json.loads(body).get('code') != 401:
                return False
        return True
    except Exception:
        return False

checks = []
try:
    # Recheck targets immediately before entering the short service switch window.
    for name, target in expected.items():
        assert str((root / name).resolve()) == target, 'Concurrent deployment: ' + name
    subprocess.run(['systemctl', 'stop', *services], check=True)
    for database in ['mall_demo', 'mall_demo_sandbox']:
        with (release / '56_repeat_membership_purchase.sql').open('rb') as migration:
            subprocess.run(['mysql', database], stdin=migration, check=True, stdout=subprocess.DEVNULL)
        assert sql(database, 'SELECT COUNT(*) FROM tri_payment_event') == counts[database]
        unique = sql(database, "SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tri_payment_event' AND COLUMN_NAME='member_id' AND NON_UNIQUE=0")
        assert unique == '0', database + ': member uniqueness still present'
        primary = sql(database, "SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tri_payment_event' AND INDEX_NAME='PRIMARY' AND COLUMN_NAME='source_key'")
        assert primary == '1', database + ': payment idempotency primary key missing'
        checks.append(database + ': migration applied, financial rows retained, payment primary key intact')
    for name in expected:
        artifact = ('admin.jar' if 'admin' in name else 'portal.jar') if name.endswith('.jar') else name
        switch(name, release / artifact)
    subprocess.run(['nginx', '-t'], check=True)
    subprocess.run(['systemctl', 'start', *services], check=True)
    for _ in range(180):
        if ready():
            break
        time.sleep(1)
    else:
        raise RuntimeError('Service readiness timeout')
    subprocess.run(['systemctl', 'reload', 'nginx'], check=True)
    checks.append('Both portal DB-backed APIs and both admin authentication boundaries ready')
    for path, name in [('/', 'storefront'), ('/sandbox/', 'sandbox'), ('/admin/', 'admin'), ('/sandbox/admin/', 'sandbox-admin')]:
        status, body = call(path)
        assert status == 200 and body == (release / name / 'index.html').read_bytes(), path
        checks.append(path + ': exact release HTML')
        for asset in re.findall(r'(?:src|href)="([^\"]+\.(?:js|css))"', body.decode()):
            asset_url = urllib.parse.urljoin(path, asset)
            status, data = call(asset_url)
            assert status == 200 and data, asset_url
        checks.append(path + ': linked JS/CSS available')
    for service in services:
        subprocess.run(['systemctl', 'is-active', '--quiet', service], check=True)
    checks.append('All four services active; systemd/payment configuration retained')
except Exception:
    subprocess.run(['systemctl', 'stop', *services])
    for name, target in expected.items():
        if str((root / name).resolve()) != target:
            switch(name, target)
    subprocess.run(['systemctl', 'start', *services], check=True)
    subprocess.run(['nginx', '-t'], check=True)
    subprocess.run(['systemctl', 'reload', 'nginx'], check=True)
    # Keep the safe index migration; never restore financial data over new transactions.
    (release / 'deployment-result.json').write_text(json.dumps({'status': 'ROLLED_BACK', 'backup': str(backup)}))
    raise

result = {'sourceCommit': manifest['sourceCommit'], 'status': 'ACCEPTANCE_PASSED',
          'backup': str(backup), 'checks': checks,
          'jarSha256': {name: hashlib.sha256((release / (name + '.jar')).read_bytes()).hexdigest()
                        for name in ['admin', 'portal']}}
(release / 'deployment-result.json').write_text(json.dumps(result, indent=2))
print(json.dumps(result), flush=True)
