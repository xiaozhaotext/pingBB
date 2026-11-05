const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),ts=require('typescript'),path=require('node:path')
const moduleValue={exports:{}};vm.runInNewContext(ts.transpileModule(fs.readFileSync(path.join(__dirname,'../src/utils/productPromotion.ts'),'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS}}).outputText,{module:moduleValue,exports:moduleValue.exports})
const {promotionValidation}=moduleValue.exports
test('the screenshot zero threshold cannot be published as a reduction',()=>{
 assert.match(promotionValidation({promotionType:4,productFullReductionList:[{fullPrice:0,reducePrice:0}]}),/大于0/)
 assert.equal(promotionValidation({promotionType:4,productFullReductionList:[{fullPrice:100,reducePrice:10}]}),'')
})
test('invalid discounts, duplicate thresholds and more than cent precision are rejected',()=>{
 for(const row of [{fullPrice:10,reducePrice:11},{fullPrice:10,reducePrice:0.001}])assert.notEqual(promotionValidation({promotionType:4,productFullReductionList:[row]}),'')
 assert.match(promotionValidation({promotionType:4,productFullReductionList:[{fullPrice:10,reducePrice:1},{fullPrice:10,reducePrice:2}]}),/重复/)
 assert.notEqual(promotionValidation({promotionType:3,productLadderList:[{count:0,discount:0}]}),'')
 assert.equal(promotionValidation({promotionType:3,productLadderList:[{count:3,discount:0.8}]}),'')
})
