const {test}=require('node:test')
const assert=require('node:assert/strict')
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),ts=require('typescript')
function load({responseAppId='wx-mini'}={}) {
 const calls=[],sdk=[],module={exports:{}}
 const uni={login:options=>options.success({code:'fresh-wx-code'}),getAccountInfoSync:()=>({miniProgram:{appId:'wx-mini'}}),requestPayment:options=>{sdk.push(options);options.success({})}}
 const context={module,exports:module.exports,uni,Error,require:name=>({http:async options=>{calls.push(options);return options.url.includes('/login')?{data:{appId:responseAppId,paymentTicket:'short-lived-ticket'}}:{data:{type:'WECHAT_MINIPROGRAM',appId:'wx-mini',timeStamp:'123',nonceStr:'nonce',package:'prepay_id=provider-order',signType:'RSA',paySign:'server-signature',outTradeNo:'MP-alias'}}}})}
 const source=fs.readFileSync(path.join(__dirname,'../src/utils/wechatPay.ts'),'utf8')
 vm.runInNewContext(ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2020}}).outputText,context)
 return {...module.exports,calls,sdk,uni}
}
test('sends only login code and authenticated payment ticket, then invokes signed native payment',async()=>{
 const p=load();const payment=await p.prepareMiniProgramPayment('MALL-ORDER');await p.requestMiniProgramPayment(payment)
 assert.equal(p.calls[0].url,'/member/wechat-mini/login');assert.equal(p.calls[0].data.code,'fresh-wx-code');assert.equal(p.calls[1].data.orderSn,'MALL-ORDER');assert.equal(p.calls[1].data.paymentTicket,'short-lived-ticket');assert.equal(p.sdk.length,1);assert.equal(p.sdk[0].package,'prepay_id=provider-order');assert.equal(p.sdk[0].signType,'RSA');assert.equal(p.sdk[0].paySign,'server-signature');
 assert.ok(!JSON.stringify(p.calls).includes('openid'));assert.ok(!JSON.stringify(p.calls).includes('appSecret'))
})
test('a different mini-program AppID cannot proceed to prepay',async()=>{
 const p=load({responseAppId:'wx-other'});await assert.rejects(()=>p.prepareMiniProgramPayment('ORDER'),/AppID/);assert.equal(p.calls.length,1);assert.equal(p.sdk.length,0)
})
test('SDK cancellation rejects the payment promise rather than fabricating a successful result',async()=>{
 const p=load();p.uni.requestPayment=options=>options.fail({errMsg:'requestPayment:fail cancel'});const payment=await p.prepareMiniProgramPayment('ORDER');await assert.rejects(()=>p.requestMiniProgramPayment(payment),/cancel/)
})
