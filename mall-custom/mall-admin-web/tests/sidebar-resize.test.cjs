const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const vm = require('node:vm')
const ts = require('typescript')
const path = require('node:path')
function setup(width) {
  const store = { device: 'desktop', sidebar: { opened: true }, closeCount: 0,
    toggleDevice(value) { this.device = value }, closeSideBar() { this.sidebar.opened = false; this.closeCount++ } }
  const mounted = [], before = [], watchers = [], listeners = {}, route = { path: '/dividendPools/partner' }
  const window = { innerWidth: width, addEventListener(name, fn) { listeners[name] = fn }, removeEventListener(name) { delete listeners[name] } }
  const vue = { onMounted: fn => mounted.push(fn), onBeforeMount: fn => before.push(fn), onBeforeUnmount: () => {}, watch: (source, fn) => watchers.push({ source, fn }) }
  const sandbox = { exports: {}, window, document: { hidden: false }, require(name) {
    if (name === 'vue') return vue
    if (name === 'vue-router') return { useRoute: () => route }
    if (name === '@/stores/app') return { useAppStore: () => store }
    throw new Error(name)
  } }
  const source = fs.readFileSync(path.join(__dirname, '../src/views/layout/composables/useResizeHandler.ts'), 'utf8')
  vm.runInNewContext(ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText, sandbox)
  sandbox.exports.default(); before.forEach(fn => fn()); mounted.forEach(fn => fn())
  return { store, window, listeners, watchers, route }
}
test('mobile browser height-only resizes keep an open menu available for scrolling', () => {
  const state = setup(390)
  state.store.sidebar.opened = true
  const closed = state.store.closeCount
  state.listeners.resize(); state.listeners.resize()
  assert.equal(state.store.sidebar.opened, true)
  assert.equal(state.store.closeCount, closed)
})
test('a real desktop-to-mobile breakpoint closes the drawer once', () => {
  const state = setup(1280)
  state.window.innerWidth = 390; state.listeners.resize()
  assert.equal(state.store.device, 'mobile'); assert.equal(state.store.sidebar.opened, false)
  state.store.sidebar.opened = true; state.listeners.resize()
  assert.equal(state.store.sidebar.opened, true)
})
test('navigation closes the mobile drawer while submenu expansion is not a route change', () => {
  const state = setup(390); state.store.sidebar.opened = true
  assert.equal(state.watchers[0].source(), '/dividendPools/partner')
  state.route.path = '/dividendPools/operations'; state.watchers[0].fn()
  assert.equal(state.store.sidebar.opened, false)
})
