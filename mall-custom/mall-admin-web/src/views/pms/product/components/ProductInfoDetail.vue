<script setup lang="ts">
import { ref, onMounted, watch, inject, type Ref } from 'vue'
import { ElMessage, type FormInstance } from 'element-plus'
import { getProductCategoryListWithChildrenAPI } from '@/apis/productCate'
import SingleUpload from '@/components/Upload/singleUpload.vue'
import type { PmsProductParam } from '@/types/product'
import type { ElCascaderDataVo } from '@/types/common'

// 定义属性
const props = defineProps({
  isEdit: {
    type: Boolean,
    default: false
  }
})

// 定义事件
const emit = defineEmits(['next-step'])

// 获取跨层传递的数据
const compProductParam = inject('product-key') as Ref<PmsProductParam>

// 级联选择器中当前选中的商品分类，结构为：[父分类ID,分类ID]
const selectProductCateValue = ref<number[]>([])
// 级联选择器中的商品分类数据
const productCateOptions = ref<ElCascaderDataVo[]>([])
// 获取商品分类列表数据
const getProductCateList = async () => {
  const res = await getProductCategoryListWithChildrenAPI()
  const list = res.data
  productCateOptions.value = list.map(item => ({
    label: item.name,
    value: item.id!,
    children: item.children?.map(it => ({ label: it.name, value: it.id! }))
  }))
}

// 初始化数据
onMounted(async () => {
  await getProductCateList()
  if (props.isEdit) {
    handleEditCreated()
  }
})

// 编辑状态初始化标记位
const hasEditCreated = ref(false)

// 表单引用
const productInfoForm = ref<FormInstance>()
// 表单验证规则
const rules = {
  name: [
    { required: true, message: '请输入商品名称', trigger: 'blur' },
    { min: 2, max: 140, message: '长度在 2 到 140 个字符', trigger: 'blur' }
  ],
  subTitle: [{ required: true, message: '请输入商品副标题', trigger: 'blur' }],
  productCategoryId: [{ required: true, message: '请选择商品分类', trigger: 'blur' }],
  description: [{ required: true, message: '请输入商品介绍', trigger: 'blur' }],
  requiredProp: [{ required: true, message: '该项为必填项', trigger: 'blur' }]
}

// 监听选择分类变化
watch(selectProductCateValue, (newValue) => {
  if (newValue && newValue.length >= 1) {
    compProductParam.value.productCategoryId = newValue[newValue.length - 1]
    compProductParam.value.productCategoryName = getCateNameById(compProductParam.value.productCategoryId!)
  } else {
    compProductParam.value.productCategoryId = undefined
    compProductParam.value.productCategoryName = undefined
  }
})


// 方法定义
const handleEditCreated = () => {
  if (compProductParam.value.productCategoryId) {
    if (compProductParam.value.cateParentId) selectProductCateValue.value.push(compProductParam.value.cateParentId)
    selectProductCateValue.value.push(compProductParam.value.productCategoryId)
  }
  hasEditCreated.value = true
}

// 根据分类ID获取分类名称
const getCateNameById = (id: number) => {
  for (const item of productCateOptions.value) {
    if (item.value === id) return item.label
    const child = item.children?.find(child => child.value === id)
    if (child) {
      return child.label
    }
  }
  return undefined
}

// 下一步按钮出来事件
const handleNext = async () => {
  if (productInfoForm.value) {
    try {
      const valid = await productInfoForm.value.validate()
      if (valid) {
        emit('next-step')
      }
    } catch {
      ElMessage({
        message: '验证失败',
        type: 'error',
        duration: 1000
      })
    }
  }
}

</script>

<template>
  <div style="margin-top: 50px">
    <el-form :model="compProductParam" :rules="rules" ref="productInfoForm" label-width="120px"
      class="form-inner-container">
      <el-form-item label="商品分类：" prop="productCategoryId">
        <el-cascader v-model="selectProductCateValue" :options="productCateOptions" :props="{ checkStrictly: true }">
        </el-cascader>
      </el-form-item>
      <el-form-item label="商品名称：" prop="name">
        <el-input v-model="compProductParam.name"></el-input>
      </el-form-item>
      <el-form-item label="副标题：" prop="subTitle">
        <el-input v-model="compProductParam.subTitle"></el-input>
      </el-form-item>
      <el-form-item label="商品品牌：">
        <el-input v-model="compProductParam.brandName" maxlength="64" clearable placeholder="自定义输入品牌（选填）" @input="compProductParam.brandId = undefined" />
      </el-form-item>
      <el-form-item label="商品介绍：">
        <el-input :autoSize="true" v-model="compProductParam.description" type="textarea"
          placeholder="请输入内容"></el-input>
      </el-form-item>
      <el-form-item v-if="compProductParam.zoneCode === 'PICK'" label="商品图片：">
        <single-upload v-model="compProductParam.pic" />
        <div style="width:100%;color:#999;font-size:12px;line-height:1.6;margin-top:4px">
          上传商品主图（JPG/PNG/WebP/GIF，不超过 10MB）。自选区商城前台会展示这张图，建议先传图再进入下一步
        </div>
      </el-form-item>
      <el-form-item label="商品货号：">
        <el-input v-model="compProductParam.productSn"></el-input>
      </el-form-item>
      <el-form-item label="商品售价：">
        <el-input v-model="compProductParam.price"></el-input>
      </el-form-item>
      <el-form-item label="市场价：">
        <el-input v-model="compProductParam.originalPrice"></el-input>
      </el-form-item>
      <el-form-item label="商品库存：">
        <el-input v-model="compProductParam.stock"></el-input>
      </el-form-item>
      <el-form-item label="计量单位：">
        <el-input v-model="compProductParam.unit"></el-input>
      </el-form-item>
      <el-form-item label="商品重量：">
        <el-input v-model="compProductParam.weight" style="width: 300px"></el-input>
        <span style="margin-left: 20px">克</span>
      </el-form-item>
      <el-form-item label="排序">
        <el-input v-model="compProductParam.sort"></el-input>
      </el-form-item>
      <el-form-item>
        <div style="width: 100%;text-align: center;">
          <el-button type="primary" @click="handleNext()">下一步，填写商品促销</el-button>
        </div>
      </el-form-item>
    </el-form>
  </div>
</template>

<style scoped></style>
