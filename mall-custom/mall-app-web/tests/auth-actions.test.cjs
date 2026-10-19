const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs'), path = require('node:path'), vm = require('node:vm')
const ts = require('typescript'), { parse } = require('@vue/compiler-sfc')

function page(file, names, deps = {}, pages = [{route:'pages/public/login'}]) {
  const raw = fs.readFileSync(path.join(__dirname,'../src/',file),'utf8')
  const source = file.endsWith('.vue') ? parse(raw).descriptor.scriptSetup.content : raw
  const storage = new Map(), events = [], timeouts = [], loads = [], unloads = []
  const context = { module:{exports:{}}, exports:{}, console, URLSearchParams, window:{location:{search:"",hash:""}},
    require: name => name==='vue' ? require('vue') : name==='@dcloudio/uni-app' ? {onLoad:fn=>loads.push(fn),onUnload:fn=>unloads.push(fn)} : name==='@/utils/sessionKeys' ? {usernameStorageKey:'sandbox-username',tokenStorageKey:'sandbox-token',passwordStorageKey:'sandbox-password',memberStoreId:'sandbox-member'} : name==='@/utils/sandbox' ? {sandboxMode:true} : deps[name] || {},
    getCurrentPages:()=>pages,
    setTimeout:fn=>timeouts.push(fn),setInterval:fn=>({fn}),clearInterval:timer=>{timer.cleared=true},
    uni:{getStorageSync:key=>storage.get(key),setStorageSync:(key,value)=>storage.set(key,value),removeStorageSync:key=>storage.delete(key),
      showToast:value=>events.push({type:'toast',...value}),navigateBack:value=>events.push({type:'back',...value}),
      switchTab:value=>events.push({type:'home',...value}),redirectTo:value=>events.push({type:'redirect',...value}),
      reLaunch:value=>events.push({type:'relaunch',...value}),showModal:value=>value.success({confirm:true}),navigateTo:value=>events.push({type:'navigate',...value})},
  }
  vm.runInNewContext(ts.transpileModule(source+'\nmodule.exports={'+names+'}',{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2020}}).outputText,context)
  return {...context.module.exports,storage,events,timeouts,loads,unloads}
}
function login(memberLogin,pages) {
  const p=page('pages/public/login.vue','username,password,logining,navigating,loginError,toLogin',{'@/stores/member':{useMemberStore:()=>({memberLogin})}},pages)
  p.username.value=' test-user ';p.password.value='TestPassword1';return p
}
function register(api={}) {
  return page('pages/public/register.vue','formData,handleGetAuthCode,handleRegister,sendingCode,registering,demoCode,registrationError,countdown',{'@/apis/member':api})
}
test('login failure stays visible and releases the button for retry',async()=>{
  const p=login(async()=>{throw {data:{message:'用户名或密码错误'}}});await p.toLogin()
  assert.equal(p.loginError.value,'用户名或密码错误');assert.equal(p.logining.value,false);assert.equal(p.timeouts.length,0)
})
test('rapid login clicks submit once and show pending state',async()=>{
  let release,count=0;const p=login(()=>{count++;return new Promise(r=>{release=r})})
  const first=p.toLogin();await p.toLogin();assert.equal(count,1);assert.equal(p.logining.value,true)
  release();await first;assert.equal(p.navigating.value,true);await p.toLogin();assert.equal(count,1)
})
test('register then login returns home instead of the previous login page',async()=>{
  const p=login(async(name)=>assert.equal(name,'test-user'),[{route:'pages/public/login'},{route:'pages/public/login'}])
  await p.toLogin();p.timeouts[0]();assert.equal(p.events.at(-1).type,'home');assert.equal(p.events.at(-1).url,'/pages/index/index')
})
test('login from a protected page returns to that page with a home fallback',async()=>{
  const p=login(async()=>{},[{route:'pages/member/recharge'},{route:'pages/public/login'}])
  await p.toLogin();p.timeouts[0]();assert.equal(p.events.at(-1).type,'back');p.events.at(-1).fail();assert.equal(p.events.at(-1).type,'home')
})
test('code click reports validation errors without requesting the server',async()=>{
  let calls=0;const p=register({getAuthCodeAPI:async()=>{calls++}});await p.handleGetAuthCode()
  assert.equal(p.registrationError.value,'请输入手机号');p.formData.value.telephone='123';await p.handleGetAuthCode();assert.equal(calls,0)
})
test('code request is single-flight, displays returned code and starts countdown',async()=>{
  let release,calls=0;const p=register({getAuthCodeAPI:()=>{calls++;return new Promise(r=>{release=r})}})
  p.formData.value.telephone='13900001234';const first=p.handleGetAuthCode();await p.handleGetAuthCode()
  assert.equal(calls,1);assert.equal(p.sendingCode.value,true);release({data:'012345'});await first
  assert.equal(p.demoCode.value,'012345');assert.equal(p.countdown.value,60);assert.equal(p.sendingCode.value,false)
  await p.handleGetAuthCode();assert.equal(calls,1);p.unloads[0]()
})
test('code request failure is visible and retryable without a countdown',async()=>{
  const p=register({getAuthCodeAPI:async()=>{throw {data:{message:'短信服务暂不可用'}}}});p.formData.value.telephone='13900001234';await p.handleGetAuthCode()
  assert.equal(p.registrationError.value,'短信服务暂不可用');assert.equal(p.sendingCode.value,false);assert.equal(p.countdown.value,0)
})
test('registration retry preserves entered data and duplicate clicks submit once',async()=>{
  let release,calls=0;const p=register({registerAPI:()=>{calls++;return new Promise((r,j)=>{release=j})}})
  Object.assign(p.formData.value,{username:' test-user ',password:'TestPassword1',telephone:'13900001234',authCode:'012345'})
  const first=p.handleRegister();await p.handleRegister();assert.equal(calls,1)
  release({data:{message:'验证码错误'}});await first;assert.equal(p.registrationError.value,'验证码错误');assert.equal(p.registering.value,false);assert.equal(p.formData.value.username,'test-user')
})
test('successful registration stays locked until navigation finishes',async()=>{
  let calls=0;const p=register({registerAPI:async()=>{calls++}})
  Object.assign(p.formData.value,{username:'test-user',password:'TestPassword1',telephone:'13900001234',authCode:'012345'})
  await p.handleRegister();await p.handleRegister();assert.equal(calls,1);assert.equal(p.registering.value,true)
  p.timeouts[0]();assert.equal(p.events.at(-1).url,'/pages/public/login');p.events.at(-1).complete();assert.equal(p.registering.value,false)
})

test('invitation code appears from a link and is submitted with registration',async()=>{
  let submitted;const p=register({registerAPI:async data=>{submitted=data}})
  p.loads[0]({inviteCode:' abcd2345 '})
  assert.equal(p.formData.value.inviteCode,'ABCD2345')
  Object.assign(p.formData.value,{username:'test-user',password:'TestPassword1',telephone:'13900001234',authCode:'012345'})
  await p.handleRegister()
  assert.equal(submitted.inviteCode,'ABCD2345')
})
test('profile failure after successful login removes the partial sandbox session',async()=>{
  let store;const p=page('stores/member.ts','useMemberStore',{
    pinia:{defineStore:(_id,setup)=>()=>store ||= setup()},
    '@/apis/member':{loginAPI:async()=>({data:{tokenHead:'Bearer ',token:'test-only'}}),getMemberInfoAPI:async()=>{throw new Error('资料服务不可用')}},
  });store=p.useMemberStore();await assert.rejects(store.memberLogin('test-user','TestPassword1'),/资料服务不可用/)
  assert.equal(p.storage.has('sandbox-token'),false);assert.equal(store.hasLogin.value,false)
})
test('logout clears the session and relaunches login after confirmation',()=>{
  let loggedOut=0;const p=page('pages/set/set.vue','toLogout',{'@/stores/member':{useMemberStore:()=>({memberLogout:()=>loggedOut++})},'@/utils/theme':{getTheme:()=> 'forest',getThemePrimary:()=> '#285b40',THEMES:[]}})
  p.toLogout();p.timeouts[0]();assert.equal(loggedOut,1);assert.equal(p.events.at(-1).url,'/pages/public/login')
})
