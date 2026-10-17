const {test}=require('node:test'),assert=require('node:assert/strict')
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),ts=require('typescript'),{parse}=require('@vue/compiler-sfc')
function setup(orderApi=async()=>({data:{list:[]}}),saleApi=async()=>({data:{list:[]}})) {
  const source=parse(fs.readFileSync(path.join(__dirname,'../src/pages/order/order.vue'),'utf8')).descriptor.scriptSetup.content
  let onLoad;const events=[];const module={exports:{}}
  vm.runInNewContext(ts.transpileModule(source+'\nmodule.exports={navList,tabCurrentIndex,orderList,afterSaleList,loadingType,searchParam,loadData,handleTabClick,handleSwiperChange}',{
    compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2020}
  }).outputText,{module,exports:module.exports,console,
    require:n=>n==='vue'?require('vue'):n==='@dcloudio/uni-app'?{onLoad:fn=>onLoad=fn,onPullDownRefresh(){}}:n==='@/apis/order'?{getOrderListAPI:orderApi}:n==='@/utils/http'?{http:saleApi}:{},
    uni:{stopPullDownRefresh(){},showToast:e=>events.push(e)}})
  return {...module.exports,onLoad,events}
}
function deferred(){let resolve,reject;return {promise:new Promise((r,j)=>{resolve=r;reject=j}),get resolve(){return resolve},get reject(){return reject}}}
test('paid checkout target selects pending shipment and requests paid status',async()=>{
  const calls=[];const page=setup(async q=>{calls.push(q);return {data:{list:[]}}})
  page.onLoad({state:'2'});await Promise.resolve()
  assert.equal(page.navList[page.tabCurrentIndex.value].text,'待发货');assert.equal(calls[0].status,1)
})
test('legacy and invalid tab parameters safely open all orders',async()=>{
  for(const state of ['-1','999','invalid','1.5']){
    const calls=[];const page=setup(async q=>{calls.push(q);return {data:{list:[]}}})
    page.onLoad({state});await Promise.resolve();assert.equal(page.tabCurrentIndex.value,0);assert.equal(calls[0].status,-1)
  }
})
test('rapid tab switch starts the new request and ignores an older response',async()=>{
  const old=deferred(),fresh=deferred(),queries=[]
  const page=setup(q=>{queries.push(q);return queries.length===1?old.promise:fresh.promise})
  const a=page.loadData();page.tabCurrentIndex.value=2;const b=page.loadData()
  assert.equal(queries.length,2);assert.equal(queries[0].status,-1);assert.equal(queries[1].status,1)
  fresh.resolve({data:{list:[{id:2}]}});await b;old.resolve({data:{list:[{id:1}]}});await a
  assert.equal(page.orderList.value[0].id,2)
})
test('late after-sales response cannot populate the normal order tab',async()=>{
  const old=deferred();const page=setup(async()=>({data:{list:[{id:9}]}}),()=>old.promise)
  page.tabCurrentIndex.value=6;const a=page.loadData();page.tabCurrentIndex.value=2;await page.loadData()
  old.resolve({data:{list:[{id:7}]}});await a
  assert.equal(page.orderList.value[0].id,9);assert.equal(page.afterSaleList.value.length,0)
})
test('after-sales pagination advances and a failed load can retry the same page',async()=>{
  const calls=[];let fail=true
  const page=setup(undefined,async q=>{calls.push(q.data.pageNum);if(q.data.pageNum===2&&fail)throw Error('offline');return {data:{list:[{id:q.data.pageNum}]}}})
  page.tabCurrentIndex.value=6;await page.loadData();await page.loadData('add');assert.equal(page.searchParam.value.pageNum,1)
  fail=false;await page.loadData('add');assert.equal(calls.join(','),'1,2,2');assert.equal(page.afterSaleList.value.length,2)
})
test('tab click and its swiper event do not issue duplicate requests',async()=>{
  let calls=0;const page=setup(async()=>{calls++;return {data:{list:[]}}})
  page.handleTabClick(2);page.handleSwiperChange({detail:{current:2}});await Promise.resolve();assert.equal(calls,1)
})
