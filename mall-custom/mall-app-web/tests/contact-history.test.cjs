const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const vm = require('node:vm')
const ts = require('typescript')
const path = require('node:path')
const root = path.join(__dirname, '../src')
const helperContext = { exports: {}, URL }
vm.runInNewContext(ts.transpileModule(fs.readFileSync(path.join(root, 'utils/contactNavigation.ts'), 'utf8'), { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText, helperContext)
const helper = helperContext.exports
function app({ saved = true, memberId = 27, url = 'https://example.com/#/pages/member/contact' } = {}) {
  const listeners = {}, pending = [], redirects = []
  const source = fs.readFileSync(path.join(root, 'App.vue'), 'utf8').split('<script setup lang="ts">')[1].split('</script>')[0]
  const context = { exports: {}, window: { location: { href: url }, addEventListener: (name, fn) => { listeners[name] = fn } }, setTimeout: fn => pending.push(fn), console: { log() {} },
    uni: { getStorageSync: key => saved && key === 'contact-saved-return:27', reLaunch: args => { redirects.push(args.url); args.complete?.() } },
    require(name) {
      if (name === '@dcloudio/uni-app') return { onLaunch: fn => fn(), onShow() {}, onHide() {} }
      if (name === '@/utils/theme') return { getTheme() {}, applyTheme() {} }
      if (name === '@/stores/member') return { useMemberStore: () => ({ memberInfo: memberId ? { id: memberId } : null }) }
      if (name === '@/utils/contactNavigation') return helper
      throw new Error(name)
    } }
  vm.runInNewContext(ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText, context)
  return { redirects, back() { listeners.popstate(); pending.splice(0).forEach(fn => fn()) } }
}
test('saved contact history returns to My Account without reopening the form', () => { const state = app(); state.back(); assert.deepEqual(state.redirects, ['/pages/user/user']) })
test('a failed or unsaved form is not silently skipped', () => { const state = app({ saved: false }); state.back(); assert.deepEqual(state.redirects, []) })
test('one account save cannot skip another account form', () => { const state = app({ memberId: 28 }); state.back(); assert.deepEqual(state.redirects, []) })
test('normal browsing and explicit entry do not trigger an automatic profile prompt', () => { const state = app({ url: 'https://example.com/#/pages/user/user' }); state.back(); assert.deepEqual(state.redirects, []); assert.deepEqual(app().redirects, []) })
test('history mode and hash mode only match the exact contact route', () => { assert.equal(helper.isContactHistoryEntry('https://example.com/pages/member/contact?edit=1'), true); assert.equal(helper.isContactHistoryEntry('https://example.com/#/pages/member/contact?edit=1'), true); assert.equal(helper.isContactHistoryEntry('https://example.com/#/pages/member/contact-other'), false) })
