<script setup lang="ts">
import { ref, computed, onMounted, provide, } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import ProductInfoDetail from './ProductInfoDetail.vue'
import ProductSaleDetail from './ProductSaleDetail.vue'
import ProductAttrDetail from './ProductAttrDetail.vue'
import ProductRelationDetail from './ProductRelationDetail.vue'
import { productCreateAPI, getPruductUpdateInfoAPI, productUpdateByIdAPI } from '@/apis/product'
import type { PmsProductParam } from '@/types/product'

// 获取路由对象
const route = useRoute()
const router = useRouter()

// 定义属性
const props = defineProps({
  isEdit: {
    type: Boolean,
    default: false
  }
})

// 自选区统一售价
const PICK_PRICE = 3999
const PICK_DEFAULT_STOCK = 100

// 默认商品参数
const defaultProductParam: PmsProductParam = {
  albumPics: '',
  brandName: '',
  deleteStatus: 0,
  description: '',
  detailDesc: '',
  detailHtml: '',
  detailMobileHtml: '',
  detailTitle: '',
  feightTemplateId: 0,
  flashPromotionCount: 0,
  flashPromotionId: 0,
  flashPromotionPrice: 0,
  flashPromotionSort: 0,
  giftPoint: 0,
  giftGrowth: 0,
  keywords: '',
  lowStock: 0,
  name: '',
  newStatus: 0,
  note: '',
  originalPrice: 0,
  pic: '',
  memberPriceList: [],
  productFullReductionList: [{ fullPrice: 0, reducePrice: 0 }],
  productLadderList: [{ count: 0, discount: 0, price: 0 }],
  previewStatus: 0,
  price: 0,
  productAttributeValueList: [],
  skuStockList: [],
  subjectProductRelationList: [],
  prefrenceAreaProductRelationList: [],
  zoneCode: undefined,
  productCategoryName: '',
  productSn: '',
  promotionEndTime: '',
  promotionPerLimit: 0,
  promotionStartTime: '',
  promotionType: 0,
  publishStatus: 0,
  recommandStatus: 0,
  sale: 0,
  serviceIds: '',
  sort: 0,
  stock: 0,
  subTitle: '',
  unit: '',
  usePointLimit: 0,
  verifyStatus: 0,
  weight: 0
}

const routeZone = (): PmsProductParam['zoneCode'] => {
  const name = String(route.name || '')
  if (name.startsWith('pick')) return 'PICK'
  if (name.startsWith('rebuy')) return 'REBUY'
  if (name.startsWith('consume')) return 'CONSUME'
  return undefined
}

// 自选区：单一价格 3999 元的旅游路线，不需要填写商品属性（规格/参数）
const isPick = computed(() => routeZone() === 'PICK')

// 步骤定义：自选区跳过「填写商品属性」
const steps = computed(() => {
  const base = [
    { key: 'info', title: '填写商品信息' },
    { key: 'sale', title: '填写商品促销' },
  ]
  if (!isPick.value) base.push({ key: 'attr', title: '填写商品属性' })
  base.push({ key: 'zone', title: '确认商品专区' })
  return base
})

// 当前步骤
const activeKey = ref('info')
// el-steps的激活状态
const active = computed(() => Math.max(0, steps.value.findIndex(item => item.key === activeKey.value)))
// 商品促销步骤的「下一步」按钮文案
const saleNextLabel = computed(() => (isPick.value ? '下一步，确认商品专区' : '下一步，填写商品属性'))
// 确认专区步骤的「上一步」按钮文案
const zonePrevLabel = computed(() => (isPick.value ? '上一步，填写商品促销' : '上一步，填写商品属性'))

// 当前商品参数
const productParam = ref<PmsProductParam>(Object.assign({}, defaultProductParam))

// 实现数据跨层传递
provide('product-key', productParam)

// 自选区进入「确认商品专区」前，自动补齐 3999 元规格
const ensurePickSku = () => {
  const param = productParam.value
  if (Number(param.price) !== PICK_PRICE) {
    param.price = PICK_PRICE
    ElMessage.info(`自选区商品统一售价 ${PICK_PRICE} 元，已自动填写`)
  }
  const stock = Number(param.stock) > 0 ? Number(param.stock) : PICK_DEFAULT_STOCK
  param.stock = stock
  const skus = param.skuStockList || []
  if (!skus.length) {
    param.skuStockList = [{
      skuCode: `PICK${Date.now()}`,
      price: PICK_PRICE,
      stock,
      lowStock: 0,
      pic: param.pic || '',
      sale: 0,
      lockStock: 0,
      spData: '[]'
    }]
  } else {
    param.skuStockList = skus.map(item => ({
      ...item,
      price: PICK_PRICE,
      stock: Number(item.stock) > 0 ? Number(item.stock) : stock,
      spData: item.spData || '[]'
    }))
  }
}

// 组件挂载后执行
onMounted(async () => {
  if (props.isEdit) {
    const res = await getPruductUpdateInfoAPI(Number(route.query.id))
    productParam.value = res.data
  }
  productParam.value.zoneCode = routeZone()
})

// 上一步
const prevStep = () => {
  const index = active.value
  if (index > 0) activeKey.value = steps.value[index - 1]!.key
}

// 下一步
const nextStep = () => {
  const index = active.value
  if (index >= steps.value.length - 1) return
  const next = steps.value[index + 1]!
  if (next.key === 'zone' && isPick.value) ensurePickSku()
  activeKey.value = next.key
}

// 结束步骤，提交数据
const finishCommit = async (isEdit: boolean) => {
  await ElMessageBox.confirm('是否要提交该商品？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  })
  productParam.value.zoneCode = routeZone()
  if (isEdit) {
    await productUpdateByIdAPI(Number(route.query.id), productParam.value)
  } else {
    await productCreateAPI(productParam.value)
  }
  ElMessage.success(productParam.value.publishStatus === 1 ? '商品已保存并上架到所属专区' : '商品已保存到所属专区；请在列表中上架后展示')
  const zoneRoute = { PICK: 'pickProduct', REBUY: 'rebuyProduct', CONSUME: 'consumeProduct' } as const
  const selected = productParam.value.zoneCode
  if (selected) router.push({ name: zoneRoute[selected] })
  else router.back()
}
</script>

<template>
  <el-card class="form-container" shadow="never">
    <el-steps :active="active" finish-status="success" align-center>
      <el-step v-for="step in steps" :key="step.key" :title="step.title"></el-step>
    </el-steps>
    <product-info-detail v-show="activeKey === 'info'" :is-edit="isEdit" @next-step="nextStep">
    </product-info-detail>
    <product-sale-detail v-show="activeKey === 'sale'" :is-edit="isEdit" :next-label="saleNextLabel" @next-step="nextStep" @prev-step="prevStep">
    </product-sale-detail>
    <product-attr-detail v-if="!isPick" v-show="activeKey === 'attr'" v-model="productParam" :is-edit="isEdit" @next-step="nextStep"
      @prev-step="prevStep">
    </product-attr-detail>
    <product-relation-detail v-show="activeKey === 'zone'" v-model="productParam" :is-edit="isEdit" :prev-label="zonePrevLabel" @prev-step="prevStep"
      @finish-commit="finishCommit">
    </product-relation-detail>
  </el-card>
</template>

<style>
.form-container {
  width: 960px;
}

.form-inner-container {
  width: 800px;
}
</style>
