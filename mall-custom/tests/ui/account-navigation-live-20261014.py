from pathlib import Path
import urllib.request,hashlib,re,json
root=Path(r'C:/Users/SK/.codex/worktrees/mall-gap-repair/平台测试/mall-custom/mall-app-web/dist/build/h5');base='https://122.51.105.107/';op=urllib.request.build_opener(urllib.request.ProxyHandler({}));passed=0
with op.open(base,timeout=15) as r:body=r.read();assert body== (root/'index.html').read_bytes();passed+=1;print('PASS LIVE_INDEX_MATCHES_BUILT_RELEASE')
paths=re.findall(r'(?:src|href)="(\./assets/[^\"]+)"',body.decode())+['static/main-nav/'+name+'.svg' for name in ['home','grid','repeat','cart','network','user']]+['static/travel-photo/alpine-lake.jpg']
for path in paths:
 path=path.removeprefix('./')
 with op.open(base+path,timeout=15) as r:data=r.read();assert r.status==200 and hashlib.sha256(data).digest()==hashlib.sha256((root/path).read_bytes()).digest();passed+=1;print('PASS LIVE_RESOURCE_SHA256',path)
print('RESULT',passed,'PASS 0 FAIL')
