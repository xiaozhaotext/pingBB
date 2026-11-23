// Compile only this page against the deployed Vue runtime. Other deployed pages are preserved.
// Usage: node scripts/build-withdrawal-overlay.mjs <extracted live admin dist> <new output directory>
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { parse, compileScript, compileStyle } from '@vue/compiler-sfc'
import ts from 'typescript'
const root = fileURLToPath(new URL('../', import.meta.url))
const [baseline, output] = process.argv.slice(2).map(p => path.resolve(p))
if (!baseline || !output || baseline === output || fs.existsSync(output)) throw Error('Provide live baseline and an absent output directory')
const assets = path.join(baseline, 'assets')
const oldPage = fs.readdirSync(assets).find(n => /^WithdrawalSettings-.*\.js$/.test(n))
const page = fs.readFileSync(path.join(assets, oldPage), 'utf8')
const main = page.match(/from"\.\/(index-[^"]+\.js)"/)[1]
// Verified import/export contract of the deployed 2026-11-21 bundle; refuse different baselines.
if (!page.includes('B as L') || !page.includes('C as U') || !page.includes('y as k')) throw Error('Recheck deployed runtime exports before changing baseline')
const aliases = { unref:'u', defineComponent:'d', ref:'r', computed:'q', onMounted:'i', createElementBlock:'c', createElementVNode:'a', createVNode:'b', withCtx:'w', toDisplayString:'t', createTextVNode:'e', Fragment:'F', renderList:'x', createBlock:'z', createCommentVNode:'A', openBlock:'o' }
let source = fs.readFileSync(path.join(root,'src/views/finance/WithdrawalSettings.vue'),'utf8')
source = source.replace("import { ElMessage } from 'element-plus'", `import {y as ElMessage, j as ElForm, k as ElFormItem, l as ElInput, E as ElButton, v as ElAlert} from './${main}'\nimport { E as ElTable, a as ElTableColumn } from './table-column-BsGrxlrs.js'\nimport { E as ElCard } from './card-CO5ZFyFh.js'`)
source = source.replace("import { getP0, postP0 } from '@/apis/p0'", `import { B as getP0, C as postP0 } from './${main}'`)
const {descriptor} = parse(source)
const id='data-v-27b2a306'
let code=compileScript(descriptor,{id,inlineTemplate:true}).content
code=code.replace(/import\s*\{([^}]+)\}\s*from\s*["']vue["']/g, (_,names)=> {
  const mapped=names.split(',').map(n=>{
    const [original,local]=n.trim().split(/\s+as\s+/)
    if(!aliases[original]) throw Error('Unverified runtime helper: '+original)
    return aliases[original]+' as '+(local||original)
  })
  return `import {${mapped.join(',')}} from './${main}'`
})
code=ts.transpileModule(code,{compilerOptions:{module:ts.ModuleKind.ESNext,target:ts.ScriptTarget.ES2020}}).outputText
code=code.replace('export default ', 'const page = ')+`\npage.__scopeId='${id}';export default page;\n`
fs.cpSync(baseline,output,{recursive:true})
fs.writeFileSync(path.join(output,'assets',oldPage),code)
const css=compileStyle({source:descriptor.styles.map(s=>s.content).join('\n'),filename:'WithdrawalSettings.vue',id,scoped:true})
if(css.errors.length) throw css.errors[0]
const oldCss=fs.readdirSync(assets).find(n=>/^WithdrawalSettings-.*\.css$/.test(n))
fs.writeFileSync(path.join(output,'assets',oldCss),css.code)
// Give the whole import graph new URLs so no cached module can refer to the old runtime.
const rename=new Map(fs.readdirSync(path.join(output,'assets')).filter(n=>/\.(js|css)$/.test(n)).map(n=>[n,n.replace(/\.(js|css)$/, '-withdrawal20261123.$1')]))
for(const name of fs.readdirSync(path.join(output,'assets'))) {
  if(!rename.has(name))continue
  const filename=path.join(output,'assets',name)
  let contents=fs.readFileSync(filename,'utf8')
  for(const [a,b] of rename) contents=contents.replaceAll(a,b)
  fs.writeFileSync(path.join(output,'assets',rename.get(name)),contents)
  fs.unlinkSync(filename)
}
let html=fs.readFileSync(path.join(output,'index.html'),'utf8')
for(const [a,b] of rename) html=html.replaceAll(a,b)
fs.writeFileSync(path.join(output,'index.html'),html)
console.log('Withdrawal page compiled; deployed application graph preserved:',output)
