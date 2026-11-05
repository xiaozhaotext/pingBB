const {test}=require('node:test')
const assert=require('node:assert/strict')
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),ts=require('typescript')
function util(name){const module={exports:{}};vm.runInNewContext(ts.transpileModule(fs.readFileSync(path.join(__dirname,'../src/utils',name+'.ts'),'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS}}).outputText,{module,exports:module.exports,URL});return module.exports}
function page({wechat=true,states=[{state:'CONFIRMED',canRetry:false}],orderStatus=0,bridgeError,prepay,successPage=false,order={}}={}){
 const callbacks={},storage=new Map(),redirects=[],timers=[],calls={jsapi:0,status:0,h5:0,mock:0}
 const file=successPage?'paySuccess.vue':'pay.vue'
 let source=fs.readFileSync(path.join(__dirname,'../src/pages/money',file),'utf8').split('<script setup lang="ts">')[1].split('</script>')[0]
 source=source.replace(/import\.meta\.env\.VITE_USE_WECHAT_PAY/g,'"true"').replace(/import\.meta\.env\.VITE_USE_ALIPAY/g,'"false"').replace(/import\.meta\.env\.VITE_USE_MOCK_PAYMENT/g,'"false"').replace(/import\.meta\.env\.VITE_API_BASE_URL/g,'"/portal"')
 const api={getOrderDetailAPI:async()=>({data:{id:11,orderSn:'SN11',status:orderStatus,payAmount:10,...order}}),
  getWechatOrderPaymentStatusAPI:async()=>{calls.status++;const state=states[Math.min(calls.status-1,states.length-1)];if(state instanceof Error)throw state;return {data:state}},
  wechatOrderPayJsapiAPI:async()=>{calls.jsapi++;if(prepay)await prepay();return {data:{package:'prepay_id=p'}}},
  wechatOrderPayH5API:async()=>{calls.h5++;return {data:{h5Url:'https://wx.tenpay.com/cgi-bin/mmpayweb-bin/checkmweb?prepay_id=p'}}},
  confirmMockPaymentAPI:async()=>{calls.mock++},getOrderFullReductionOffersAPI:async()=>({data:[]})}
 const module={exports:{}};const context={module,exports:module.exports,console,Error,URL,URLSearchParams,
  window:{location:{href:'https://zhiyoumei.com.cn/sandbox/#/pages/money/pay?orderId=11'},addEventListener(){},removeEventListener(){}},
  document:{visibilityState:'visible',addEventListener(){},removeEventListener(){}},
  setTimeout:(fn,ms)=>{if(ms===1500){Promise.resolve().then(fn);return 1}timers.push(fn);return timers.length},clearTimeout(){},
  uni:{getStorageSync:k=>storage.get(k),setStorageSync:(k,v)=>storage.set(k,v),removeStorageSync:k=>storage.delete(k),redirectTo:x=>redirects.push(x.url),switchTab:x=>redirects.push(x.url),showToast(){}},
  require(name){
   if(name==='vue')return {ref:value=>({value}),onMounted:fn=>{callbacks.mounted=fn},onUnmounted:fn=>{callbacks.unmounted=fn}}
   if(name==='@dcloudio/uni-app')return {onLoad:fn=>{callbacks.load=fn},onShow:fn=>{callbacks.show=fn},onUnload:fn=>{callbacks.unload=fn}}
   if(name==='@/apis/order')return api
   if(name==='@/utils/wechatPay')return {isWechatBrowser:()=>wechat,ensureWechatOauth:async()=>true,invokeWechatJsapi:async()=>{if(bridgeError)throw bridgeError}}
   if(name==='@/utils/paymentResult')return util('paymentResult')
   if(name==='@/utils/sessionKeys')return {tokenStorageKey:'token'}
   throw Error(name)
  }}
 source+='\nexports.page = '+(successPage?'{loadData,confirmed,payText,resultMessage}':'{handleConfirmPay,paymentState,paymentMessage,checking,paying,retryCheck,orderInfo,money,orderPromotionRules,USE_ALIPAY,USE_WECHAT_PAY,USE_MOCK_PAYMENT,payType,handleChangePayType,viewOrder,paymentOffers,orderId}')
 vm.runInNewContext(ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2020}}).outputText,context)
 return {...module.exports.page,callbacks,storage,redirects,timers,calls,context}
}
test('JSAPI success waits for server confirmation before navigating with order identity',async()=>{
 const p=page({states:[{state:'PENDING',canRetry:false},{state:'CONFIRMED',canRetry:false}]});await p.callbacks.load({orderId:'11'});await p.handleConfirmPay()
 assert.equal(p.calls.status,2);assert.deepEqual(p.redirects,['/pages/money/paySuccess?orderId=11']);assert.equal(p.storage.size,0);assert.equal(p.calls.mock,0)
})
test('bridge success alone never claims payment succeeded or enables another payment',async()=>{
 const p=page({states:[{state:'PENDING',canRetry:true}]});await p.callbacks.load({orderId:11});await p.handleConfirmPay();await p.handleConfirmPay()
 assert.equal(p.paymentState.value,'pending');assert.equal(p.calls.jsapi,1);assert.equal(p.redirects.length,0);assert.match(p.paymentMessage.value,/不代表支付失败/)
})
test('network failure after payment preserves a recoverable pending result',async()=>{
 const p=page({states:[Error('offline')]});await p.callbacks.load({orderId:11});await p.handleConfirmPay()
 assert.equal(p.paymentState.value,'pending');assert.equal(p.storage.get('token:order-payment:11'),'submitted');assert.equal(p.redirects.length,0)
})
test('paid and closed orders cannot start another charge',async()=>{
 for(const status of [1,2,3,4]){const p=page({orderStatus:status});await p.callbacks.load({orderId:11});await p.handleConfirmPay();assert.equal(p.calls.jsapi,0);assert.equal(p.redirects.length,status===4?0:1)}
})
test('cancelled dialog does not become a successful or failed cash payment',async()=>{
 const p=page({bridgeError:Error('get_brand_wcpay_request:cancel')});await p.callbacks.load({orderId:11});await p.handleConfirmPay();assert.equal(p.paymentState.value,'ready');assert.equal(p.redirects.length,0);assert.equal(p.storage.size,0)
})
test('H5 redirect returns to the same environment and then verifies without starting a payment',async()=>{
 const p=page({wechat:false});await p.callbacks.load({orderId:11});await p.handleConfirmPay()
 const returned=new URL(p.context.window.location.href).searchParams.get('redirect_url');assert.equal(returned,'https://zhiyoumei.com.cn/sandbox/#/pages/money/pay?orderId=11&checkPayment=1')
 const resumed=page();await resumed.callbacks.load({orderId:11,checkPayment:'1'});assert.equal(resumed.calls.jsapi,0);assert.equal(resumed.calls.status,1);assert.equal(resumed.redirects.length,1)
})
test('paid but unfulfilled orders show review and prohibit repeat charge',async()=>{
 const p=page({states:[{state:'REVIEW',canRetry:false}]});await p.callbacks.load({orderId:11});await p.handleConfirmPay();await p.handleConfirmPay();assert.equal(p.paymentState.value,'review');assert.match(p.paymentMessage.value,/微信已收款/);assert.equal(p.calls.jsapi,1)
})
test('rapid clicks only start one SDK payment request',async()=>{
 let release;const p=page({prepay:()=>new Promise(r=>{release=r})});await p.callbacks.load({orderId:11});const first=p.handleConfirmPay();await Promise.resolve();await p.handleConfirmPay();release();await first;assert.equal(p.calls.jsapi,1)
})
test('success page never trusts entry URL and only returns to shop after persisted success',async()=>{
 const p=page({successPage:true,orderStatus:1});await p.callbacks.load({orderId:11});assert.equal(p.confirmed.value,true);p.timers[0]();assert.deepEqual(p.redirects,['/pages/index/index'])
 const pending=page({successPage:true});await pending.callbacks.load({orderId:11,success:'true'});assert.equal(pending.confirmed.value,false);assert.equal(pending.timers.length,0)
 const missing=page({successPage:true});await missing.callbacks.load({success:'true'});assert.equal(missing.confirmed.value,false)
})
test('actual payment template plainly renders the threshold, discount and final payable amount',async()=>{
 const p=page({order:{totalAmount:100,promotionAmount:10,payAmount:90,orderItemList:[{promotionName:'满100元减10元'}]}})
 await p.callbacks.load({orderId:11})
 const vue=require('vue'),{compile}=require('@vue/compiler-dom'),{renderToString}=require('@vue/server-renderer')
 const template=fs.readFileSync(path.join(__dirname,'../src/pages/money/pay.vue'),'utf8').split('<template>')[1].split('</template>')[0]
 const render=new Function('Vue',compile(template,{mode:'function',isCustomElement:()=>true}).code)(vue)
 const ctx=Object.fromEntries(Object.entries(p).map(([key,value])=>[key,value&&typeof value==='object'&&'value' in value?value.value:value]))
 const html=await renderToString(vue.createSSRApp({render,setup:()=>ctx}))
 assert.match(html,/满100元减10元/);assert.match(html,/本单活动优惠（已计入应付）/);assert.match(html,/-¥10\.00/);assert.match(html,/本次实际支付/);assert.match(html,/¥90\.00/)
})
