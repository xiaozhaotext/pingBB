const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const ts = require('typescript')

async function page({ sandbox = true, serverSandbox = true, enabled = true, orderError, create,
  wechatBrowser = false, payment = { mock: 'true' }, payModeCapture } = {}) {
  let mounted, confirmed = 0, created = 0, authorized = 0, bridgeCalls = 0
  const storage = new Map(), redirects = []
  const options = { sandbox: serverSandbox, purchaseEnabled: enabled, wechatReady: !serverSandbox,
    wechatJsapiReady: !serverSandbox, alipayReady: false }
  const api = {
    getMembershipPaymentOptionsAPI: async () => ({ data: options }),
    getMemberInfoAPI: async () => ({ data: { id: 11 } }),
    createMembershipOrderAPI: async params => { created++; if (payModeCapture) payModeCapture(params.payMode); if (orderError) throw orderError; if (create) await create(); return { data: { orderId: 10, merchantOrderNo: 'MP10', amount: 3999, payment } } },
    confirmMockMembershipPaymentAPI: async () => { confirmed++ },
  }
  const file = path.join(__dirname, '../src/components/MembershipV2Benefits.vue')
  let source = fs.readFileSync(file, 'utf8').split('<script setup lang="ts">')[1].split('</script>')[0]
  source = source.replace("import.meta.env.VITE_SANDBOX_MODE === 'true'", String(sandbox))
  const context = { exports: {}, setTimeout: fn => fn(), uni: { getStorageSync: k => storage.get(k), setStorageSync: (k, v) => storage.set(k, v), removeStorageSync: k => storage.delete(k), showToast() {}, switchTab: x => redirects.push(x.url) },
    require(name) {
      if (name === 'vue') return { ref: value => ({ value }), onMounted: fn => { mounted = fn } }
      if (name === '@/apis/triPools') return { getTriPoolsAPI: async () => ({ data: { tier: 'VISITOR' } }), tierNames: {} }
      if (name === '@/stores/member') return { useMemberStore: () => ({ memberInfo: { id: 11 } }) }
      if (name === '@/apis/member') return api
      if (name === '@/utils/wechatPay') return {
        isWechatBrowser: () => wechatBrowser,
        ensureWechatOauth: async () => { authorized++; return true },
        invokeWechatJsapi: async () => { bridgeCalls++ },
      }
      throw new Error(name)
    } }
  vm.runInNewContext(ts.transpileModule(source + '\nexports.subject = { handleBuy, actionError, submitting };', { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText, context)
  await mounted()
  return { ...context.exports.subject, storage, redirects,
    counts: () => ({ created, confirmed }), jsapiCounts: () => ({ authorized, bridgeCalls }) }
}
test('sandbox button runs order then verified mock confirmation and clears pending key', async () => {
  const p = await page(); await p.handleBuy()
  assert.deepEqual(p.counts(), { created: 1, confirmed: 1 })
  assert.equal(p.storage.size, 0)
  assert.deepEqual(p.redirects, ['/pages/user/user'])
})
test('production page never confirms a mock payment response', async () => {
  const p = await page({ sandbox: false, serverSandbox: false }); await p.handleBuy()
  assert.equal(p.counts().confirmed, 0); assert.match(p.actionError.value, /支付环境不匹配/)
})
test('production page cannot use mock backend and disabled purchase cannot create an order', async () => {
  for (const args of [{ sandbox: false, serverSandbox: true }, { enabled: false }]) { const p = await page(args); await p.handleBuy(); assert.equal(p.counts().created, 0) }
})
test('double click creates one order and confirms once', async () => {
  let release; const p = await page({ create: () => new Promise(resolve => { release = resolve }) })
  const first = p.handleBuy(); await Promise.resolve(); await p.handleBuy(); release(); await first
  assert.deepEqual(p.counts(), { created: 1, confirmed: 1 })
})
test('failed order preserves request id for retry and shows HTTP failure', async () => {
  const p = await page({ orderError: { statusCode: 403 } }); await p.handleBuy()
  const saved = [...p.storage.values()][0]; await p.handleBuy()
  assert.equal([...p.storage.values()][0], saved); assert.match(p.actionError.value, /HTTP 403/); assert.equal(p.counts().confirmed, 0)
})
test('sandbox with real WeChat payment obtains OAuth before JSAPI without mock confirmation', async () => {
  let mode
  const p = await page({ sandbox: true, serverSandbox: false, wechatBrowser: true,
    payment: { type: 'WECHAT_JSAPI', appId: 'wx-test' }, payModeCapture: value => { mode = value } })
  await p.handleBuy()
  assert.equal(mode, 'WECHAT_JSAPI')
  assert.deepEqual(p.jsapiCounts(), { authorized: 1, bridgeCalls: 1 })
  assert.deepEqual(p.counts(), { created: 1, confirmed: 0 })
})
