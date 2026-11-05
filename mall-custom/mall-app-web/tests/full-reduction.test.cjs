const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const ts = require('typescript')
const vue = require('vue')
const { compile } = require('@vue/compiler-dom')
const { renderToString } = require('@vue/server-renderer')

const moduleValue = { exports: {} }
vm.runInNewContext(ts.transpileModule(fs.readFileSync(path.join(__dirname, '../src/utils/fullReduction.ts'), 'utf8'), {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
}).outputText, { module: moduleValue, exports: moduleValue.exports, encodeURIComponent })
const { fullReductionCandidates, fullReductionOffers, fullReductionUrl } = moduleValue.exports
const offer = { productId: 10, productName: '测试商品', zoneCode: 'REBUY', currentAmount: 80, threshold: 100, discount: 10, gap: 20 }
const detail = () => ({ product: { id: 10, price: 40, promotionType: 4 }, productFullReductionList: [{ fullPrice: 100, reducePrice: 10 }], skuStockList: [
  { id: 100, price: 40, stock: 20, lockStock: 0 },
  { id: 101, price: 10, stock: 20, lockStock: 0 },
] })
const items = [{ productId: 10, productSkuId: 100, price: 40, quantity: 2, zoneCode: 'REBUY' }]

test('same-product multi-SKU candidates reach the exact threshold without combining other products or zones', () => {
  const result = fullReductionCandidates(detail(), [...items,
    { productId: 11, productSkuId: 102, price: 999, quantity: 1, zoneCode: 'REBUY' },
    { productId: 10, productSkuId: 100, price: 40, quantity: 5, zoneCode: 'CONSUME' },
  ], 'REBUY', 100)
  assert.equal(result.current, 80)
  assert.equal(result.gap, 20)
  assert.deepEqual(Array.from(result.candidates, item => [item.sku.id, item.quantity, item.amount]), [[100, 1, 40], [101, 2, 20]])
})

test('cent thresholds do not round 99.99 up to 100 and candidate quantities use whole units', () => {
  const product = detail()
  product.skuStockList = [{ id: 100, price: 99.99, stock: 2, lockStock: 0 }, { id: 101, price: 0.01, stock: 2, lockStock: 0 }]
  const result = fullReductionCandidates(product, [{ ...items[0], quantity: 1, price: 99.99 }], 'REBUY', 100)
  assert.equal(result.gap, 0.01)
  assert.equal(result.candidates.find(item => item.sku.id === 101).quantity, 1)
})

test('locked inventory, zero prices and quantity limits exclude impossible recommendations', () => {
  const product = detail()
  product.skuStockList = [
    { id: 100, price: 40, stock: 2, lockStock: 0 },
    { id: 101, price: 10, stock: 5, lockStock: 4 },
    { id: 102, price: 0, stock: 1000 },
    { id: 103, price: 0.01, stock: 100000 },
  ]
  assert.equal(fullReductionCandidates(product, items, 'REBUY', 100).candidates.length, 0)
})

test('PICK routes and invalid full reduction settings produce no candidates', () => {
  assert.equal(fullReductionCandidates(detail(), items, 'PICK', 100), null)
  const product = detail()
  product.productFullReductionList = [{ fullPrice: 0, reducePrice: 0 }, { fullPrice: 100, reducePrice: 101 }]
  assert.equal(fullReductionCandidates(product, items, 'REBUY', 100), null)
})

test('split promotion lines produce one link per product and zone with original cart selection', () => {
  const offers = fullReductionOffers([{ fullReductionOffer: offer }, { fullReductionOffer: offer }])
  assert.equal(offers.length, 1)
  const url = fullReductionUrl(offers[0], [12, 14], 8)
  assert.match(url, /productId=10&zone=REBUY/)
  assert.match(url, /orderId=8/)
  assert.deepEqual(JSON.parse(decodeURIComponent(url.match(/cartIds=([^&]+)/)[1])), [12, 14])
})

test('actual offer panel plainly displays shortfall, discount rule and top-up entry', async () => {
  const file = fs.readFileSync(path.join(__dirname, '../src/components/FullReductionPanel.vue'), 'utf8')
  const template = file.split('<template>')[1].split('</template>')[0]
  const render = new Function('Vue', compile(template, { mode: 'function', prefixIdentifiers: true, isCustomElement: () => true }).code)(vue)
  const html = await renderToString(vue.createSSRApp({ render, setup: () => ({ offers: [offer], orderId: undefined, money: value => Number(value).toFixed(2), go() {} }) }))
  assert.match(html, /满100.00元减10.00元/)
  assert.match(html, /还差 ¥20.00 达到满减/)
  assert.match(html, /去凑单/)
})

test('checkout footer shows 0.50 for a payable small order and no free price for blocked settlement', async () => {
  const file = fs.readFileSync(path.join(__dirname, '../src/pages/order/createOrder.vue'), 'utf8')
  const template = file.split('<!-- 底部 -->')[1].split('<!-- 优惠券面板 -->')[0]
  const render = new Function('Vue', compile(template, { mode: 'function', prefixIdentifiers: true, isCustomElement: () => true }).code)(vue)
  const context = { hasBlockedZone: false, consumePoints: 0, calcAmount: { payAmount: 0.5 }, num: Number, handleSubmit() {}, settlementPending: false }
  const paidHtml = await renderToString(vue.createSSRApp({ render, setup: () => context }))
  assert.match(paidHtml, /0.50/)
  const blockedHtml = await renderToString(vue.createSSRApp({ render, setup: () => ({ ...context, hasBlockedZone: true, calcAmount: { payAmount: 0 } }) }))
  assert.match(blockedHtml, /待调整支付方式/)
  assert.doesNotMatch(blockedHtml, /class="price">0/)
})

function areaPage({ originalStatus = 0, initialQuantity = 2 } = {}) {
  const callbacks = {}, navigation = [], modals = [], calls = { add: 0 }
  const cart = initialQuantity ? [{ ...items[0], id: 1, quantity: initialQuantity }] : []
  const api = {
    getProductDetailAPI: async () => ({ data: detail() }),
    getCartListAPI: async () => ({ data: cart }),
    generateConfirmOrderAPI: async ids => ({ data: { cartPromotionItemList: cart.filter(item => ids.includes(item.id)) } }),
    getOrderDetailAPI: async () => ({ data: { status: originalStatus } }),
    addCartAPI: async input => {
      calls.add++
      const existing = cart.find(item => item.productSkuId === input.productSkuId)
      if (existing) existing.quantity += input.quantity
      else cart.push({ ...items[0], ...input, id: 8, price: detail().skuStockList.find(sku => sku.id === input.productSkuId).price })
    },
  }
  let source = fs.readFileSync(path.join(__dirname, '../src/pages/product/fullReduction.vue'), 'utf8').split('<script setup lang="ts">')[1].split('</script>')[0]
  source += '\nexports.page = {addAndCheckout,checkout,selection,cartIds,error}'
  const module = { exports: {} }
  vm.runInNewContext(ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 } }).outputText, {
    module, exports: module.exports, console,
    uni: { redirectTo: input => navigation.push(input.url), navigateTo: input => navigation.push(input.url), showToast() {}, showModal: input => modals.push(input) },
    require(name) {
      if (name === 'vue') return { ref: value => ({ value }) }
      if (name === '@dcloudio/uni-app') return { onLoad: fn => { callbacks.load = fn }, onShow: fn => { callbacks.show = fn } }
      if (name.startsWith('@/apis/')) return api
      if (name === '@/utils/fullReduction') return moduleValue.exports
      if (name === '@/utils/cartCheckout') return {
        parseCartIds: value => JSON.parse(value || '[]'),
        buildCartCheckoutUrl: ids => `/pages/order/createOrder?cartIds=${encodeURIComponent(JSON.stringify(ids))}`,
      }
      if (name === '@/utils/productImage') return { productImageUrl: value => value }
      throw Error(name)
    },
  })
  return { ...module.exports.page, callbacks, navigation, modals, calls, cart }
}

test('area entry adds the required quantity and returns the original selection to authoritative checkout', async () => {
  const page = areaPage()
  await page.callbacks.load({ productId: '10', zone: 'REBUY', threshold: '100', cartIds: '[1]' })
  assert.equal(page.selection.value.gap, 20)
  await page.addAndCheckout(101, 2)
  assert.equal(page.calls.add, 1)
  assert.equal(page.selection.value.gap, 0)
  assert.equal(page.navigation.length, 1)
  assert.deepEqual(JSON.parse(decodeURIComponent(page.navigation[0].split('cartIds=')[1])), [1, 8])
})

test('an existing pending or paid order requires handling before a replacement cart can be submitted', async () => {
  for (const originalStatus of [0, 1, 2, 3]) {
    const page = areaPage({ originalStatus, initialQuantity: 0 })
    await page.callbacks.load({ productId: '10', zone: 'REBUY', threshold: '100', orderId: '11' })
    assert.equal(page.selection.value.current, 0)
    await page.addAndCheckout(100, 3)
    assert.equal(page.calls.add, 0)
    assert.equal(page.navigation.length, 0)
    assert.equal(page.modals.length, 1)
  }
})

test('after the original order is cancelled a fresh combined cart reaches the threshold on its own', async () => {
  const page = areaPage({ originalStatus: 4, initialQuantity: 0 })
  await page.callbacks.load({ productId: '10', zone: 'REBUY', threshold: '100', orderId: '11' })
  assert.equal(page.selection.value.candidates[0].quantity, 3)
  await page.addAndCheckout(100, 3)
  assert.equal(page.calls.add, 1)
  assert.equal(page.selection.value.gap, 0)
  assert.equal(page.navigation.length, 1)
})
