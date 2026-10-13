<script setup lang="ts">
import { computed } from 'vue'

// 订单信息由父组件传入；这里只展示订单自身的真实节点，不再伪造承运商扫描记录。
const props = defineProps({
  modelValue: Boolean,
  order: {
    type: Object as () => object | null,
    default: null,
  },
})

const emit = defineEmits(['update:modelValue'])

const visible = computed({
  get() {
    return props.modelValue
  },
  set(value: boolean) {
    emit('update:modelValue', value)
  },
})

const text = (value: unknown) =>
  value === null || value === undefined || value === '' ? '暂无' : String(value)

const timeline = computed(() => {
  const order = props.order as unknown as Record<string, unknown> | null
  if (!order) {
    return [] as { name: string; time: string }[]
  }
  const steps: { name: string; time: string }[] = []
  if (order.createTime) {
    steps.push({ name: '订单已提交', time: text(order.createTime) })
  }
  if (order.paymentTime) {
    steps.push({ name: '订单付款成功', time: text(order.paymentTime) })
  }
  if (order.deliveryTime) {
    steps.push({
      name: '平台已发货：' + text(order.deliveryCompany) + ' ' + text(order.deliverySn),
      time: text(order.deliveryTime),
    })
  }
  if (order.receiveTime) {
    steps.push({ name: '买家已收货', time: text(order.receiveTime) })
  }
  return steps
})
</script>

<template>
  <el-dialog title="订单跟踪" v-model="visible" width="40%">
    <el-steps
      v-if="timeline.length"
      direction="vertical"
      :active="timeline.length"
      finish-status="success"
      space="50px"
    >
      <el-step
        v-for="item in timeline"
        :key="item.name"
        :title="item.name"
        :description="item.time"
      ></el-step>
    </el-steps>
    <el-empty v-else description="该订单暂无发货信息"></el-empty>
    <el-alert
      type="info"
      :closable="false"
      title="承运商轨迹查询尚未接入；如需逐条扫描记录，请按上面的运单号到物流公司官网或开放平台查询。"
    ></el-alert>
  </el-dialog>
</template>

<style></style>
