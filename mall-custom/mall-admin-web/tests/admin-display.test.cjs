const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs'), path = require('node:path'), vm = require('node:vm')
const ts = require('typescript'), { parse } = require('@vue/compiler-sfc')
function moduleFrom(file, imports = {}, extra = '') {
  let source = fs.readFileSync(path.join(__dirname, '../src', file), 'utf8')
  if (file.endsWith('.vue')) source = parse(source).descriptor.scriptSetup.content
  const context = { module: { exports: {} }, exports: {}, console, Intl, Set,
    require: name => imports[name] || {} }
  context.exports = context.module.exports
  vm.runInNewContext(ts.transpileModule(source + extra, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 } }).outputText, context)
  return context.module.exports
}
const display = moduleFrom('utils/adminDisplay.ts')
test('money formats thousands and keeps zero distinct from missing values', () => {
  assert.equal(display.formatMoney(195936.2), '195,936.20')
  assert.equal(display.formatMoney(-0), '0.00')
  assert.equal(display.formatMoney(null), '—')
})
test('internal ledger keys have readable Chinese descriptions', () => {
  assert.equal(display.businessText('tri:batch:70:82'), '三池分红（批次 70，会员 82）')
  assert.equal(display.businessText('tri:MEMBERSHIP:43:referral'), '会员购买直推奖励（会员单 43）')
  assert.equal(display.businessText('PARTNER分红；批次70'), '合伙人分红；批次70')
  assert.equal(display.businessText('MEMBERSHIP:44'), '会员购买（会员单 44）')
  assert.equal(display.businessText('QA sandbox demonstration only'), '沙箱演示记录')
  assert.equal(display.businessText('ADMIN:3'), '平台加款（操作人 3）')
  assert.equal(display.businessText('WITHDRAW:4'), '提现转入（申请 4）')
  assert.equal(display.businessText('RCabc123'), '充值单 abc123')
})
test('business source uses membership context and actual order numbers', () => {
  assert.equal(display.businessSource(44, 'earn 会员购买权益'), '会员购买权益（编号 44）')
  assert.equal(display.businessSource(132, 'order_pay', '202610170001'), '商品订单 202610170001')
})
function adminPage(api) {
  return moduleFrom('views/ums/admin/index.vue', {
    vue: { ...require('vue'), onMounted() {} },
    '@/apis/admin.ts': { getAdminListAPI: api },
  }, '\nmodule.exports={getList,list,total,listLoading,loadError,listQuery,handleResetSearch}')
}
test('admin page shows all five accounts from CommonPage', async () => {
  const page = adminPage(async () => ({ data: { list: Array.from({length:5}, (_, id) => ({id})), total:5 } }))
  await page.getList()
  assert.equal(page.list.value.length,5); assert.equal(page.total.value,5)
  assert.equal(page.loadError.value,'')
})
test('admin request failure is not shown as a valid empty list; retry recovers', async () => {
  let fail = true
  const page = adminPage(async () => { if (fail) throw Error('offline'); return {data:{list:[{id:3}],total:1}} })
  await page.getList(); assert.match(page.loadError.value,/加载失败/)
  assert.equal(page.listLoading.value,false)
  fail = false; await page.getList(); assert.equal(page.total.value,1); assert.equal(page.loadError.value,'')
})
test('stale administrator response cannot overwrite a newer search', async () => {
  let finish; let calls = 0
  const page = adminPage(() => ++calls === 1 ? new Promise(resolve => finish = resolve) : Promise.resolve({data:{list:[{id:2}],total:1}}))
  const old = page.getList(); await page.getList(); finish({data:{list:[],total:0}}); await old
  assert.equal(page.list.value[0].id,2)
})
test('ledger display resolves nicknames and real order number without changing ledger records', async () => {
  const row = {member_id:82,biz_id:'132',change_type:'order_pay'}; const before = JSON.stringify(row)
  const {useLedgerDisplay} = moduleFrom('composables/useLedgerDisplay.ts', {
    vue: require('vue'), '@/utils/adminDisplay': display,
    '@/apis/p0': {getP0: async () => ({data:{profile:{nickname:'测试昵称',username:'account'}}})},
    '@/apis/order': {getOrderDetailByIdAPI:async () => ({data:{orderSn:'202610170001'}})},
  })
  const view = useLedgerDisplay(); await view.hydrate([row],true)
  assert.equal(view.memberName(row),'测试昵称'); assert.equal(view.source(row),'商品订单 202610170001')
  assert.equal(JSON.stringify(row),before)
})
