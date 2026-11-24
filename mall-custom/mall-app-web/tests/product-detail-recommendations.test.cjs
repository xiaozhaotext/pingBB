const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),ts=require('typescript')
const source=fs.readFileSync(require('node:path').join(__dirname,'../src/pages/product/product.vue'),'utf8')
const body=source.slice(source.indexOf('async function loadRecommendations(){'),source.indexOf('\nconst openProductSearch'))
function page(tree,zone='PICK',emptyFirst=false){
 const calls=[],context={zoneCode:{value:zone},membershipRoute:true,product:{value:{id:1,productCategoryId:58}},recommendLoading:{value:false},recommendError:{value:false},recommendProducts:{value:[]},recommendationLabel:{value:''},getMallCategoryTreeAPI:async selected=>{assert.equal(selected,zone);return{data:tree}},searchProductListAPI:async params=>{calls.push(params);if(!tree.length&&params.productCategoryId)throw Error('out-of-scope category');return{data:{list:emptyFirst&&calls.length===1?[]:[{id:2,publishStatus:1}]}}}}
 vm.runInNewContext(ts.transpileModule(body,{compilerOptions:{target:ts.ScriptTarget.ES2020}}).outputText,context)
 return{context,calls}
}
test('hidden activity category is omitted and recommendations remain in PICK',async()=>{const p=page([]);await p.context.loadRecommendations();assert.equal(p.calls.length,1);assert.equal(p.calls[0].zone,'PICK');assert.equal(p.calls[0].productCategoryId,undefined);assert.equal(p.context.recommendError.value,false)})
test('visible descendant category keeps same-category recommendations in its zone',async()=>{const p=page([{id:7,children:[{id:58}]}],'CONSUME');await p.context.loadRecommendations();assert.equal(p.calls[0].productCategoryId,58);assert.equal(p.calls[0].zone,'CONSUME');assert.equal(p.context.recommendProducts.value.length,1)})
test('empty category results fall back within the original zone',async()=>{const p=page([{id:58}],'REBUY',true);await p.context.loadRecommendations();assert.equal(p.calls.length,2);assert.equal(p.calls[1].zone,'REBUY');assert.equal(p.calls[1].productCategoryId,undefined);assert.equal(p.context.recommendError.value,false)})
