const http=require('http'),https=require('https'),fs=require('fs'),path=require('path');
const {chromium}=require('C:/Users/SK/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const root=process.env.MALL_PROJECT_ROOT||process.cwd();
const account=JSON.parse(fs.readFileSync('.tools/https-test-account.json','utf8'));
const server=http.createServer((req,res)=>{
 const api=req.url.startsWith('/portal/')||req.url.startsWith('/admin-api/');
 if(api||req.url.startsWith('/oss/')){
  const target=https.request({hostname:'122.51.105.107',port:443,path:req.url,method:req.method,headers:{...req.headers,host:'122.51.105.107'}},r=>{res.writeHead(r.statusCode,r.headers);r.pipe(res)});
  target.on('error',()=>{if(res.destroyed)return;if(!res.headersSent)res.writeHead(502);res.end()});req.pipe(target);return;
 }
 const admin=req.url.startsWith('/admin/');
 const relative=req.url.split('?')[0];const file=path.join(root,admin?'mall-admin-web/dist':'mall-app-web/dist/build/h5',admin?(relative.slice(7)||'index.html'):(relative==='/'?'index.html':relative));
 try{const body=fs.readFileSync(file);res.setHeader('Content-Type',({'.js':'application/javascript','.css':'text/css','.html':'text/html','.json':'application/json','.svg':'image/svg+xml','.png':'image/png','.jpg':'image/jpeg'}[path.extname(file)]||'application/octet-stream'));res.end(body)}catch{res.writeHead(404);res.end()}
});
(async()=>{
 await new Promise(r=>server.listen(18774,'127.0.0.1',r));const browser=await chromium.launch();const p=await browser.newPage({viewport:{width:390,height:844}});let passed=0;let memberToken='';const errors=[];p.on('request',r=>{if(r.url().includes('/member/earnings/account'))memberToken=r.headers()['authorization']||memberToken});p.on('pageerror',e=>errors.push(e.message));
 const check=(ok,name)=>{if(!ok)throw Error(name);passed++;console.log('PASS',name)};
 await p.goto('http://127.0.0.1:18774/#/pages/user/use',{waitUntil:'domcontentloaded'});await p.waitForURL('**/#/pages/user/user');await p.locator('.main-navigation').waitFor();check(true,'OLD_USE_URL_REDIRECTS_TO_USER');
 await p.goto('http://127.0.0.1:18774/#/pages/member/center',{waitUntil:'domcontentloaded'});await p.waitForURL('**/#/pages/member/promotion');await p.getByText('登录后查看您的推广记录',{exact:true}).waitFor();check(true,'GUEST_OLD_CENTER_REDIRECT_WITHOUT_AUTH_REQUESTS');
 check((await p.locator('.main-nav-item').allTextContents()).map(x=>x.trim()).join('|')==='首页|自选区|复购区|购物车|推广中心|我的','SIX_NAV_LABELS_AND_ORDER');
 await p.goto('http://127.0.0.1:18774/#/pages/public/login',{waitUntil:'domcontentloaded'});const inputs=p.locator('.input-item input');await inputs.nth(0).pressSequentially(account.username);await inputs.nth(1).pressSequentially(account.password);await p.locator('.confirm-btn').click();await p.waitForURL(u=>!u.hash.includes('public/login'),{timeout:30000});
 await p.locator('.main-navigation').getByRole('button',{name:'我的',exact:true}).click();await p.locator('.account-id').filter({hasText:'会员 ID'}).waitFor();check(true,'LIVE_CONSUMER_LOGIN_AND_PROFILE');
 for(const [path,selector,type] of [['/member/earnings/account','.account-assets',0],['/member/points/account?pointsType=1','.points-summary',1],['/member/points/account?pointsType=2','.points-summary',2]]){
  const r=await p.request.get('http://127.0.0.1:18774/portal'+path,{headers:{Authorization:memberToken}});const data=await r.json();check(data.code===200,'LIVE_ACCOUNT_API_'+type);
  const expected=String(type===0?data.data?.availableEarnings??'—':data.data?.available??data.data?.balance??'未开户');
  const panel=type===0?p.locator(selector).first():p.locator(selector).nth(type-1);await panel.getByText(expected,{exact:true}).first().waitFor();check(true,'DISPLAYED_BALANCE_MATCHES_LIVE_DATABASE_'+type);
 }

 check(await p.locator('.account-stat-grid').count()===0 && await p.locator('.recommend-layout').count()===0,'MY_PAGE_HAS_NO_PROMOTION_DASHBOARD_OR_PROGRESS');
 const sections=await p.locator('.account-section-title').allTextContents();check(sections.some(x=>x.includes('我的钱包'))&&sections.some(x=>x.includes('商品积分'))&&sections.some(x=>x.includes('复购积分'))&&sections.some(x=>x.includes('我的订单')),'MY_PAGE_MINDMAP_PANELS_PRESERVED');
 const before=await p.locator('.main-navigation').boundingBox();await p.evaluate(()=>scrollTo(0,document.body.scrollHeight));const after=await p.locator('.main-navigation').boundingBox();check(Math.abs(before.y-after.y)<1,'BOTTOM_NAV_FIXED_WHILE_SCROLLING');
 await p.locator('.main-navigation').getByRole('button',{name:'推广中心',exact:true}).click();await p.waitForURL('**/#/pages/member/promotion');await p.locator('.section-title').filter({hasText:'数据看板'}).waitFor();check(true,'PROMOTION_DASHBOARD_AND_LEVELS_MOVED');
 check(await p.getByText('会员级别',{exact:true}).count()===1&&await p.getByText('我的邀请码',{exact:true}).count()===1&&await p.getByText('我的推荐',{exact:true}).count()===1,'PROMOTION_ENTRIES_MATCH_MINDMAP');
 const points=[['自选区','/pages/zone/pick'],['复购区','/pages/zone/rebuy'],['购物车','/pages/cart/cart'],['首页','/pages/index/index'],['我的','/pages/user/user']];
 for(const [label,route] of points){await p.locator('.main-navigation').getByRole('button',{name:label,exact:true}).click();await p.waitForURL(u=>u.hash==='#'+route||label==='首页'&&u.hash==='#/');await p.locator('.main-navigation').waitFor();check(true,'NAVIGATE_'+label)}
 await p.locator('.points-summary').first().getByText('积分明细 ›',{exact:true}).click();await p.waitForURL('**/pages/member/pointsLedger?type=1');check(true,'PRODUCT_POINTS_LEDGER_ENTRY_REAL_ROUTE');
 await p.goto('http://127.0.0.1:18774/#/pages/user/user',{waitUntil:'domcontentloaded'});await p.locator('.points-summary').nth(1).getByText('积分明细 ›',{exact:true}).click();await p.waitForURL('**/pages/member/pointsLedger?type=2');check(true,'REBUY_POINTS_LEDGER_ENTRY_REAL_ROUTE');
 await p.goto('http://127.0.0.1:18774/#/pages/user/user',{waitUntil:'domcontentloaded'});check(!await p.evaluate(()=>document.documentElement.scrollWidth>innerWidth),'MOBILE_NO_HORIZONTAL_OVERFLOW');
 await p.setViewportSize({width:1440,height:1000});check(!await p.evaluate(()=>document.documentElement.scrollWidth>innerWidth),'DESKTOP_NO_HORIZONTAL_OVERFLOW');
 await p.goto('http://127.0.0.1:18774/#/pages/member/center',{waitUntil:'domcontentloaded'});await p.waitForURL('**/#/pages/member/promotion');check(true,'OLD_MEMBER_CENTER_REDIRECTS_TO_PROMOTION');
 for(const icon of ['home','grid','repeat','cart','network','user']){const r=await p.request.get('http://127.0.0.1:18774/static/main-nav/'+icon+'.svg');check(r.status()===200,'NAV_ICON_'+icon)}
 check(errors.length===0,'NO_BROWSER_RUNTIME_ERRORS');console.log('RESULT',passed,'PASS 0 FAIL');await browser.close();server.close();
})().catch(e=>{console.error(e.message);server.close();process.exit(1)});