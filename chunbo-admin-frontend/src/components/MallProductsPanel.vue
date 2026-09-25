<template>
  <div class="tab-pane">
    <div class="pane-header">
      <div>
        <h3>🛍️ 春播健康商城商品档案、价格调优与进销存管理中心</h3>
        <span class="sub-desc">在此对便民商城商品进行上下架控制、价格修改及补货入库流水记录</span>
      </div>
      <div style="display: flex; gap: 8px;">
        <el-button v-if="currentUserRole === 'ADMIN' || currentUserRole === 'HR'" type="primary" size="small" plain :loading="inventoryExportLoading" @click="exportInventoryExcel">
          ⬇ 导出台账
        </el-button>
        <el-button type="success" size="small" @click="showAddProductDialog = true">
          + 新增商品档案
        </el-button>
        <el-button type="warning" size="small" plain @click="triggerProductImport">
          ⬆ 表格新增商品
        </el-button>
        <input ref="productImportInput" type="file" accept=".xlsx,.xls" style="display:none" @change="handleProductImport" />
      </div>
    </div>

    <!-- 指标卡 -->
    <div class="metrics-grid mb-16">
      <div class="metric-card bg-blue">
        <div class="m-label">在售药品与生活健康品</div>
        <div class="m-val">{{ mallProducts.filter(p => p.status !== 'OFF_SALE').length }} <span class="unit">款</span></div>
        <div class="m-sub">面向大众消费者提供在线采购</div>
      </div>
      <div class="metric-card bg-orange">
        <div class="m-label">已下架暂停销售品</div>
        <div class="m-val">{{ mallProducts.filter(p => p.status === 'OFF_SALE').length }} <span class="unit">款</span></div>
        <div class="m-sub">商城前台自动隐去不展示</div>
      </div>
      <div class="metric-card bg-purple">
        <div class="m-label">库存量充足品</div>
        <div class="m-val">{{ mallProducts.filter(p => (p.stock || p.stockQty || 0) > 50).length }} <span class="unit">款</span></div>
        <div class="m-sub">支持便民同城极速达</div>
      </div>
      <div class="metric-card bg-green">
        <div class="m-label">全栈进销存关联</div>
        <div class="m-val">{{ inventoryRecords.length }} <span class="unit">条</span></div>
        <div class="m-sub">出入库台账流水（补货/发货自动生成）</div>
      </div>
    </div>

    <!-- 搜索 / 删除 / 分页工具栏 -->
    <div class="batch-salary-toolbar">
      <el-input v-model="productsPager.state.search" placeholder="搜索商品名 / 类别 / 厂家 / 状态…" size="small" clearable style="width: 260px;" />
      <el-button size="small" type="danger" plain :disabled="!productSelection.length" @click="batchDeleteRows('/api/admin/mall/product/batch-delete', productSelection.map(r => r.id), '商品档案', loadMallAdminProducts)">🗑 删除选中</el-button>
      <el-pagination style="margin-left: auto;" v-model:current-page="productsPager.state.page" v-model:page-size="productsPager.state.size" :page-sizes="[10, 20, 50]" :total="productsPager.total" layout="total, sizes, prev, pager, next" size="small" />
    </div>

    <!-- 商品表格 -->
    <el-card>
      <el-table :data="productsPager.paged" stripe size="small" v-loading="mallLoading" @selection-change="productSelection = $event">
        <el-table-column type="selection" width="42" />
        <el-table-column prop="id" label="ID" width="55" />
        <el-table-column label="商品图" width="92">
          <template #default="scope">
            <el-upload
              :show-file-list="false"
              accept="image/*"
              :http-request="(o) => handleRowImageUpload(scope.row, o)"
            >
              <el-image
                v-if="scope.row.imageUrl"
                :src="scope.row.imageUrl"
                :preview-src-list="[scope.row.imageUrl]"
                preview-teleported
                fit="cover"
                style="width: 52px; height: 52px; border-radius: 6px; border: 1px solid #e2e8f0;"
              />
              <div v-else class="row-img-upload-placeholder">传图</div>
            </el-upload>
          </template>
        </el-table-column>
        <el-table-column prop="productName" label="商品名称" min-width="140">
          <template #default="scope">
            <b>{{ scope.row.productName }}</b>
          </template>
        </el-table-column>
        <el-table-column prop="category" label="分类" width="100">
          <template #default="scope">
            <el-tag size="small">{{ scope.row.category }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="specification" label="规格" width="120" />
        <el-table-column prop="retailGuidePrice" label="零售指导价" width="110">
          <template #default="scope">
            <b class="text-danger">¥{{ Number(scope.row.retailGuidePrice || 0).toFixed(2) }}</b>
          </template>
        </el-table-column>
        <el-table-column prop="wholesalePrice" label="进货成本价" width="100">
          <template #default="scope">
            <span>¥{{ Number(scope.row.wholesalePrice || 0).toFixed(2) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="stock" label="当前库存" width="90">
          <template #default="scope">
            <b :class="(scope.row.stock || scope.row.stockQty || 0) < 50 ? 'text-danger' : 'text-success'">
              {{ scope.row.stock || scope.row.stockQty || 0 }}
            </b>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="在售状态" width="90">
          <template #default="scope">
            <el-tag :type="scope.row.status === 'OFF_SALE' ? 'danger' : 'success'" size="small">
              {{ scope.row.status === 'OFF_SALE' ? '已下架' : '在售中' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="中台进销存与销售操作" width="220" fixed="right">
          <template #default="scope">
            <el-button 
              size="small" 
              :type="scope.row.status === 'OFF_SALE' ? 'success' : 'warning'" 
              link
              @click="handleToggleProductStatus(scope.row)"
            >
              {{ scope.row.status === 'OFF_SALE' ? '上架' : '下架' }}
            </el-button>
            <el-button size="small" type="primary" link @click="openPriceDialog(scope.row)">
              调价
            </el-button>
            <el-button size="small" type="success" link @click="openInboundDialog(scope.row)">
              入库补货
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 价格调整弹窗 -->
    <el-dialog v-model="showPriceDialog" title="💰 调整商品商城售价" width="400px">
      <el-form label-width="100px">
        <el-form-item label="商品名称">
          <b>{{ editingProduct.productName }}</b>
        </el-form-item>
        <el-form-item label="零售指导价" required>
          <el-input-number v-model="priceForm.retailGuidePrice" :precision="2" :step="1" :min="1" />
        </el-form-item>
        <el-form-item label="批发进价">
          <el-input-number v-model="priceForm.wholesalePrice" :precision="2" :step="1" :min="0.5" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showPriceDialog = false">取消</el-button>
        <el-button type="primary" @click="submitPriceUpdate">确认修改售价</el-button>
      </template>
    </el-dialog>

    <!-- 补货入库弹窗 -->
    <el-dialog v-model="showInboundDialog" title="📦 进销存商品补货入库" width="420px">
      <el-alert title="补货入库将自动累加商品库存，并生成一笔出入库进销存台账记录。" type="info" :closable="false" class="mb-16" />
      <el-form label-width="100px">
        <el-form-item label="补货商品">
          <b>{{ inboundProduct.productName }}</b>
        </el-form-item>
        <el-form-item label="当前在库">
          <span>{{ inboundProduct.stock || inboundProduct.stockQty || 0 }} 盒/瓶</span>
        </el-form-item>
        <el-form-item label="入库补货量" required>
          <el-input-number v-model="inboundQty" :min="10" :step="50" :max="5000" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showInboundDialog = false">取消</el-button>
        <el-button type="success" @click="submitInbound">确认入库并更新台账</el-button>
      </template>
    </el-dialog>

    <!-- 新增商品档案弹窗 -->
    <el-dialog v-model="showAddProductDialog" title="➕ 新增商城商品档案" width="500px">
      <el-form :model="newProductForm" label-width="100px">
        <el-form-item label="商品名称" required>
          <el-input v-model="newProductForm.productName" placeholder="如：连花清瘟胶囊" />
        </el-form-item>
        <el-form-item label="商品分类">
          <el-select v-model="newProductForm.category" style="width: 100%">
            <el-option label="感冒发热" value="感冒发热" />
            <el-option label="咳嗽咽痛" value="咳嗽咽痛" />
            <el-option label="肠胃消化" value="肠胃消化" />
            <el-option label="皮肤外用" value="皮肤外用" />
            <el-option label="家庭常备" value="家庭常备" />
            <el-option label="慢病用药" value="慢病用药" />
          </el-select>
        </el-form-item>
        <el-form-item label="规格">
          <el-input v-model="newProductForm.specification" placeholder="如：0.35g*24粒/盒" />
        </el-form-item>
        <el-form-item label="生产厂家">
          <el-input v-model="newProductForm.manufacturer" placeholder="如：北京同仁堂科技发展股份有限公司" />
        </el-form-item>
        <el-form-item label="零售单价" required>
          <el-input-number v-model="newProductForm.retailGuidePrice" :precision="2" :step="1" :min="1" />
        </el-form-item>
        <el-form-item label="进货成本价">
          <el-input-number v-model="newProductForm.wholesalePrice" :precision="2" :step="1" :min="0.5" />
        </el-form-item>
        <el-form-item label="初始库存">
          <el-input-number v-model="newProductForm.stock" :min="10" :step="50" />
        </el-form-item>
        <el-form-item label="商品图片">
          <div class="upload-image-zone">
            <el-upload
              class="product-img-uploader"
              :show-file-list="false"
              accept="image/*"
              :http-request="handleProductImageUpload"
            >
              <img v-if="newProductForm.imageUrl" :src="newProductForm.imageUrl" class="upload-preview-img" />
              <el-icon v-else class="upload-placeholder-icon"><Plus /></el-icon>
            </el-upload>
            <div class="upload-tips">
              <div class="tip-main">点击上传商品实拍图</div>
              <div class="tip-sub">jpg/png/webp，不超过 5MB；上传后立即在便民商城展示</div>
              <el-button v-if="newProductForm.imageUrl" link type="danger" size="small" @click="newProductForm.imageUrl = ''">移除图片</el-button>
            </div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddProductDialog = false">取消</el-button>
        <el-button type="primary" @click="submitAddProduct">保存商品档案</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { makeListPager, batchDeleteRows } from '../utils/common.js'

const props = defineProps({
  currentUserName: { type: String, default: '' },
  currentUserRole: { type: String, default: '' }
})

// ── 药品进销存台账导出 Excel（仅 ADMIN/HR 可见）──
const inventoryExportLoading = ref(false)
const exportInventoryExcel = async () => {
  inventoryExportLoading.value = true
  try {
    const res = await axios.get('/api/export/inventory', { responseType: 'blob' })
    const link = document.createElement('a')
    link.href = URL.createObjectURL(res.data)
    link.download = '药品进销存台账.xlsx'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(link.href)
    ElMessage.success('药品进销存台账导出成功')
  } catch (e) {
    ElMessage.error('导出失败：' + (e.response?.data?.message || e.message || '网络错误'))
  } finally {
    inventoryExportLoading.value = false
  }
}

// ── 商城商品管理 ──
const mallProducts = ref([])
const mallLoading = ref(false)
const showPriceDialog = ref(false)
const editingProduct = ref({})
const priceForm = ref({ retailGuidePrice: 10, wholesalePrice: 5 })
const showInboundDialog = ref(false)
const inboundProduct = ref({})
const inboundQty = ref(100)
const showAddProductDialog = ref(false)
const newProductForm = ref({
  productName: '',
  category: '',
  specification: '',
  manufacturer: '',
  retailGuidePrice: null,
  wholesalePrice: null,
  stock: null,
  imageUrl: ''
})

// 进销存台账流水（指标卡「全栈进销存关联」用）
const inventoryRecords = ref([])

// 分页 / 选择
const productsPager = makeListPager(mallProducts, ['productName', 'category', 'specification', 'manufacturer', 'status'])
const productSelection = ref([])

// 自定义上传：把商品图片 POST 到后端，成功后回填 imageUrl
const uploadingProductImg = ref(false)
const handleProductImageUpload = async (options) => {
  const formData = new FormData()
  formData.append('file', options.file)
  uploadingProductImg.value = true
  try {
    const res = await axios.post('/api/admin/mall/product/upload-image', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    if (res.data?.success && res.data.url) {
      newProductForm.value.imageUrl = res.data.url
      ElMessage.success('商品图片上传成功')
    } else {
      ElMessage.error(res.data?.message || '图片上传失败')
    }
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '图片上传失败')
  } finally {
    uploadingProductImg.value = false
  }
}

// 已有商品行内换图：上传后仅更新该商品 imageUrl
const handleRowImageUpload = async (row, options) => {
  const formData = new FormData()
  formData.append('file', options.file)
  try {
    const res = await axios.post('/api/admin/mall/product/upload-image', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    if (res.data?.success && res.data.url) {
      await axios.post('/api/admin/mall/product/image', { id: row.id, imageUrl: res.data.url })
      ElMessage.success('商品图片已更新')
      loadMallAdminProducts()
    } else {
      ElMessage.error(res.data?.message || '图片上传失败')
    }
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '图片上传失败')
  }
}

const loadMallAdminProducts = async () => {
  mallLoading.value = true
  try {
    const res = await axios.get('/api/admin/mall/products')
    if (res.data?.success) mallProducts.value = res.data.data || []
  } catch (e) {} finally { mallLoading.value = false }
}

const loadInventoryRecords = async () => {
  try {
    const res = await axios.get('/api/pharmacy/inventory-records')
    inventoryRecords.value = res.data || []
  } catch (e) {}
}

// ── 进销存：Excel 商品表批量导入（新商品建档上架；已存在同价库存累加；异价拒绝）──
const productImportInput = ref(null)
const triggerProductImport = () => {
  productImportInput.value && productImportInput.value.click()
}
const handleProductImport = async (e) => {
  const file = e.target.files && e.target.files[0]
  if (!file) return
  if (!/\.(xlsx|xls)$/i.test(file.name)) {
    ElMessage.warning('请选择 Excel 商品表（xlsx / xls）')
    e.target.value = ''
    return
  }
  const fd = new FormData()
  fd.append('file', file)
  try {
    const res = await axios.post('/api/admin/mall/product/batch-import', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
    const d = res.data
    if (d && d.success) {
      const lines = (d.details || [])
        .map(x => `<div style="margin:2px 0;">${x.success ? '✅' : '⛔'} <b>${x.productName || ''}</b>：${x.message || ''}</div>`)
        .join('')
      ElMessageBox.alert(lines || d.message, `商品表导入完成（新增 ${d.addedCount} / 累加 ${d.mergedCount} / 失败 ${d.failCount}）`, {
        dangerouslyUseHTMLString: true, confirmButtonText: '知道了'
      }).catch(() => {})
      loadMallAdminProducts()
    } else {
      ElMessage.error((d && d.message) || '商品表导入失败')
    }
  } catch (err) {
    ElMessage.error('导入失败：' + (err.response?.data?.message || err.message || '网络错误'))
  }
  e.target.value = ''
}

const handleToggleProductStatus = async (row) => {
  const nextStatus = row.status === 'OFF_SALE' ? 'ON_SALE' : 'OFF_SALE'
  try {
    const res = await axios.post('/api/admin/mall/product/status', { id: row.id, status: nextStatus })
    row.status = nextStatus
    ElMessage.success(res.data?.message || '状态已更新')
  } catch (e) { ElMessage.error('修改失败') }
}

const openPriceDialog = (row) => {
  editingProduct.value = row
  priceForm.value.retailGuidePrice = Number(row.retailGuidePrice) || 10
  priceForm.value.wholesalePrice = Number(row.wholesalePrice) || 5
  showPriceDialog.value = true
}

const submitPriceUpdate = async () => {
  try {
    await axios.post('/api/admin/mall/product/update-price', {
      id: editingProduct.value.id,
      retailGuidePrice: priceForm.value.retailGuidePrice,
      wholesalePrice: priceForm.value.wholesalePrice
    })
    ElMessage.success('售价调整成功')
    showPriceDialog.value = false
    loadMallAdminProducts()
  } catch (e) { ElMessage.error('调价失败') }
}

const openInboundDialog = (row) => {
  inboundProduct.value = row
  inboundQty.value = 100
  showInboundDialog.value = true
}

const submitInbound = async () => {
  try {
    const res = await axios.post('/api/admin/mall/product/inbound', {
      id: inboundProduct.value.id,
      quantity: inboundQty.value,
      operator: props.currentUserName
    })
    ElMessage.success(res.data?.message || '入库补货成功')
    showInboundDialog.value = false
    loadMallAdminProducts()
  } catch (e) { ElMessage.error('入库失败') }
}

const submitAddProduct = async () => {
  if (!newProductForm.value.productName.trim()) {
    ElMessage.warning('请输入商品名称')
    return
  }
  try {
    await axios.post('/api/admin/mall/product/save', newProductForm.value)
    ElMessage.success('商品档案新增成功，已上架至便民商城')
    showAddProductDialog.value = false
    newProductForm.value = {
      productName: '',
      category: '',
      specification: '',
      manufacturer: '',
      retailGuidePrice: null,
      wholesalePrice: null,
      stock: null,
      imageUrl: ''
    }
    loadMallAdminProducts()
  } catch (e) { ElMessage.error(e.response?.data?.message || '新增失败') }
}

onMounted(() => {
  loadMallAdminProducts()
  loadInventoryRecords()
})
</script>
