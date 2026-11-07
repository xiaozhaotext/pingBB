<script setup lang="ts">
import { inject, type Ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { PmsProductParam } from '@/types/product'

const props = defineProps<{ isEdit?: boolean; prevLabel?: string }>()
const emit = defineEmits(['prev-step', 'finish-commit'])
const product = inject('product-key') as Ref<PmsProductParam>

function finish() {
  if (!product.value.zoneCode) {
    ElMessage.warning('请选择商品所属专区')
    return
  }
  if (product.value.zoneCode === 'PICK' && product.value.productCategoryName !== '旅游路线') {
    ElMessage.warning(`自选区商品必须选择「旅游路线」分类，当前分类为「${product.value.productCategoryName || '未选择'}」。请点击「上一步」返回第 1 步「填写商品信息」修改商品分类后，再回到这里提交`)
    return
  }
  if (product.value.zoneCode === 'PICK') {
    if (Number(product.value.price) !== 3999) {
      ElMessage.warning('自选区商品统一售价 3999 元，请返回第 1 步把售价改为 3999 元')
      return
    }
    const skus = product.value.skuStockList || []
    if (!skus.length || skus.some(s => Number(s.price) !== 3999)) {
      ElMessage.warning('自选区商品规格未正确生成（规格价格须为 3999 元），请返回上一步后重新进入本页再提交')
      return
    }
  }
  emit('finish-commit', props.isEdit)
}
</script>

<template>
  <div class="zone-relation">
    <el-form label-width="120px">
      <el-form-item label="商品所属专区" required>
        <strong>{{ { PICK: '自选区', REBUY: '复购区', CONSUME: '消费区' }[product.zoneCode || 'PICK'] }}</strong>
      </el-form-item>
      <el-alert title="商品自动归属当前进入的专区。自选区商品需为旅游路线，售价和所有规格均为3999元；规格由系统自动生成，无需手动填写。商品还需上架才会在商城显示。" type="info" :closable="false" />
      <el-form-item class="actions">
        <el-button @click="emit('prev-step')">{{ prevLabel || '上一步，填写商品属性' }}</el-button>
        <el-button type="primary" @click="finish">完成，提交商品</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<style scoped>
.zone-relation { margin: 50px auto 0; max-width: 800px; }
.actions { margin-top: 28px; }
</style>
