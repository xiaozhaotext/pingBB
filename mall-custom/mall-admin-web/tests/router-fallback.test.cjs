const {test}=require('node:test'),assert=require('node:assert/strict')
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),ts=require('typescript')
const vueRouter=require('vue-router')
function execute(file,dependencies) {
  const module={exports:{}}
  const context={module,exports:module.exports,console,require:name=>dependencies[name] || {default:{}}}
  vm.runInNewContext(ts.transpileModule(fs.readFileSync(path.join(__dirname,'../src',file),'utf8'),{
    compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2020,esModuleInterop:true}
  }).outputText,context)
  return module.exports
}
function setup(authenticated=true) {
  const loaded=execute('router/index.ts',{'vue-router':{...vueRouter,createWebHashHistory:vueRouter.createMemoryHistory}})
  const permission={addRouters:[],generateRoutes(){this.addRouters=[{
    path:'/financeMgr',component:{},children:[{path:'dividend',name:'financeDividend',component:{}}]
  }]}}
  const user={userInfo:{token:authenticated?'test-token':'',menus:[],username:'test'},getUserInfo:async()=>{},fedLogout(){this.userInfo.token=''}}
  execute('router/guard.ts',{
    '@/router/index':loaded,nprogress:{default:{start(){},done(){}},__esModule:true},
    '@/stores/user':{useUserStore:()=>user},'@/stores/permission':{default:()=>permission,__esModule:true}
  })
  return loaded.default
}
test('fresh authorized deep link is re-matched after dynamic route registration',async()=>{
  const router=setup();await router.push('/financeMgr/dividend?date=2026-10-17')
  assert.equal(router.currentRoute.value.name,'financeDividend')
  assert.equal(router.currentRoute.value.query.date,'2026-10-17')
})
test('unknown console URL renders a real not-found route instead of an empty view',async()=>{
  const router=setup();await router.push('/no-such-console-page')
  assert.equal(router.currentRoute.value.name,'notFound')
  assert.equal(router.currentRoute.value.matched.length,1)
})
test('fallback does not bypass login for anonymous visitors',async()=>{
  const router=setup(false);await router.push('/financeMgr/dividend')
  assert.equal(router.currentRoute.value.path,'/login')
})
