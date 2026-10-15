const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const vm = require('node:vm')
const ts = require('typescript')
const { parse } = require('@vue/compiler-sfc')

function page(initialStorage = {}) {
  const store = new Map(Object.entries(initialStorage)), calls = [], redirects = []
  let handler = async ({ url }) => ({ data: url.endsWith('/account') ? { balance: '100.10' } : [] })
  const source = parse(fs.readFileSync(require('node:path').join(__dirname, '../src/pages/member/recharge.vue'), 'utf8')).descriptor.scriptSetup.content
  const code = ts.transpileModule(source + '\nmodule.exports={refresh,create,pay,select,amount,channel,balance,orders,current,error,busy,loaded}', { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 } }).outputText
  const context = {
    module: { exports: {} }, exports: {},
    require: name => name === 'vue' ? require('vue') : name === '@dcloudio/uni-app' ? { onShow() {} } : name === '@/utils/http' ? { http: async options => { calls.push(options); return handler(options) } } : { getMemberInfoAPI: async () => ({ data: { id: 1 } }) },
    uni: { getStorageSync: key => store.get(key), setStorageSync: (key, value) => store.set(key, value), removeStorageSync: key => store.delete(key) },
    window: { location: { assign: url => redirects.push(url) } }, console,
  }
  vm.runInNewContext(code, context)
  return { ...context.module.exports, store, calls, redirects, handle: fn => { handler = fn } }
}
test('reload restores an uncertain request so its original amount and channel can be retried', async () => {
  const saved = { amount: '25.10', channel: 'ALIPAY', requestId: 'request-1234567890' }
  const p = page({ 'recharge-request-1': saved })
  await p.refresh()
  assert.equal(p.amount.value, '25.10')
  assert.equal(p.channel.value, 'ALIPAY')
  assert.equal(p.store.get('recharge-request-1').requestId, saved.requestId)
})
test('invalid or fractional-cent amounts never submit an order', async () => {
  const p = page(); await p.refresh()
  for (const amount of ['0', '0.001', '-1', '50000.01', 'NaN', '1e2']) {
    p.amount.value = amount; await p.create()
  }
  assert.equal(p.calls.filter(c => c.method === 'POST').length, 0)
})
test('network uncertainty reuses request id and equivalent decimal spelling', async () => {
  const p = page(); await p.refresh()
  const requests = []
  p.handle(async options => { requests.push(options.data); throw new Error('timeout') })
  p.amount.value = '100.10'; await p.create()
  p.amount.value = '100.1'; await p.create()
  assert.equal(requests.length, 2)
  assert.equal(requests[0].requestId, requests[1].requestId)
})
test('double click creates one order and payment uses the same server order', async () => {
  const p = page(); await p.refresh()
  let release
  p.handle(options => options.url.endsWith('/orders') ? new Promise(resolve => { release = resolve }) : Promise.resolve({ data: { type: 'WECHAT_H5_URL', h5Url: 'https://pay.example/order1' } }))
  const first = p.create(); await p.create()
  release({ data: { orderNo: 'RC1', amount: '100.00', channel: 'WECHAT', status: 'PENDING' } }); await first
  assert.equal(p.calls.filter(c => c.method === 'POST' && c.url.endsWith('/orders')).length, 1)
  assert.equal(p.calls.filter(c => c.url.endsWith('/orders/RC1/pay')).length, 1)
  assert.equal(p.redirects[0], 'https://pay.example/order1')
  assert.equal(p.current.value.status, 'PENDING')
})
test('unavailable channel never displays paid or redirects', async () => {
  const p = page(); await p.refresh()
  p.handle(async () => { throw { data: { code: 56002 } } })
  await p.create()
  assert.equal(p.current.value, null)
  assert.equal(p.redirects.length, 0)
  assert.equal(p.store.has('recharge-request-1'), false)
})
