const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),ts=require('typescript'),path=require('node:path')
const moduleValue={exports:{}};vm.runInNewContext(ts.transpileModule(fs.readFileSync(path.join(__dirname,'../src/utils/productPromotion.ts'),'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS}}).outputText,{module:moduleValue,exports:moduleValue.exports})
const {promotionTips,displayedSkuPrice}=moduleValue.exports
test('zero and invalid tiers are not advertised as a real promotion',()=>{
 assert.match(promotionTips({product:{promotionType:4},productFullReductionList:[{fullPrice:0,reducePrice:0}]})[0],/暂无有效/)
 assert.match(promotionTips({product:{promotionType:4},productFullReductionList:[{fullPrice:100,reducePrice:10}]})[0],/同款同专区满100元减10元/)
})
test('membership price is not falsely advertised as a supported discount',()=>assert.match(promotionTips({product:{promotionType:2}})[0],/尚未接入结算/))
test('expired special price and fixed membership route use original SKU price',()=>{
 const p={price:100,promotionType:1,promotionPrice:80,promotionEndTime:'2020-01-01T00:00:00Z'}
 assert.equal(displayedSkuPrice(p,{price:100}),100)
 assert.equal(displayedSkuPrice({...p,promotionEndTime:undefined},{price:100,promotionPrice:70}),70)
 assert.equal(displayedSkuPrice({...p,promotionEndTime:undefined},{price:100},true),100)
 assert.equal(displayedSkuPrice({price:100,promotionType:1,promotionPrice:80},{price:90,promotionPrice:null}),80)
})
