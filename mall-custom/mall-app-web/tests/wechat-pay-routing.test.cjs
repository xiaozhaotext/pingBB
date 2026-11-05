const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const ts = require('typescript')

function paymentPage(inWechat) {
  const file = path.join(__dirname, '../src/pages/money/pay.vue')
  let source = fs.readFileSync(file, 'utf8').split('<script setup lang="ts">')[1].split('</script>')[0]
  source = source.replaceAll("import.meta.env.VITE_USE_ALIPAY === 'true'", 'false')
    .replaceAll("import.meta.env.VITE_USE_WECHAT_PAY === 'true'", 'true')
    .replaceAll("import.meta.env.VITE_USE_MOCK_PAYMENT === 'true'", 'false')
    .replaceAll('import.meta.env.VITE_API_BASE_URL', "'/sandbox/portal'")
  const calls = { h5: 0, jsapi: 0, oauth: 0, bridge: 0 }
  const window = { location: { href: 'https://example.com/sandbox/' } }
  const storage = new Map()
  const utilModule = { exports: {} }
  vm.runInNewContext(ts.transpileModule(fs.readFileSync(path.join(__dirname, '../src/utils/paymentResult.ts'), 'utf8'), { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText, { module: utilModule, exports: utilModule.exports, URL })
  const context = { exports: {}, window, URLSearchParams, URL,
    uni: { showToast() {}, redirectTo() {}, getStorageSync:k=>storage.get(k), setStorageSync:(k,v)=>storage.set(k,v), removeStorageSync:k=>storage.delete(k) },
    require(name) {
      if (name === 'vue') return { ref: value => ({ value }), onMounted() {}, onUnmounted() {} }
      if (name === '@dcloudio/uni-app') return { onLoad() {}, onShow() {} }
      if (name === '@/utils/paymentResult') return utilModule.exports
      if (name === '@/utils/sessionKeys') return { tokenStorageKey:'sandbox-token' }
      if (name === '@/apis/order') return {
        getOrderDetailAPI: async () => ({ data: {} }), confirmMockPaymentAPI: async () => {},
        getWechatOrderPaymentStatusAPI: async () => ({data:{state:'CONFIRMED',canRetry:false}}),
        wechatOrderPayH5API: async () => { calls.h5++; return { data: { h5Url: 'https://pay.example/h5' } } },
        wechatOrderPayJsapiAPI: async () => { calls.jsapi++; return { data: { type: 'WECHAT_JSAPI' } } },
      }
      if (name === '@/utils/wechatPay') return {
        isWechatBrowser: () => inWechat,
        ensureWechatOauth: async () => { calls.oauth++; return true },
        invokeWechatJsapi: async () => { calls.bridge++ },
      }
      throw new Error(name)
    },
  }
  const compiled = ts.transpileModule(source + '\nexports.page = { handleConfirmPay, payType, orderId, orderInfo, paymentState };',
    { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText
  vm.runInNewContext(compiled, context)
  context.exports.page.payType.value = 2
  context.exports.page.orderId.value = 7
  context.exports.page.orderInfo.value = { orderSn: 'TEST-7' }
  context.exports.page.paymentState.value = 'ready'
  return { ...context.exports.page, calls, window }
}

test('outside WeChat the existing H5 payment path remains in use', async () => {
  const page = paymentPage(false)
  await page.handleConfirmPay()
  assert.equal(new URL(page.window.location.href).origin, 'https://pay.example')
  assert.equal(new URL(page.window.location.href).searchParams.get('redirect_url'), 'https://example.com/sandbox/#/pages/money/pay?orderId=7&checkPayment=1')
  assert.deepEqual(page.calls, { h5: 1, jsapi: 0, oauth: 0, bridge: 0 })
})

test('inside WeChat the page authorizes then invokes JSAPI', async () => {
  const page = paymentPage(true)
  await page.handleConfirmPay()
  assert.deepEqual(page.calls, { h5: 0, jsapi: 1, oauth: 1, bridge: 1 })
})
