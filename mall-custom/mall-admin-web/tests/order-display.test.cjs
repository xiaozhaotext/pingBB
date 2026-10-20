const {test}=require('node:test'),assert=require('node:assert/strict')
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),ts=require('typescript')
const source=fs.readFileSync(path.join(__dirname,'../src/utils/orderDisplay.ts'),'utf8')
const context={exports:{}}
vm.runInNewContext(ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.CommonJS}}).outputText,context)
const display=context.exports
test('sandbox mock payment is paid, with an explicit method instead of unpaid fallback',()=>{
  assert.equal(display.orderPayTypeLabel(3),'沙盒模拟支付')
  assert.equal(display.orderPaymentStatusLabel(1),'已支付')
  assert.equal(display.orderPayTypeLabel(99),'未知支付方式')
  assert.equal(display.orderPaymentStatusLabel(4),'已关闭')
})
test('funds amount includes wallet payments and never falls back to gross when cash is zero',()=>{
  assert.equal(display.orderFundsAmount({totalAmount:3499,payAmount:2310,balanceAmount:0}),2310)
  assert.equal(display.orderFundsAmount({totalAmount:99,payAmount:0,balanceAmount:70}),70)
  assert.equal(display.orderFundsAmount({totalAmount:100,payAmount:0,balanceAmount:0}),0)
})
