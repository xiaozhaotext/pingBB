const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs'), vm = require('node:vm'), ts = require('typescript')
const { parse } = require('@vue/compiler-sfc')
function page(storage = new Map(), sandboxMode = false, backendSandbox = sandboxMode) {
  let handler = async () => ({ data: {} }), calls = []
  const source = parse(fs.readFileSync(require('node:path').join(__dirname, '../src/pages/member/earnings.vue'), 'utf8')).descriptor.scriptSetup.content
  const code = ts.transpileModule(source + '\nmodule.exports={loadData,handleWithdraw,withdrawAmount,withdrawing,modeReady,sandbox,spendableBalance}', { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 } }).outputText
  const context = { module: {exports: {}}, exports: {}, console, require: name => name === 'vue' ? require('vue') : name === '@dcloudio/uni-app' ? {onShow(){}} : name === '@/utils/sandbox' ? {sandboxMode} : {getWithdrawalOptionsAPI: async()=>({data:{sandbox:backendSandbox}}),getRechargeAccountAPI: async()=>({data:{balance:'99.40'}}),getMemberInfoAPI: async () => ({data:{id:1}}), getEarningsAccountAPI: async () => ({data:{}}), getWithdrawListAPI: async () => ({data:[]}), getEarningsLedgerAPI: async () => ({data:[]}), applyWithdrawAPI: async request => { calls.push(request); return handler(request) } }, uni: {showToast() {}, getStorageSync: k => storage.get(k), setStorageSync: (k,v) => storage.set(k,v), removeStorageSync: k => storage.delete(k)} }
  vm.runInNewContext(code, context)
  context.module.exports.modeReady.value = true // baseline fixture represents a successfully loaded production page
  return {...context.module.exports, storage, calls, handle: h => handler = h}
}
test('fractional cents and invalid amounts never submit', async () => {
 const p = page(); for(const a of ['0','-1','1.001','NaN','1e2']) {p.withdrawAmount.value=a;await p.handleWithdraw()} assert.equal(p.calls.length,0)
})

test('sandbox withdrawal reloads spendable balance and uses isolated pending-request storage',async()=>{
 const p=page(new Map(),true);await p.loadData();assert.equal(p.sandbox.value,true);assert.equal(p.spendableBalance.value,'99.40');
 p.withdrawAmount.value='100';p.handle(async()=>{throw Error('timeout')});await p.handleWithdraw();assert.equal(p.storage.has('sandbox-withdraw-request-1'),true);
 const reload=page(p.storage,true);await reload.loadData();assert.equal(reload.withdrawAmount.value,'100');await reload.handleWithdraw();assert.equal(reload.calls[0].idempotencyKey,p.calls[0].idempotencyKey);assert.equal(p.storage.size,0);
})
test('production page cannot initiate withdrawal against a sandbox backend',async()=>{
 const p=page(new Map(),false,true);await p.loadData();assert.equal(p.modeReady.value,false);p.withdrawAmount.value='10';await p.handleWithdraw();assert.equal(p.calls.length,0);
})
test('timeout and reload reuse the original request instead of freezing twice', async () => {
 const p=page();p.handle(async()=>{throw new Error('timeout')});p.withdrawAmount.value='10.00';await p.handleWithdraw()
 const key=p.calls[0].idempotencyKey
 const reload=page(p.storage);await reload.loadData();assert.equal(reload.withdrawAmount.value,'10.00')
 await reload.handleWithdraw();assert.equal(reload.calls[0].idempotencyKey,key);assert.equal(p.storage.size,0)
})
test('server failure retains the key; definitive validation error releases it', async()=>{
 const p=page();p.withdrawAmount.value='10';p.handle(async()=>{throw {data:{code:500}}});await p.handleWithdraw();assert.equal(p.storage.size,1)
 p.handle(async()=>{throw {data:{code:404}}});await p.handleWithdraw();assert.equal(p.storage.size,0)
})
test('double click submits once while first request is unresolved', async()=>{
 const p=page();let release;p.withdrawAmount.value='10';p.handle(()=>new Promise(r=>release=r));const first=p.handleWithdraw();await p.handleWithdraw();await Promise.resolve();assert.equal(p.calls.length,1);release({data:{}});await first
})
