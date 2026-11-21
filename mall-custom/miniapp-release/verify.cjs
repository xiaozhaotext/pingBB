const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const root = path.join(__dirname, 'package')
const settings = require(path.join(root, 'utils/miniHomePresentation.js'))
const defaults = settings.sections(null)
assert.deepEqual(defaults.zoneOrder, ['pick', 'rebuy', 'consume'])
assert.equal(defaults.showPick, true)
assert.equal(defaults.showRebuy, true)
assert.equal(defaults.showConsume, true)
const changed = settings.sections({ showConsume: false, zoneOrder: ['consume', 'pick', 'rebuy'] })
assert.equal(changed.showConsume, false)
assert.deepEqual(settings.zoneOrder(changed), { consume: 1, pick: 2, rebuy: 3 })
assert.deepEqual(settings.sections({ zoneOrder: ['pick', 'pick', 'consume'] }).zoneOrder, defaults.zoneOrder)
assert.equal(settings.links({ pick: '/pages/notice/notice' }).pick, '/pages/notice/notice')
assert.equal(settings.links({ pick: 'https://example.invalid' }).pick, '/pages/zone/pick')
const wxml = fs.readFileSync(path.join(root, 'pages/index/index.wxml'), 'utf8')
assert.equal((wxml.match(/<view(?:\s|>)/g) || []).length, (wxml.match(/<\/view>/g) || []).length)
const stack = []
for (const match of wxml.matchAll(/<\/?([a-z][\w-]*)(?:\s[^<>]*?)?\/?>/g)) {
  const tag = match[1]
  if (match[0].startsWith('</')) assert.equal(stack.pop(), tag, `标签嵌套错误：${tag}`)
  else if (!match[0].endsWith('/>')) stack.push(tag)
}
assert.equal(stack.length, 0, '首页标签未闭合')
for (const key of ['showPick', 'showRebuy', 'showConsume', 'showHero', 'showAnnouncement', 'showQuickLinks'])
  assert.ok(wxml.includes(`miniSections.${key}`), `${key} 未绑定到首页`)
global.wx = {
  request({ url, success }) {
    const data = url.includes('home-layout.json') ? { showConsume: false, zoneOrder:['rebuy','consume','pick'] }
      : url.includes('home-links.json') ? { pick:'/pages/notice/notice' }
      : url.includes('home-copy.json') ? { brand:'测试名称' } : null
    success({ statusCode:data ? 200 : 404, data })
  },
  setStorageSync() {},
}
settings.load(true).then(data => {
  assert.equal(data.sections.showConsume, false)
  assert.equal(data.zoneOrder.pick, 3)
  assert.equal(data.links.pick, '/pages/notice/notice')
  assert.equal(data.text.brand, '测试名称')
  console.log('小程序首页默认内容、后台配置读取、无效配置回退及页面结构检查通过')
}).catch(error => { console.error(error); process.exitCode = 1 })
