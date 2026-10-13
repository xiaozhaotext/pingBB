import subprocess,os,json,hashlib,shutil,time,urllib.request,sys
from pathlib import Path
release=Path(sys.argv[1]).resolve()
assert release.parent==Path('/opt/mall-demo/releases'), 'Release must be a direct child of the demo releases directory'
manifest=json.loads((release/'manifest.json').read_text())
for name,sha in manifest['files'].items():
 assert hashlib.sha256((release/name).read_bytes()).hexdigest()==sha,name
print('PASS artifact SHA256 validation',flush=True)
backup=Path('/opt/mall-demo/backups/gap-'+time.strftime('%Y%m%d-%H%M%S'))
backup.mkdir(mode=0o700,parents=True,exist_ok=False)
for name in ['mall-admin.jar','mall-portal.jar','admin','storefront']:
 p=Path('/opt/mall-demo')/name
 if p.is_dir():shutil.copytree(p,backup/name,symlinks=True)
 else:shutil.copy2(p,backup/name)
shutil.copytree('/etc/mall-demo',backup/'environment',symlinks=True)
shutil.copytree('/etc/systemd/system/mall-portal.service.d',backup/'portal-systemd',symlinks=True)
shutil.copy2('/etc/nginx/snippets/mall-p0-public-blocks.conf',backup/'public-blocks.conf')
with (backup/'database.sql').open('wb') as f:
 subprocess.run(['mysqldump','--single-transaction','--no-tablespaces','mall_demo'],stdout=f,check=True)
os.chmod(backup/'database.sql',0o600)
print('PASS recoverable database, service and artifact backups',str(backup),flush=True)
for sql in sorted(release.glob('*.sql')):
 assert 'DROP TABLE' not in sql.read_text().upper(),sql.name
 with sql.open('rb') as f:subprocess.run(['mysql','mall_demo'],stdin=f,check=True)
 print('PASS additive migration',sql.name,flush=True)
# Invalidate only administrator permission caches, not user carts/order keys or the whole Redis DB.
prefix='mall:ums:resourceList:'
keys=subprocess.check_output(['redis-cli','--scan','--pattern',prefix+'*'],text=True).splitlines()
if keys:subprocess.run(['redis-cli','DEL']+keys,check=True,stdout=subprocess.DEVNULL)
for name in ['admin','storefront']:
 dest=release/(name+'-files');dest.mkdir(exist_ok=True)
 subprocess.run(['tar','-xzf',str(release/(name+'.tar.gz')),'-C',str(dest)],check=True)
# Keep original public blocks and add the newly introduced financial endpoints.
block=Path('/etc/nginx/snippets/mall-p0-public-blocks.conf');s=block.read_text()
for rule in ['location = /portal/member/dividend/distribute { return 403; }','location ^~ /portal/member/membership/ { return 403; }']:
 if rule not in s:s+='\n'+rule+'\n'
block.write_text(s)
# Command-line flags override any stale environment or older drop-in; all unapproved money writes stay off.
override=Path('/etc/systemd/system/mall-portal.service.d/override.conf')
override.write_text('[Service]\nExecStart=\nExecStart=/usr/bin/java -Xms128m -Xmx512m -jar /opt/mall-demo/mall-portal.jar --spring.profiles.active=dev --server.address=127.0.0.1 --mall.p0.consumer-simulation-enabled=false --mall.membership.payment.enabled=false --mall.payment.mock.enabled=false --mall.public-base-url=https://122.51.105.107\n')
subprocess.run(['nginx','-t'],check=True)
try:
 subprocess.run(['systemctl','stop','mall-admin','mall-portal'],check=True)
 for name in ['mall-admin','mall-portal']:shutil.copy2(release/(name+'.jar'),Path('/opt/mall-demo')/(name+'.jar'))
 for name in ['admin','storefront']:
  dest=Path('/opt/mall-demo')/name
  retained=backup/(name+'-previous-active')
  dest.rename(retained)
  dest.symlink_to(release/(name+'-files'),target_is_directory=True)
 subprocess.run(['systemctl','daemon-reload'],check=True)
 subprocess.run(['systemctl','restart','mall-admin','mall-portal'],check=True)
 credentials=json.loads((release/'private-test-credentials.json').read_text())
 for port in [8080,8085]:
  ready=False
  for _ in range(40):
   try:
    if port==8080:
     request=urllib.request.Request('http://127.0.0.1:8080/admin/login',data=json.dumps({'username':'admin','password':credentials['adminPassword']}).encode(),headers={'Content-Type':'application/json'})
    else:request=urllib.request.Request('http://127.0.0.1:8085/home/announcements')
    with urllib.request.urlopen(request,timeout=3) as response: data=json.load(response)
    if data.get('code')==200:ready=True;break
   except Exception:pass
   time.sleep(1)
  if not ready:raise RuntimeError('backend core API probe failed '+str(port))
 subprocess.run(['systemctl','reload','nginx'],check=True)
 print('PASS admin login and portal DB-backed API; static sites switched; money remains disabled',flush=True)
 print('BLOCKED full Actuator health: MongoDB is not installed; favorites/history not accepted',flush=True)
except Exception:
 subprocess.run(['systemctl','stop','mall-admin','mall-portal'],check=True)
 # Preserve failed artifacts and use the existing backup without touching customer data.
 for name in ['mall-admin','mall-portal']:shutil.copy2(backup/(name+'.jar'),Path('/opt/mall-demo')/(name+'.jar'))
 for name in ['admin','storefront']:
  p=Path('/opt/mall-demo')/name
  old=backup/(name+'-previous-active')
  if old.exists():
   if p.exists():p.rename(backup/(name+'-failed-link'))
   old.rename(p)
 shutil.copy2(backup/'public-blocks.conf',block)
 shutil.copy2(backup/'portal-systemd/override.conf',override)
 subprocess.run(['systemctl','daemon-reload'],check=True)
 subprocess.run(['systemctl','restart','mall-admin','mall-portal'],check=True)
 subprocess.run(['systemctl','reload','nginx'],check=True)
 raise
(release/'deployment-result.json').write_text(json.dumps({'backup':str(backup),'sourceCommit':manifest['builtFrom'],'status':'CORE_APIS_READY'},indent=2))
print('PASS DEPLOYMENT',flush=True)
