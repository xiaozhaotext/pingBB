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
// el-steps的激活状态
const active = ref(0)
// 商品信息/促销/属性/关联组件显示状态
const showStatus = ref([true, false, false, false])
// 当前商品参数
const productParam = ref<PmsProductParam>(Object.assign({}, defaultProductParam))
const routeZone = (): PmsProductParam['zoneCode'] => {
  const name = String(route.name || '')
  if (name.startsWith('pick')) return 'PICK'
  if (name.startsWith('rebuy')) return 'REBUY'
  if (name.startsWith('consume')) return 'CONSUME'
  return undefined
}
const isPick = computed(() => routeZone() === 'PICK')
// 实现数据跨层传递
provide('product-key', productParam)

// 组件挂载后执行
onMounted(async () => {
  if (props.isEdit) {
    const res = await getPruductUpdateInfoAPI(Number(route.query.id))
    productParam.value = res.data
  }
  productParam.value.zoneCode = routeZone()
  if (isPick.value && !props.isEdit) {
    productParam.value.price = 3999
    productParam.value.originalPrice = 3999
    productParam.value.unit = '条'
  }
})

// 隐藏所有商品编辑组件
const hideAll = () => {
  // 将所有状态设置为false
  showStatus.value.fill(false)
}

// 上一步
const prevStep = () => {
  if (active.value > 0 && active.value < showStatus.value.length) {
    active.value = isPick.value && active.value === 2 ? 0 : active.value - 1
    hideAll()
    showStatus.value[active.value] = true
  }
}

// 下一步
const nextStep = () => {
  if (active.value < showStatus.value.length - 1) {
    active.value = isPick.value && active.value === 0 ? 2 : active.value + 1
    hideAll()
    showStatus.value[active.value] = true
  }
}

// 结束步骤，提交数据
const finishCommit = async (isEdit: boolean) => {
  await ElMessageBox.confirm('是否要提交该商品？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  })
  productParam.value.zoneCode = routeZone()
  if (isPick.value) {
    productParam.value.price = 3999
    productParam.value.originalPrice = 3999
    productParam.value.promotionType = 0
    productParam.value.promotionPrice = 0
    productParam.value.giftPoint = 0
    productParam.value.giftGrowth = 0
    productParam.value.usePointLimit = 0
    productParam.value.productAttributeValueList = []
    productParam.value.memberPriceList = []
    productParam.value.productLadderList = []
    productParam.value.productFullReductionList = []
    const existing = productParam.value.skuStockList?.[0]
    productParam.value.skuStockList = [{
      ...(existing?.id ? { id: existing.id } : {}),
      skuCode: existing?.skuCode,
      price: 3999,
      promotionPrice: 3999,
      stock: Number(productParam.value.stock || 0),
      lowStock: Number(productParam.value.lowStock || 0),
      lockStock: existing?.lockStock || 0,
      spData: '[]',
    }]
  }
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
    <el-steps :active="isPick && active === 2 ? 1 : active" finish-status="success" align-center>
      <el-step title="填写商品信息"></el-step>
      <el-step v-if="!isPick" title="填写商品促销"></el-step>
      <el-step :title="isPick ? '上传路线图片与行程' : '填写商品属性'"></el-step>
      <el-step v-if="!isPick" title="确认商品专区"></el-step>
    </el-steps>
    <product-info-detail v-show="showStatus[0]" :is-edit="isEdit" @next-step="nextStep">
    </product-info-detail>
    <product-sale-detail v-show="showStatus[1]" :is-edit="isEdit" @next-step="nextStep" @prev-step="prevStep">
    </product-sale-detail>
    <product-attr-detail v-show="showStatus[2]" v-model="productParam" :is-edit="isEdit" @next-step="nextStep" @finish-commit="finishCommit"
      @prev-step="prevStep">
    </product-attr-detail>
    <product-relation-detail v-show="showStatus[3]" v-model="productParam" :is-edit="isEdit" @prev-step="prevStep"
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
