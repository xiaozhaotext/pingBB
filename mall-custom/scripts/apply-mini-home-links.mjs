import fs from 'node:fs'
import path from 'node:path'

const root = process.argv[2]
if (!root) throw new Error('请提供小程序编译包目录')
const file = path.join(root, 'pages/index/index.js')
let js = fs.readFileSync(file, 'utf8')
function replaceOnce(before, after) {
  const at = js.indexOf(before)
  if (at < 0 || js.indexOf(before, at + before.length) >= 0)
    throw new Error(`首页编译结构变化，停止修改：${before.slice(0, 60)}`)
  js = js.slice(0, at) + after + js.slice(at + before.length)
}
replaceOnce(
  'miniZoneOrder=e.ref(miniHomePresentation.zoneOrder(miniHomePresentation.DEFAULT_SECTIONS));let miniPresentationRun',
  'miniZoneOrder=e.ref(miniHomePresentation.zoneOrder(miniHomePresentation.DEFAULT_SECTIONS)),miniLinks=e.ref({...miniHomePresentation.DEFAULT_LINKS});const miniNavigate=key=>{const url=miniLinks.value[key];url&&(url==="/pages/index/index"||url.startsWith("/pages/zone/")?c(url):v(url))};let miniPresentationRun')
replaceOnce('miniZoneOrder.value=data.zoneOrder;try', 'miniZoneOrder.value=data.zoneOrder;miniLinks.value=data.links;try')
replaceOnce('c:e.o(e=>v("/pages/cart/cart"),"fa")', 'c:e.o(e=>miniNavigate("topCart"),"fa")')
replaceOnce('a:e.o(e=>c("/pages/index/index"),"9f")', 'a:e.o(e=>miniNavigate("brand"),"9f")')
replaceOnce('w:e.o(e=>v("/pages/category/category"),"69")', 'w:e.o(e=>miniNavigate("quickCategory"),"69")')
replaceOnce('x:e.o(e=>v("/pages/cart/cart"),"83")', 'x:e.o(e=>miniNavigate("quickCart"),"83")')
for (const [slot, token] of [['pick','53'],['rebuy','ec'],['consume','48'],
  ['pick','a0'],['rebuy','c7'],['consume','ad']]) {
  const name = { pick:'pick', rebuy:'rebuy', consume:'consume' }[slot]
  const before = `e.o(e=>c("/pages/zone/${slot}"),"${token}")`
  replaceOnce(before, `e.o(e=>miniNavigate("${name}"),"${token}")`)
}
fs.writeFileSync(file, js)
console.log('首页跳转已读取后台配置；商品详情、支付与订单事件未修改')
