import fs from 'node:fs'
import path from 'node:path'

const root = process.argv[2]
if (!root) throw new Error('请提供小程序编译包目录')
const jsPath = path.join(root, 'pages/index/index.js')
const wxmlPath = path.join(root, 'pages/index/index.wxml')
let js = fs.readFileSync(jsPath, 'utf8')
let wxml = fs.readFileSync(wxmlPath, 'utf8')

function once(source, before, after) {
  const first = source.indexOf(before)
  if (first < 0 || source.indexOf(before, first + before.length) >= 0)
    throw new Error(`预期只匹配一处：${before.slice(0, 50)}`)
  return source.slice(0, first) + after + source.slice(first + before.length)
}

js = once(js,
  'miniLayout=e.ref(miniHomePresentation.layout()),miniText=e.ref(miniHomePresentation.fallbackText());',
  'miniLayout=e.ref(miniHomePresentation.layout()),miniText=e.ref(miniHomePresentation.fallbackText()),miniSections=e.ref({...miniHomePresentation.DEFAULT_SECTIONS}),miniZoneOrder=e.ref(miniHomePresentation.zoneOrder(miniHomePresentation.DEFAULT_SECTIONS));')
js = once(js,
  'miniText.value=data.text;miniLayout.value=miniHomePresentation.layout();',
  'miniText.value=data.text;miniLayout.value=miniHomePresentation.layout();miniSections.value=data.sections;miniZoneOrder.value=data.zoneOrder;')
js = once(js,
  'miniText:miniText.value,miniHeroCaptions:',
  'miniText:miniText.value,miniSections:miniSections.value,miniZoneOrder:miniZoneOrder.value,miniHeroCaptions:')

wxml = once(wxml, 'wx:if="{{j}}" class="travel-hero', 'wx:if="{{j && miniSections.showHero}}" class="travel-hero')
wxml = once(wxml, 'wx:if="{{q}}" class="hero-pagination', 'wx:if="{{q && miniSections.showHero}}" class="hero-pagination')
wxml = once(wxml, '<view class="home-announcement ', '<view wx:if="{{miniSections.showAnnouncement}}" class="home-announcement ')
wxml = once(wxml, '<view class="home-quick-links ', '<view wx:if="{{miniSections.showQuickLinks}}" class="home-quick-links ')
wxml = once(wxml, '<view class="home-zones ', '<view wx:if="{{miniSections.showPick || miniSections.showRebuy || miniSections.showConsume}}" class="home-zones ')
wxml = once(wxml, 'class="home-zone brand-zone data-v-641dbf77" style="height:', 'wx:if="{{miniSections.showPick}}" class="home-zone brand-zone data-v-641dbf77" style="order:{{miniZoneOrder.pick}};height:')
wxml = once(wxml, 'class="home-zone rebuy-zone data-v-641dbf77" style="width:', 'wx:if="{{miniSections.showRebuy}}" class="home-zone rebuy-zone data-v-641dbf77" style="order:{{miniZoneOrder.rebuy}};width:')
wxml = once(wxml, 'class="home-zone consume-zone data-v-641dbf77" style="width:', 'wx:if="{{miniSections.showConsume}}" class="home-zone consume-zone data-v-641dbf77" style="order:{{miniZoneOrder.consume}};width:')

const starts = [...wxml.matchAll(/<view class="home-product-heading data-v-641dbf77">/g)].map(match => match.index)
if (starts.length !== 3) throw new Error('商品板块结构发生变化，停止修改')
const endMarker = '</view></view><main-navigation'
const end = wxml.lastIndexOf(endMarker)
if (end < starts[2]) throw new Error('找不到首页板块结尾，停止修改')
// 从后向前插入，保留原有的商品查询、购物车和点击事件。
for (const [start, finish, key] of [[starts[2], end, 'showConsume'], [starts[1], starts[2], 'showRebuy'], [starts[0], starts[1], 'showPick']]) {
  wxml = wxml.slice(0, finish) + '</view>' + wxml.slice(finish)
  wxml = wxml.slice(0, start) + `<view wx:if="{{miniSections.${key}}}">` + wxml.slice(start)
}

fs.writeFileSync(jsPath, js)
fs.writeFileSync(wxmlPath, wxml)
console.log('小程序首页板块设置已接入，原事件与商品内容保留')
