const {test}=require('node:test'), assert=require('node:assert/strict')
const fs=require('node:fs'), vm=require('node:vm'), ts=require('typescript'), vue=require('vue')
function setup() {
 const route=vue.reactive({name:'rebuyProduct'}), calls=[], messages=[]
 let confirm=async()=>{}, fail=false
 const http=async req=>{calls.push(req); if(fail) throw {response:{data:{message:'不属于当前专区'}}}; return {data:req.method==='post'?req.data.ids.length:[]} }
 const module={exports:{}}
 const raw=fs.readFileSync(require('node:path').join(__dirname,'../src/views/zone/ZoneProductList.vue'),'utf8')
 let source=require('@vue/compiler-sfc').parse(raw).descriptor.scriptSetup.content.replace(/import\.meta\.env\.BASE_URL/g, "'/admin/'")
 source+='\nmodule.exports={rows,page,managementMode,selectedIds,allSelected,startBatch,selectAll,selectProduct,submitBatch,cancelBatch,zone};'
 vm.runInNewContext(ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2020,esModuleInterop:true}}).outputText,{
 module,exports:module.exports,console,require:name=>({'vue':{...vue,onMounted(){}},'vue-router':{useRoute:()=>route,useRouter:()=>({})},'@/utils/http':{__esModule:true,default:http},'element-plus':{ElMessage:{success:s=>messages.push(s),error:s=>messages.push(s)},ElMessageBox:{confirm:(...args)=>confirm(...args)}}}[name]||{})
 })
 return {s:module.exports,route,calls,messages,setConfirm:fn=>confirm=fn,setFail:()=>fail=true}
}
test('select all spans pages and changing zone clears management and selection',async()=>{
 const {s,route}=setup();s.rows.value=Array.from({length:25},(_,i)=>({id:i+1,assigned:1}));s.startBatch('publish');s.selectAll(true)
 assert.equal(s.selectedIds.value.length,25);s.page.value=2;assert.equal(s.allSelected.value,true)
 route.name='consumeProduct';await vue.nextTick();assert.equal(s.managementMode.value,null);assert.equal(s.selectedIds.value.length,0)
})
for(const action of ['trash','publish']) test(`batch ${action} submits exact current-zone selection`,async()=>{
 const {s,calls}=setup();s.startBatch(action);s.selectProduct(7,true);s.selectProduct(9,true);s.selectProduct(7,false);await s.submitBatch()
 assert.equal(calls[0].url,`/p0/zones/REBUY/products/batch-${action}`);assert.deepEqual(Array.from(calls[0].data.ids),[9]);assert.equal(s.managementMode.value,null)
})
test('cancel and navigation during confirmation never submit a mutation',async()=>{
 const {s,calls,route,setConfirm}=setup();s.startBatch('trash');s.selectProduct(7,true);setConfirm(async()=>{throw 'cancel'});await s.submitBatch();assert.equal(calls.length,0)
 setConfirm(async()=>{route.name='consumeProduct'});await s.submitBatch();assert.equal(calls.filter(c=>c.method==='post').length,0)
})
test('server rejection displays reason and does not claim success',async()=>{
 const {s,messages,setFail}=setup();s.startBatch('publish');s.selectProduct(7,true);setFail();await s.submitBatch();assert.ok(messages.includes('不属于当前专区'));assert.ok(!messages.some(s=>s.startsWith('已上架')))
})

