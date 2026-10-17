const {test}=require('node:test'),assert=require('node:assert/strict')
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),ts=require('typescript'),{parse}=require('@vue/compiler-sfc')
function orderPage(order,returns) {
  const source=parse(fs.readFileSync(path.join(__dirname,'../src/views/oms/order/index.vue'),'utf8')).descriptor.scriptSetup.content
  const context={module:{exports:{}},exports:{},console,
    require:n=>n==='vue'?{...require('vue'),onMounted:()=>{}}:n==='vue-router'?{useRouter:()=>({})}:n==='@/stores/order'?{useOrderStore:()=>({})}:n==='@/apis/order'?{getOrderListAPI:order}:n==='@/apis/returnApply'?{getReturnApplyListAPI:returns}:{}}
  vm.runInNewContext(ts.transpileModule(source+'\nmodule.exports={getList,list,listLoading,total,activeStatus,returnApplies,listQuery}',{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2020}}).outputText,context)
  return context.module.exports
}
function deferred(){let resolve,reject;const promise=new Promise((r,j)=>{resolve=r;reject=j});return {promise,resolve,reject}}
test('late order response cannot overwrite the newly selected tab',async()=>{
  const a=deferred(),b=deferred();const queries=[];const p=orderPage(q=>{queries.push(q);return queries.length===1?a.promise:b.promise})
  const first=p.getList();p.activeStatus.value='completed';const second=p.getList()
  b.resolve({data:{list:[{id:3,status:3}],total:1}});await second
  a.resolve({data:{list:[{id:1,status:1}],total:9}});await first
  assert.equal(p.list.value[0].id,3);assert.equal(p.total.value,1);assert.deepEqual(queries.map(q=>q.status),[1,3]);assert.equal(p.listLoading.value,false)
})
test('late after-sales response cannot replace current order results',async()=>{
  const a=deferred();const p=orderPage(async()=>({data:{list:[{id:2}],total:1}}),()=>a.promise)
  p.activeStatus.value='after-sales';const first=p.getList();p.activeStatus.value='pending-receipt';await p.getList()
  a.resolve({data:{list:[{id:99}],total:9}});await first
  assert.equal(p.list.value[0].id,2);assert.equal(p.returnApplies.value.length,0);assert.equal(p.total.value,1)
})
test('stale request completion does not hide the active loading indicator',async()=>{
  const a=deferred(),b=deferred();let n=0;const p=orderPage(()=>++n===1?a.promise:b.promise)
  const first=p.getList();p.activeStatus.value='completed';const second=p.getList()
  a.resolve({data:{list:[],total:0}});await first;assert.equal(p.listLoading.value,true)
  b.resolve({data:{list:[],total:0}});await second;assert.equal(p.listLoading.value,false)
})
test('new tab clears stale rows immediately and reports an empty result correctly',async()=>{
  const a=deferred();const p=orderPage(()=>a.promise);p.list.value=[{id:1}];p.total.value=1
  const pending=p.getList();assert.equal(p.list.value.length,0);assert.equal(p.total.value,0)
  a.resolve({data:{list:[],total:0}});await pending;assert.equal(p.list.value.length,0);assert.equal(p.listLoading.value,false)
})
test('admin catalog routes normalize legacy OSS URLs without changing external images',()=>{
  const source=fs.readFileSync(path.join(__dirname,'../src/utils/productImage.ts'),'utf8')
  const context={exports:{},window:{location:{origin:'https://example.test'}}}
  vm.runInNewContext(ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.CommonJS}}).outputText,context)
  const url=context.exports.productImageUrl
  assert.equal(url('http://macro-oss.oss-cn-shenzhen.aliyuncs.com/a.jpg'),'https://example.test/oss/a.jpg')
  assert.equal(url('/oss/a.jpg'),'https://example.test/oss/a.jpg');assert.equal(url('oss/a.jpg'),'https://example.test/oss/a.jpg')
  assert.equal(url('https://other.test/a.jpg'),'https://other.test/a.jpg');assert.equal(url(null),'')
})
