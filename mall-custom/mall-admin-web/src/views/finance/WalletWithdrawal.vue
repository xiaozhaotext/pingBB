<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getP0, postP0 } from '@/apis/p0'
type Row = {id:number;member_id:number;username:string;gross_cents:number;fee_cents:number;net_cents:number;recipient_name:string;channel:string;bank_name:string;bank_branch:string;recipient_account:string;created_at:string;payout_reference:string}
const status=ref('PENDING'),rows=ref<Row[]>([]),loading=ref(false)
const money=(cents:number)=>(Number(cents)/100).toFixed(2)
const channelName=(channel:string)=>({WECHAT:'微信',ALIPAY:'支付宝',BANK:'银行卡'}[channel]||channel||'—')
const accountLabel=(row:Row)=>{
  if(row.channel==='BANK')return `银行卡 ${row.recipient_account}（${row.bank_name||'-'}${row.bank_branch?' · '+row.bank_branch:''}）`
  if(row.channel==='ALIPAY')return `支付宝账号 ${row.recipient_account}`
  if(row.channel==='WECHAT')return `微信号 ${row.recipient_account}`
  return row.recipient_account
}
async function load(){loading.value=true;try{rows.value=(await getP0<Row[]>(`wallet-withdrawals?status=${status.value}`)).data||[]}finally{loading.value=false}}
async function decide(row:Row,paid:boolean){
  const noteResult=await ElMessageBox.prompt(paid?'请先完成手动打款，再填写处理说明':'填写拒绝原因','处理提现',{inputPattern:/.{2,255}/,inputErrorMessage:'请输入2至255字'})
  let payoutReference=''
  if(paid){const result=await ElMessageBox.prompt('请输入实际打款凭证号，确认后该申请将标记为已打款','确认打款',{inputPattern:/.+/,inputErrorMessage:'凭证号不能为空'});payoutReference=result.value}
  await postP0(`wallet-withdrawals/${row.id}/decision`,{paid,note:noteResult.value,payoutReference})
  ElMessage.success(paid?'已记录手动打款':'已拒绝并退回余额');await load()
}
onMounted(load)
</script>
<template>
  <main class="page"><h1>余额提现审核</h1><p>确认实际转账完成后，填写打款凭证号；拒绝申请会自动退回账户余额。</p>
    <el-radio-group v-model="status" @change="load"><el-radio-button value="PENDING">待处理</el-radio-button><el-radio-button value="PAID">已打款</el-radio-button><el-radio-button value="REJECTED">已拒绝</el-radio-button></el-radio-group>
    <el-table :data="rows" v-loading="loading" style="margin-top:16px" border>
      <el-table-column prop="id" label="申请ID" width="90"/><el-table-column prop="member_id" label="会员ID" width="90"/><el-table-column prop="username" label="会员" width="130"/>
      <el-table-column label="申请/手续费/实打金额" min-width="190"><template #default="{row}">{{money(row.gross_cents)}} / {{money(row.fee_cents)}} / {{money(row.net_cents)}}</template></el-table-column>
      <el-table-column prop="recipient_name" label="真实姓名" width="110"/><el-table-column label="收款方式" width="100"><template #default="{row}">{{channelName(row.channel)}}</template></el-table-column>
      <el-table-column label="收款账户信息" min-width="320"><template #default="{row}">{{accountLabel(row)}}</template></el-table-column><el-table-column prop="payout_reference" label="打款凭证号" min-width="140"/>
      <el-table-column prop="created_at" label="申请时间" min-width="170"/>
      <el-table-column v-if="status==='PENDING'" label="操作" width="190"><template #default="{row}"><el-button link type="success" @click="decide(row,true)">确认已打款</el-button><el-button link type="danger" @click="decide(row,false)">拒绝</el-button></template></el-table-column>
    </el-table>
  </main>
</template>
<style scoped>.page{padding:24px}.page h1{color:#285a62}.page p{color:#667b7b}</style>
