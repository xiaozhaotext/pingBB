const {test}=require('node:test'),assert=require('node:assert/strict')
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),ts=require('typescript')
const {parse}=require('@vue/compiler-sfc')
function load(file,names='',deps={}) {
  const raw=fs.readFileSync(path.join(__dirname,'../src',file),'utf8')
  const source=file.endsWith('.vue')?parse(raw).descriptor.scriptSetup.content:raw
  const storage=new Map(),events=[],modals=[]
  const context={exports:{},module:{exports:{}},console,Date,Intl,
    window:{location:{origin:'https://example.test'}},
    require:n=>n==='vue'?require('vue'):n==='@dcloudio/uni-app'?{onShow:()=>{},onLoad:()=>{},onUnload:()=>{}}:n==='@/utils/sandbox'?{sandboxMode:true}:n==='./date'?load('utils/date.ts'):deps[n]||{},
    uni:{getStorageSync:k=>storage.get(k),setStorageSync:(k,v)=>storage.set(k,v),removeStorageSync:k=>storage.delete(k),showToast:e=>events.push(e),showModal:e=>modals.push(e)}}
  vm.runInNewContext(ts.transpileModule(source+(names?'\nmodule.exports={'+names+'}':''),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2020}}).outputText,context)
  return {...(names?context.module.exports:context.exports),storage,events,modals}
}
test('catalog OSS URLs use the origin proxy even under a sandbox route',()=>{
  const {productImageUrl:url}=load('utils/productImage.ts')
  for(const value of ['http://macro-oss.oss-cn-shenzhen.aliyuncs.com/a.jpg','https://macro-oss.oss-cn-shenzhen.aliyuncs.com/a.jpg','/oss/a.jpg','oss/a.jpg'])assert.equal(url(value),'https://example.test/oss/a.jpg')
  assert.equal(url(' https://other.test/a.jpg '),'https://other.test/a.jpg')
  assert.equal(url(null),'/static/temp/cate1.jpg')
})
test('ISO timestamps render in Shanghai time across midnight',()=>{
  const {chinaDateTime:fmt}=load('utils/chinaTime.ts')
  assert.equal(fmt('2026-10-16T15:24:30.000+00:00'),'2026-10-16 23:24')
  assert.equal(fmt('2026-10-16T18:24:30Z'),'2026-10-17 02:24')
  assert.equal(fmt('2026-10-17T00:00:00+08:00'),'2026-10-17 00:00')
  assert.equal(fmt(null),'—');assert.equal(fmt(''),'—');assert.equal(fmt('bad-date'),'—')
})
test('zero discounts never render negative zero while real deductions keep their sign',()=>{
  const {formatOrderDeduction:fmt}=load('utils/order-display.ts')
  assert.equal(fmt(0),'￥0.00');assert.equal(fmt(-0),'￥0.00');assert.equal(fmt('0.000'),'￥0.00')
  assert.equal(fmt(88),'-￥88.00');assert.equal(fmt('12.10'),'-￥12.10')
  assert.equal(fmt(null),'—');assert.equal(fmt('bad'),'—')
})
function invitation(api) {return load('pages/member/invitation.vue','bindCode,bindError,bindState,binding,inviter,handleBind,loadInviter',{'@/apis/member':api})}
test('invalid invitation preserves the input, exposes the server message and permits retry',async()=>{
  const p=invitation({bindInviterAPI:async()=>{throw {data:{message:'邀请码不存在'}}},getMyInviterAPI:async()=>({data:null})})
  p.bindState.value='unbound';p.bindCode.value='INVALID999';await p.handleBind()
  assert.equal(p.bindError.value,'邀请码不存在');assert.equal(p.bindCode.value,'INVALID999');assert.equal(p.bindState.value,'unbound');assert.equal(p.binding.value,false)
  assert.equal(p.modals.length,1);assert.equal(p.modals[0].content,'邀请码不存在');assert.equal(p.modals[0].showCancel,false)
})
test('invitation network failure stays visible and does not report success',async()=>{
  const p=invitation({bindInviterAPI:async()=>{throw Error('网络断开')},getMyInviterAPI:async()=>({data:null})})
  p.bindState.value='unbound';p.bindCode.value='ABCD2345';await p.handleBind()
  assert.equal(p.bindError.value,'网络断开');assert.equal(p.events.length,0);assert.equal(p.binding.value,false)
  assert.equal(p.modals[0].title,'绑定失败');assert.equal(p.modals[0].content,'网络断开')
})
test('successful binding normalizes the code, clears pending data and blocks rebinding',async()=>{
  let calls=0,submitted;const p=invitation({bindInviterAPI:async b=>{calls++;submitted=b;return {data:{inviterId:9}}}})
  p.bindState.value='unbound';p.bindCode.value=' abcd2345 ';p.storage.set('pendingInviteCode','ABCD2345')
  await p.handleBind();await p.handleBind();assert.equal(calls,1);assert.equal(submitted.code,'ABCD2345');assert.equal(p.bindState.value,'bound');assert.equal(p.storage.has('pendingInviteCode'),false)
})
test('double binding clicks submit once while pending',async()=>{
  let finish,calls=0;const p=invitation({bindInviterAPI:()=>{calls++;return new Promise(r=>{finish=r})}})
  p.bindState.value='unbound';p.bindCode.value='ABCD2345';const pending=p.handleBind();await p.handleBind();assert.equal(calls,1)
  finish({data:{inviterId:9}});await pending;assert.equal(p.binding.value,false)
})
test('blank invitation prompts without calling the binding API',async()=>{
  let calls=0;const p=invitation({bindInviterAPI:async()=>{calls++}});p.bindState.value='unbound';p.bindCode.value='  ';await p.handleBind()
  assert.equal(calls,0);assert.equal(p.bindError.value,'请输入邀请码')
})
test('phone input strips non-digits, limits length and updates submitted state',()=>{
  const p=load('pages/public/register.vue','formData,handlePhoneInput')
  assert.equal(p.handlePhoneInput({detail:{value:'138 0000-1041abc999'}}),'13800001041')
  assert.equal(p.formData.value.telephone,'13800001041')
  p.handlePhoneInput({detail:{value:''}});assert.equal(p.formData.value.telephone,'')
})
