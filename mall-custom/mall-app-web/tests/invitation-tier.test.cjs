const {test}=require('node:test'),assert=require('node:assert/strict')
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),ts=require('typescript'),{parse}=require('@vue/compiler-sfc')
function page(){
  const raw=fs.readFileSync(path.join(__dirname,'../src/pages/member/invitation.vue'),'utf8')
  const source=parse(raw).descriptor.scriptSetup.content+'\nmodule.exports={invitees,paidInvitees,visitorInvitees,inviteeIdentity}'
  const context={module:{exports:{}},exports:{},require:n=>n==='vue'?require('vue'):n==='@dcloudio/uni-app'?{onLoad(){},onShow(){}}:{},uni:{getStorageSync(){}}}
  vm.runInNewContext(ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.CommonJS}}).outputText,context)
  return context.module.exports
}
test('nine paid identities plus visitor ID102 remain ten invitees, with nine paid counted',()=>{
  const p=page();p.invitees.value=Array.from({length:10},(_,i)=>({memberId:100+i,membershipTier:i===2?'VISITOR':'MEMBER'}))
  assert.equal(p.paidInvitees.value,9);assert.equal(p.visitorInvitees.value,1)
  assert.equal(p.inviteeIdentity(p.invitees.value[2].membershipTier),'游客')
  p.invitees.value[2].membershipTier='MEMBER';assert.equal(p.paidInvitees.value,10)
  assert.equal(p.inviteeIdentity(undefined),'身份待核实')
})
