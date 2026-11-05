const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const ts = require('typescript')

function page(file, { guest = false } = {}) {
  const callbacks = {}, navigation = [], state = { records: [{ id: 1, title: '公告', content: '完整正文', publish_time: '2026-11-05T14:00:00' }], read: 0 }
  const api = {
    getNoticesAPI: async () => ({ data: state.records }),
    getAnnouncementDetailAPI: async () => {
      if (state.unavailable) throw Error('404')
      return { data: state.records[0] }
    },
    readNoticeAPI: async () => { state.read++ },
  }
  let source = fs.readFileSync(path.join(__dirname, '../src/pages/notice', file), 'utf8').split('<script setup lang="ts">')[1].split('</script>')[0]
  source += '\nexports.page = ' + (file === 'notice.vue' ? '{notices,error,openNotice}' : '{notice,error}')
  const module = { exports: {} }
  vm.runInNewContext(ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 } }).outputText, {
    module, exports: module.exports,
    uni: { stopPullDownRefresh() {}, navigateTo: value => navigation.push(value.url), getStorageSync: () => guest ? '' : 'test-token' },
    require(name) {
      if (name === 'vue') return { ref: value => ({ value }) }
      if (name === '@dcloudio/uni-app') return { onLoad: fn => { callbacks.load = fn }, onShow: fn => { callbacks.show = fn }, onPullDownRefresh() {} }
      if (name === '@/apis/notice') return api
      if (name === '@/utils/chinaTime') return { chinaDateTime: value => value }
      if (name === '@/utils/sessionKeys') return { tokenStorageKey: 'token' }
      throw Error(name)
    },
  })
  return { ...module.exports.page, callbacks, navigation, state }
}

test('inbox opens a detail page and returning refreshes announcements removed by the administrator', async () => {
  const inbox = page('notice.vue')
  await inbox.callbacks.show()
  assert.equal(inbox.notices.value.length, 1)
  inbox.openNotice(inbox.notices.value[0])
  assert.deepEqual(inbox.navigation, ['/pages/notice/detail?id=1'])
  inbox.state.records = []
  await inbox.callbacks.show()
  assert.equal(inbox.notices.value.length, 0)
})

test('detail displays current content, marks it read, and discards cached content after unpublishing', async () => {
  const detail = page('detail.vue')
  await detail.callbacks.load({ id: '1' })
  assert.equal(detail.notice.value.content, '完整正文')
  assert.equal(detail.state.read, 1)
  detail.state.unavailable = true
  await detail.callbacks.show()
  await new Promise(resolve => setImmediate(resolve))
  assert.equal(detail.notice.value, undefined)
  assert.match(detail.error.value, /已下线、已删除/)
})

test('public announcement details remain available to guests without creating a read marker', async () => {
  const detail = page('detail.vue', { guest: true })
  await detail.callbacks.load({ id: '1' })
  assert.equal(detail.notice.value.title, '公告')
  assert.equal(detail.state.read, 0)
})

test('homepage follows the server publication result even for an old test title and opens that announcement', async () => {
  const full = fs.readFileSync(path.join(__dirname, '../src/pages/index/index.vue'), 'utf8')
  let source = full.slice(full.indexOf("const announcement=ref"), full.indexOf('const pickProducts='))
  source += '\nexports.page = {loadAnnouncement,openAnnouncement,announcement,announcementId}'
  const module = { exports: {} }, navigation = []
  let records = [{ id: 1, title: 'HTTPS验收测试-1791871259' }]
  vm.runInNewContext(ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 } }).outputText, {
    module, exports: module.exports, ref: value => ({ value }),
    uni: { navigateTo: input => navigation.push(input.url) },
    require: () => ({ getAnnouncementsAPI: async () => ({ data: records }) }),
  })
  const home = module.exports.page
  await home.loadAnnouncement()
  assert.equal(home.announcement.value, records[0].title)
  home.openAnnouncement()
  assert.deepEqual(navigation, ['/pages/notice/detail?id=1'])
  records = []
  await home.loadAnnouncement()
  assert.equal(home.announcement.value, '暂无公告')
  assert.equal(home.announcementId.value, undefined)
})
