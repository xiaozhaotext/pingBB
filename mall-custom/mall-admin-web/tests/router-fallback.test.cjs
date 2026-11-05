const {test}=require('node:test'),assert=require('node:assert/strict')
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),ts=require('typescript')
const vueRouter=require('vue-router')
function execute(file,dependencies,expose='') {
  const module={exports:{}}
  const context={module,exports:module.exports,console,require:name=>dependencies[name] || {default:{}}}
  const raw=fs.readFileSync(path.join(__dirname,'../src',file),'utf8')
  const source=file.endsWith('.vue')?require('@vue/compiler-sfc').parse(raw).descriptor.scriptSetup.content:raw
  vm.runInNewContext(ts.transpileModule(source+expose,{
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

test('desktop displays dashboard, notice management and cache with the existing notice permission and old URL', () => {
  const loaded = execute('router/index.ts', { 'vue-router': { ...vueRouter, createWebHashHistory: vueRouter.createMemoryHistory } })
  const pinia = require('pinia')
  pinia.setActivePinia(pinia.createPinia())
  const permissions = execute('stores/permission.ts', { pinia, vue: require('vue'), '@/router/index': loaded })
  const store = permissions.usePermissionStore()
  const names = ['serviceDesktop', 'serviceDesktopData', 'memberMgrNotices', 'desktopClearCache', 'serviceDesktopUser']
  store.generateRoutes({ username: 'test', menus: names.map((name, index) => ({
    name, hidden: name === 'serviceDesktopUser' ? 1 : 0, sort: 100 - index,
  })) })
  const desktop = store.addRouters.find(route => route.name === 'serviceDesktop')
  assert.deepEqual(Array.from(desktop.children.filter(route => !route.hidden), route => route.meta.title), ['大数据界面', '公告管理', '清除缓存'])
  loaded.default.addRoute(desktop)
  assert.equal(loaded.default.resolve('/desktop/notices').name, 'memberMgrNotices')
  assert.equal(loaded.default.resolve('/memberMgr/operations/notices').name, 'memberMgrNotices')
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
test('connection recovery clears the local session without waiting for an unavailable API',()=>{
  let cleared=false,target
  const page=execute('views/normal/ConnectionError.vue',{
    'vue-router':{useRoute:()=>({query:{redirect:'/memberMgr/management/list'}}),useRouter:()=>({replace:value=>target=value})},
    '@/stores/user':{useUserStore:()=>({fedLogout:()=>cleared=true})}
  },'\nmodule.exports={relogin}')
  page.relogin();assert.equal(cleared,true);assert.equal(target.path,'/login');assert.equal(target.query.redirect,'/memberMgr/management/list')
})
test('connection recovery rejects external redirects and authentication loops',()=>{
  for(const value of ['//other.example/path','https://other.example','/login?redirect=/home','/connection-error']){
    let target;const page=execute('views/normal/ConnectionError.vue',{
      'vue-router':{useRoute:()=>({query:{redirect:value}}),useRouter:()=>({replace:value=>target=value})},
      '@/stores/user':{useUserStore:()=>({fedLogout(){}})}
    },'\nmodule.exports={relogin}')
    page.relogin();assert.equal(target.query.redirect,'/home')
  }
})
