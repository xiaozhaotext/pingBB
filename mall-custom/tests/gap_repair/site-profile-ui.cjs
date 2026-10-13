const http=require('http'),fs=require('fs'),path=require('path');
const {chromium}=require('C:/Users/SK/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const root='C:/Users/SK/.codex/worktrees/mall-gap-repair/平台测试/mall-custom';
const account=JSON.parse(fs.readFileSync('.tools/consultation-test-accounts.json','utf8'))[0];
const server=http.createServer((req,res)=>{
 const api=req.url.startsWith('/portal/')||req.url.startsWith('/admin-api/');
 if(api||req.url.startsWith('/oss/')){
  const remote=!api; const admin=req.url.startsWith('/admin-api/');
  const target=http.request({hostname:remote?'122.51.105.107':'127.0.0.1',port:remote?80:admin?18080:18085,path:remote?req.url:req.url.replace(admin?'/admin-api':'/portal',''),method:req.method,headers:{...req.headers,host:'127.0.0.1'}},r=>{res.writeHead(r.statusCode,r.headers);r.pipe(res)});
  target.on('error',()=>{res.writeHead(502);res.end()});req.pipe(target);return;
 }
 const admin=req.url.startsWith('/admin/');
 const relative=req.url.split('?')[0];const file=path.join(root,admin?'mall-admin-web/dist':'mall-app-web/dist/build/h5',admin?(relative.slice(7)||'index.html'):(relative==='/'?'index.html':relative));
 try{const body=fs.readFileSync(file);res.setHeader('Content-Type',({'.js':'application/javascript','.css':'text/css','.html':'text/html','.json':'application/json','.svg':'image/svg+xml','.png':'image/png','.jpg':'image/jpeg'}[path.extname(file)]||'application/octet-stream'));res.end(body)}catch{res.writeHead(404);res.end()}
});
(async()=>{
 await new Promise(r=>server.listen(18774,'127.0.0.1',r));const browser=await chromium.launch();
 const p=await browser.newPage({viewport:{width:1440,height:1000}});let count=0;
 p.on('response',async r=>{if(r.url().includes('/admin-api/admin/')){try{const x=await r.json();console.log('ADMIN_RESPONSE',new URL(r.url()).pathname,x.code,x.message)}catch{console.log('ADMIN_RESPONSE_NOT_JSON',r.status())}}});p.on('pageerror',e=>console.log('PAGE_ERROR',e.message));
 const check=(ok,label)=>{if(!ok)throw Error(label);count++;console.log('PASS',label)};
 await p.goto('http://127.0.0.1:18774/admin/',{waitUntil:'domcontentloaded'});
 await p.getByPlaceholder('请输入用户名').fill('admin');await p.getByPlaceholder('请输入密码').fill(account.password);await p.getByRole('button',{name:'登录',exact:true}).click();await p.getByRole('heading',{name:'经营概览'}).waitFor();
 await p.goto('http://127.0.0.1:18774/admin/#/p0/site-profile',{waitUntil:'domcontentloaded'});await p.getByRole('heading',{name:'公司信息与隐私政策'}).waitFor();
 await p.locator('.el-form-item').filter({hasText:'公司名称'}).locator('input').fill('浏览器验收测试公司');
 const saved=p.waitForResponse(r=>r.url().includes('/site-profile/company')&&r.request().method()==='POST');await p.getByRole('button',{name:'保存公司信息'}).click();check((await (await saved).json()).code===200,'ADMIN_UI_COMPANY_SAVE_REAL_API');
 await p.getByRole('tab',{name:'隐私政策'}).click();const title='浏览器验收测试政策';
 await p.locator('.el-form-item').filter({hasText:'政策标题'}).locator('input').fill(title);await p.locator('.el-form-item').filter({hasText:'正文'}).locator('textarea').fill('第一行\n<script>window.injected=true</script>\n第二行');
 check(await p.getByRole('button',{name:'发布已保存版本'}).isDisabled(),'UNSAVED_DRAFT_PUBLISH_DISABLED');
 const draft=p.waitForResponse(r=>r.url().includes('/privacy/draft')&&r.request().method()==='POST');await p.getByRole('button',{name:'保存草稿'}).click();check((await (await draft).json()).code===200,'ADMIN_UI_DRAFT_PERSISTED');
 await p.locator('.el-checkbox').filter({hasText:'我确认已保存当前内容'}).click();const published=p.waitForResponse(r=>r.url().includes('/privacy/publish')&&r.request().method()==='POST');await p.getByRole('button',{name:'发布已保存版本'}).click();check((await (await published).json()).code===200,'ADMIN_UI_REVIEWED_POLICY_PUBLISHED');
 check(!await p.evaluate(()=>document.documentElement.scrollWidth>innerWidth),'ADMIN_PROFILE_DESKTOP_NO_OVERFLOW');
 const mobile=await browser.newPage({viewport:{width:390,height:844}});await mobile.goto('http://127.0.0.1:18774/#/pages/support/privacy',{waitUntil:'domcontentloaded'});await mobile.getByText(title,{exact:true}).waitFor();check(true,'MOBILE_POLICY_PUBLIC_REAL_API');
 check((await mobile.locator('.content').innerText()).includes('<script>window.injected=true</script>'),'POLICY_HTML_RENDERED_AS_PLAIN_TEXT');check(await mobile.evaluate(()=>!window.injected),'POLICY_CONTENT_DOES_NOT_EXECUTE_SCRIPT');check(!await mobile.evaluate(()=>document.documentElement.scrollWidth>innerWidth),'MOBILE_POLICY_NO_HORIZONTAL_OVERFLOW');
 console.log('RESULT',count,'PASS 0 FAIL');await browser.close();server.close();
})().catch(e=>{console.error(e.message);server.close();process.exit(1)});