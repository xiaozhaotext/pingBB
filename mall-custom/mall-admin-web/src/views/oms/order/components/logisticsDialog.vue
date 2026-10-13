<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { getOrderLogisticsAPI } from '@/apis/order'
import type { OmsLogisticsTrackResult } from '@/types/order'

// 订单信息由父组件传入；展示订单自身的真实节点，并按真实运单号查询承运商轨迹。
const props = defineProps({
  modelValue: Boolean,
  order: {
    type: Object as () => Record<string, unknown> | null,
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

const loading = ref(false)
const track = ref<OmsLogisticsTrackResult | null>(null)

const text = (value: unknown) =>
  value === null || value === undefined || value === '' ? '暂无' : String(value)

const orderTimeline = computed(() => {
  const order = props.order
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

const loadTrack = async () => {
  const order = props.order
  const deliverySn = order?.deliverySn ? String(order.deliverySn) : ''
  if (!deliverySn) {
    track.value = null
    return
  }
  loading.value = true
  try {
    const res = await getOrderLogisticsAPI({
      deliveryCompany: order?.deliveryCompany ? String(order.deliveryCompany) : '',
      deliverySn,
      receiverPhone: order?.receiverPhone ? String(order.receiverPhone) : undefined,
    })
    track.value = res.data
  } catch (error) {
    track.value = null
  } finally {
    loading.value = false
  }
}

watch(
  () => [props.modelValue, props.order],
  () => {
    if (props.modelValue) {
      loadTrack()
    }
  },
  { immediate: true },
)
</script>

<template>
  <el-dialog title="订单跟踪" v-model="visible" width="45%">
    <el-steps
      v-if="orderTimeline.length"
      direction="vertical"
      :active="orderTimeline.length"
      finish-status="success"
      space="50px"
    >
      <el-step
        v-for="item in orderTimeline"
        :key="item.name"
        :title="item.name"
        :description="item.time"
      ></el-step>
    </el-steps>
    <el-empty v-else description="该订单暂无发货信息"></el-empty>

    <el-divider content-position="left">承运商轨迹</el-divider>
    <el-skeleton v-if="loading" :rows="2" animated></el-skeleton>
    <template v-else-if="track">
      <el-alert
        v-if="!track.available"
        type="info"
        :closable="false"
        :title="track.message || '物流轨迹查询通道未接入'"
        description="可按上面的运单号到物流公司官网或开放平台查询；配置 mall.logistics.* 后可在此自动展示。"
      ></el-alert>
      <el-alert
        v-else-if="!track.success"
        type="warning"
        :closable="false"
        :title="track.message || '物流轨迹查询失败'"
      ></el-alert>
      <template v-else>
        <el-tag v-if="track.stateText" type="success" class="track-state">{{ track.stateText }}</el-tag>
        <el-empty v-if="!track.nodes.length" description="承运商暂无轨迹"></el-empty>
        <el-timeline v-else>
          <el-timeline-item
            v-for="(node, index) in track.nodes"
            :key="index"
            :timestamp="text(node.time)"
            placement="top"
          >
            {{ node.context }}
          </el-timeline-item>
        </el-timeline>
      </template>
    </template>
    <el-empty v-else description="该订单暂无运单号，无法查询承运商轨迹"></el-empty>
  </el-dialog>
</template>

<style>
.track-state {
  margin-bottom: 12px;
}
</style>