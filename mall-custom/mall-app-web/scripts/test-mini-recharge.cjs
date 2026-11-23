const {test}=require('node:test'), assert=require('node:assert/strict'), fs=require('node:fs'),vm=require('node:vm'),ts=require('typescript'),path=require('node:path')
const {parse}=require('@vue/compiler-sfc')
function platform(source, mini=true) {
 return source.replace(/\/\/ #ifdef H5\r?\n([\s\S]*?)\/\/ #endif/g,(_,body)=>mini?'':body)
 .replace(/\/\/ #ifdef MP-WEIXIN\r?\n([\s\S]*?)\/\/ #endif/g,(_,body)=>mini?body:'')
}
function run(source,mocks,uni,extra='') {
 const mod={exports:{}}
 const code=ts.transpileModule(platform(source)+extra,{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2020}}).outputText
 vm.runInNewContext(code,{module:mod,exports:mod.exports,require:name=>mocks[name],uni,console,setTimeout,encodeURIComponent})
 return mod.exports
}
const root=path.join(__dirname,'../src')
const signed={type:'WECHAT_MINIPROGRAM',appId:'wx734dae9c5f5285b6',timeStamp:'1795400000',nonceStr:'nonce',package:'prepay_id=verified',signType:'RSA',paySign:'server-signature',outTradeNo:'RC1'}
test('native recharge exchanges login code and invokes wx payment with backend signature',async()=>{
 const calls=[]
 const api=run(fs.readFileSync(path.join(root,'utils/wechatPay.ts'),'utf8'),{'@/utils/http':{http:async o=>{calls.push(o);return {data:o.url.endsWith('/login')?{appId:signed.appId,paymentTicket:'ticket'}:signed}}}},{login:o=>o.success({code:'one-time-code'}),getAccountInfoSync:()=>({miniProgram:{appId:signed.appId}}),requestPayment:o=>{calls.push(o);o.success()}})
 await api.requestMiniProgramPayment(await api.prepareMiniProgramRechargePayment('RC1'))
 assert.equal(calls[0].url,'/member/wechat-mini/login');assert.equal(calls[1].url,'/member/recharge/orders/RC1/mini-pay')
 assert.equal(calls[1].data.paymentTicket,'ticket');assert.equal(calls[2].package,signed.package);assert.equal(calls[2].paySign,signed.paySign)
})
function recharge(cancel=false){
 const calls=[],storage=new Map();let paid=false
 const http=async o=>{calls.push(o);const u=o.url
  if(u.endsWith('/mini-payment-options'))return {data:{sandbox:false,channels:['WECHAT']}}
  if(u.endsWith('/account'))return {data:{balance:paid?'12.35':'0.00'}}
  if(u.endsWith('/mini-orders'))return {data:{orderNo:'RC1',amount:'12.35',channel:'WECHAT',status:'PENDING'}}
  if(u.endsWith('/mini-status')){paid=true;return {data:{orderNo:'RC1',amount:'12.35',channel:'WECHAT',status:'PAID'}}}
  if(u.endsWith('/orders'))return {data:[]}
  if(u.endsWith('/orders/RC1'))return {data:{orderNo:'RC1',amount:'12.35',channel:'WECHAT',status:paid?'PAID':'PENDING'}}
  if(u.endsWith('/ledger'))return {data:[]}
  return {data:{}}
 }
 const uni={getStorageSync:k=>storage.get(k),setStorageSync:(k,v)=>storage.set(k,v),removeStorageSync:k=>storage.delete(k),showModal:o=>calls.push(o)}
 const source=parse(fs.readFileSync(path.join(root,'pages/member/recharge.vue'),'utf8')).descriptor.scriptSetup.content
 const page=run(source,{vue:require('vue'),'@dcloudio/uni-app':{onShow(){}},'@/utils/chinaTime':{},'@/utils/http':{http},'@/apis/member':{getMemberInfoAPI:async()=>({data:{id:1}})},'@/utils/sandbox':{sandboxMode:false},'@/utils/wechatPay':{isWechatBrowser:()=>false,prepareMiniProgramRechargePayment:async no=>{calls.push({nativeOrder:no});return signed},requestMiniProgramPayment:async p=>{calls.push({nativePayment:p});if(cancel)throw Error('requestPayment:fail cancel')}}},uni,'\nmodule.exports={refresh,create,pay,amount,current,balance,error}')
 return {...page,calls,storage}
}
test('recharge page opens native payment and confirms balance with the backend',async()=>{
 const p=recharge();await p.refresh();p.amount.value='12.35';await p.create()
 assert(p.calls.some(o=>o.nativePayment));assert(p.calls.some(o=>o.url?.endsWith('/mini-orders')))
 assert(!p.calls.some(o=>o.url?.endsWith('/pay')));assert.equal(p.current.value.status,'PAID');assert.equal(p.balance.value,'12.35')
})
test('cancelled native checkout keeps pending order and does not credit the wallet',async()=>{
 const p=recharge(true);await p.refresh();p.amount.value='12.35';await p.create()
 assert.equal(p.current.value.status,'PENDING');assert.equal(p.balance.value,'0.00');assert.match(p.error.value,/cancel/)
 assert(!p.calls.some(o=>o.url?.endsWith('/mini-status')))
})
