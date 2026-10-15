const {test}=require('node:test');const assert=require('node:assert/strict');
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const {createRequire}=require('node:module');
const req=createRequire(path.resolve(__dirname,'../../mall-admin-web/package.json'));
const ts=req('typescript'),vue=req('vue'),{parse}=req('@vue/compiler-sfc');
const {renderToString}=req('@vue/server-renderer');
const appPath=path.resolve(__dirname,'../src/pages/member/dividend.vue');
function page(api={}) {
 const sfc=parse(fs.readFileSync(appPath,'utf8')).descriptor;
 const source=sfc.scriptSetup.content.replace(/^import .*$/gm,'');
 const code=ts.transpileModule(source+'\nmodule.exports={info,records,loadError,page,hasMore,loadInfo,loadRecords,loadMore,fmt}',{compilerOptions:{target:ts.ScriptTarget.ES2020,module:ts.ModuleKind.CommonJS}}).outputText;
 const calls=[];const context={module:{exports:{}},ref:vue.ref,onMounted(){},getDividendInfoAPI:async()=>({data:{identityName:'合伙人',shares:1,capAmount:2400,capBase:1200,capPerReferral:1200,capMax:12000,referralCount:1,distributionEnabled:false}}),getDividendRecordsAPI:async p=>{calls.push({...p});return {data:[]}},...api};
 vm.runInNewContext(code,context);return {...context.module.exports,calls,template:sfc.template.content};
}
test('mobile loads configured cap and renders identity, disabled state and records',async()=>{
 const p=page();await p.loadInfo();await p.loadRecords(true);
 const html=await renderToString(vue.createSSRApp({setup:()=>p,template:p.template}));
 for(const text of ['我的分红','合伙人','2400.00','12000.00','每日分红发放当前未启用','暂无分红记录'])assert.ok(html.includes(text),text);
});
test('mobile record pagination appends next twenty rows',async()=>{
 const p=page({getDividendRecordsAPI:async({page})=>({data:Array.from({length:page===1?20:1},(_,i)=>({stat_date:'2026-10-16',shares:1,dividend_amount:i+page}))})});
 await p.loadRecords(true);assert.equal(p.hasMore.value,true);p.loadMore();await new Promise(setImmediate);
 assert.equal(p.records.value.length,21);assert.equal(p.hasMore.value,false);assert.equal(p.page.value,2);
});
test('mobile info failure displays explicit error',async()=>{
 const p=page({getDividendInfoAPI:async()=>{throw Error('offline')}});await p.loadInfo();assert.match(p.loadError.value,/加载失败/);
});
test('reproduces record failure being shown as empty history without an error',async()=>{
 const p=page({getDividendRecordsAPI:async()=>{throw Error('offline')}});await p.loadRecords(true);
 assert.equal(p.records.value.length,0);assert.equal(p.loadError.value,'');
});
test('reproduces failed load-more advancing page and skipping failed page on retry',async()=>{
 const calls=[];const p=page({getDividendRecordsAPI:async({page})=>{calls.push(page);if(page===2)throw Error('offline');return {data:Array(20).fill({})}}});
 await p.loadRecords(true);p.loadMore();await new Promise(setImmediate);p.loadMore();await new Promise(setImmediate);
 assert.deepEqual(calls,[1,2,3]);
});
test('admin dividend section loads pool data and refreshes via its actual component logic',async()=>{
 const file=path.resolve(__dirname,'../../mall-admin-web/src/views/p0/P0Workbench.vue');const sfc=parse(fs.readFileSync(file,'utf8')).descriptor;
 const source=sfc.scriptSetup.content.replace(/^import .*$/gm,'');const pending=[],calls=[];
 const fixture={poolStats:{pool_balance:600,total_in:1000,total_out:400},poolTodayIn:{today_in:1000},dailyDetails:[{total_shares:4,total_amount:400,recipients:2,per_share_amount:100}]};
 const context={module:{exports:{}},...vue,useRoute:()=>({name:'financeDividend',meta:{section:'p0Dividend'}}),watch:(_,f)=>pending.push(f()),getP0:async p=>{calls.push(p);return {data:fixture}},ElMessage:{},ElMessageBox:{}};
 vm.runInNewContext(ts.transpileModule(source+'\nmodule.exports={load,poolStats,poolTodayIn,data,section,money}',{compilerOptions:{target:ts.ScriptTarget.ES2020,module:ts.ModuleKind.CommonJS}}).outputText,context);
 await Promise.all(pending);const p=context.module.exports;assert.equal(p.poolStats.value.pool_balance,600);assert.equal(p.poolTodayIn.value.today_in,1000);assert.equal(p.money(p.poolStats.value.pool_balance),'600.00');
 await p.load();assert.deepEqual(calls,['dividend','dividend']);
 for(const text of ['分红池','当前余额（元）','累计注入（元）','累计发放（元）','今日注入（元）'])assert.ok(sfc.template.content.includes(text));
});
