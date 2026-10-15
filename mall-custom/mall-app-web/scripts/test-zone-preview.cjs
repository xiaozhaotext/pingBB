const { test } = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs'), vm = require('node:vm')
const dep = require
const ts = dep('typescript'), { parse } = dep('@vue/compiler-sfc')
const filename = require('node:path').join(__dirname, '../src/pages/order/createOrder.vue')
function page() {
 const pending=[], submitted=[], redirects=[]
 const source=parse(fs.readFileSync(filename,'utf8')).descriptor.scriptSetup.content
 const code=ts.transpileModule(source+'\nmodule.exports={refreshSettlement,handleSubmit,useBalance,calcAmount,settlementPending,settlementReady,currentAddress,zoneSettlement}',{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2020}}).outputText
 const api={settlementPreviewAPI: request=>new Promise((resolve,reject)=>pending.push({request,resolve,reject})),generateOrderAPI:async p=>{submitted.push(p);return {data:{order:{id:1},paidImmediately:true}}}}
 const c={module:{exports:{}},exports:{},console,defineExpose(){},setTimeout:fn=>fn(),require:name=>name==='vue'?dep('vue'):name==='@dcloudio/uni-app'?{onLoad(){},onShow(){}}:name==='@/apis/order'?api:{},uni:{showToast(){},showModal(){},redirectTo:p=>redirects.push(p.url)}}
 vm.runInNewContext(code,c);return {...c.module.exports,pending,submitted,redirects}
}
function confirm(cash) {return {data:{memberReceiveAddressList:[{id:1,defaultStatus:1}],cartPromotionItemList:[],couponHistoryDetailList:[],calcAmount:{payAmount:cash},zoneSettlement:{plans:[{zoneCode:'PICK',payable:true}]},productPointsBalance:0,repurchasePointsBalance:0,rechargeBalance:100,rebuyPointsRatio:0.3}}}
test('an older preview cannot overwrite the newest selected method and amount', async()=>{
 const p=page();const first=p.refreshSettlement();p.useBalance.value=true;const second=p.refreshSettlement();
 p.pending[1].resolve(confirm(0));await second;p.pending[0].resolve(confirm(100));await first;
 assert.equal(p.calcAmount.value.payAmount,0);assert.equal(p.settlementReady.value,true);assert.equal(p.settlementPending.value,false)
})
test('pending or failed preview cannot submit a stale order',async()=>{
 const p=page();p.currentAddress.value={id:1};const req=p.refreshSettlement();await p.handleSubmit();assert.equal(p.submitted.length,0);
 p.pending[0].reject(new Error('network unavailable'));await req;await p.handleSubmit();assert.equal(p.submitted.length,0);assert.equal(p.settlementReady.value,false)
})
test('zero-cash completion navigates to paid orders instead of pending orders',async()=>{
 const p=page();const req=p.refreshSettlement();p.pending[0].resolve(confirm(0));await req;await p.handleSubmit();assert.equal(p.submitted.length,1);assert.equal(p.redirects[0],'/pages/order/order?state=1')
})
