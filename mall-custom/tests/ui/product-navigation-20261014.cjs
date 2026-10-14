const http=require('http'),https=require('https'),fs=require('fs'),path=require('path');
const {chromium}=require('C:/Users/SK/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const root=process.env.MALL_PROJECT_ROOT||path.resolve(__dirname,'../..');

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
 await new Promise(r=>server.listen(18774,'127.0.0.1',r));const browser=await chromium.launch();const p=await browser.newPage({viewport:{width:390,height:844},hasTouch:true});let passed=0;p.on('pageerror',e=>console.log('PAGE_ERROR',e.message));p.on('response',async r=>{if(r.url().includes('/product/detail/')){try{console.log('DETAIL_CODE',(await r.json()).code)}catch{console.log('DETAIL_HTTP',r.status())}}});
 const check=(ok,name)=>{if(!ok)throw Error(name);passed++;console.log('PASS',name)};
 const detail='http://127.0.0.1:18774/#/pages/product/product?id=43&zone=PICK';
 await p.goto(detail,{waitUntil:'domcontentloaded'});await p.locator('.introduce-section .title').filter({hasText:'Vanward'}).waitFor({timeout:25000});
 check(await p.locator('.uni-page-head:visible').count()===0,'ONE_CUSTOM_PRODUCT_HEADER');check((await p.locator('.detail-tabs').innerText()).replace(/\s/g,'')==='商品评价详情','PRODUCT_REVIEW_DETAIL_SECTIONS');
 await p.goBack({waitUntil:'domcontentloaded'});await p.waitForURL('**/#/pages/zone/pick');check(true,'DIRECT_LINK_BROWSER_BACK_STAYS_IN_PICK_ZONE');
 check((await p.locator('.main-nav-item').allTextContents()).map(x=>x.trim()).join('|')==='首页|购物车|推广中心|我的','FOUR_PRIMARY_NAV_ENTRIES');
 check(await p.locator('.main-nav-item.selected').innerText()==='首页','ZONE_HIGHLIGHTS_HOME_PARENT');
 await p.goto(detail,{waitUntil:'domcontentloaded'});await p.locator('.detail-tabs').waitFor();await p.locator('.nav-back').click();await p.waitForURL('**/#/pages/zone/pick');check(true,'DIRECT_LINK_BACK_BUTTON_HAS_FALLBACK');
 await p.goto('http://127.0.0.1:18774/#/pages/zone/rebuy',{waitUntil:'domcontentloaded'});
 await p.goto('http://127.0.0.1:18774/#/pages/product/product?id=43&zone=REBUY',{waitUntil:'domcontentloaded'});await p.locator('.detail-tabs').waitFor();await p.locator('.nav-back').click();await p.waitForURL('**/#/pages/zone/rebuy');check(true,'REBUY_DIRECT_LINK_BACK_FALLBACK');
 await p.goto(detail,{waitUntil:'domcontentloaded'});await p.locator('.introduce-section .title').filter({hasText:'Vanward'}).waitFor();await p.getByText('购买类型',{exact:true}).click();await p.locator('.popup.spec.show').waitFor();await p.locator('.nav-back').click({force:true});await p.locator('.popup.spec.show').waitFor({state:'hidden'});check(p.url().includes('/pages/product/product'),'BACK_CLOSES_SPEC_BEFORE_LEAVING');
 await p.getByText('购买类型',{exact:true}).click();await p.locator('.popup.spec.show').waitFor();await p.goBack({waitUntil:'domcontentloaded'});await p.waitForURL(u=>u.hash.includes('/pages/product/product'));await p.locator('.popup.spec.show').waitFor({state:'hidden'});await p.waitForTimeout(1000);  check(await p.locator('.detail-tabs').count()===1,'BROWSER_BACK_CLOSES_OVERLAY_WITHOUT_EXIT');
 await p.locator('.carousel').evaluate(el=>{const touch=(x)=>new Touch({identifier:1,target:el,clientX:x,clientY:200});el.dispatchEvent(new TouchEvent('touchstart',{bubbles:true,touches:[touch(8)]}));el.dispatchEvent(new TouchEvent('touchend',{bubbles:true,changedTouches:[touch(160)]}))});await p.waitForTimeout(1000);  check(await p.locator('.detail-tabs').count()===1,'IMAGE_SWIPE_DOES_NOT_TRIGGER_PAGE_BACK');
 await p.locator('.detail-tabs').getByText('评价',{exact:true}).click();await p.waitForFunction(()=>Math.abs(document.querySelector('.eva-section').getBoundingClientRect().top-80)<10);check(true,'REVIEW_TAB_SCROLLS_TO_REVIEW_SECTION');
 const edge=await browser.newPage({viewport:{width:390,height:844},hasTouch:true});await edge.goto(detail,{waitUntil:'domcontentloaded'});await edge.locator('.introduce-section .title').filter({hasText:'Vanward'}).waitFor();await edge.locator('.container').evaluate(el=>{const touch=(x,y)=>new Touch({identifier:1,target:el,clientX:x,clientY:y});el.dispatchEvent(new TouchEvent('touchstart',{bubbles:true,touches:[touch(5,700)]}));el.dispatchEvent(new TouchEvent('touchmove',{bubbles:true,cancelable:true,touches:[touch(120,710)]}));el.dispatchEvent(new TouchEvent('touchend',{bubbles:true,changedTouches:[touch(150,710)]}))});await edge.waitForURL('**/#/pages/zone/pick');check(true,'EDGE_SWIPE_RETURNS_WITHIN_SHOP');await edge.close();
 await p.goto('http://127.0.0.1:18774/#/',{waitUntil:'domcontentloaded'});await p.locator('.home-zones').waitFor();check(await p.locator('.home-zone.brand-zone').getByText('自选区',{exact:true}).count()===1&&await p.locator('.home-zone.rebuy-zone').getByText('复购区',{exact:true}).count()===1,'HOME_ZONE_ENTRIES_RETAINED');
 check(!await p.evaluate(()=>document.documentElement.scrollWidth>innerWidth),'MOBILE_LAYOUT_NO_OVERFLOW');
 console.log('RESULT',passed,'PASS 0 FAIL');await browser.close();server.close();
})().catch(e=>{console.error(e.message);server.close();process.exit(1)});





