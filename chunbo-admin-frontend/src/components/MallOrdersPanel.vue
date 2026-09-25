<template>
  <div class="tab-pane">
    <div class="module-hero-banner">
      <div>
        <h2 class="hero-title">📦 春播商城订单履约与进销存出库中心</h2>
        <p class="hero-sub">查看用户在线购药订单，商户或管理员手动核准并点击【📦 一键发货出库】，系统将自动扣减药品库存、录入春播健康便民速递单号并生成出库审计台账（非自动发货，保障药品出库合规）。列表自动加载，刷新浏览器即可同步最新订单。</p>
      </div>
    </div>

    <!-- 台账导出（仅 ADMIN/HR） -->
    <div v-if="currentUserRole === 'ADMIN' || currentUserRole === 'HR'" style="display: flex; justify-content: flex-end; margin: 0 0 12px;">
      <el-button type="primary" size="small" plain :loading="ordersExportLoading" @click="exportOrdersExcel">⬇ 导出订单台账 Excel</el-button>
    </div>

    <!-- 指标卡片 -->
    <div class="metrics-grid">
      <div class="metric-card bg-blue">
        <div class="m-label">商城总订单量</div>
        <div class="m-val">{{ mallOrdersList.length }} <span class="unit">单</span></div>
        <div class="m-sub">生活便民购药总订单</div>
      </div>
      <div class="metric-card bg-orange">
        <div class="m-label">待发货配货中</div>
        <div class="m-val">{{ pendingShipCount }} <span class="unit">单</span></div>
        <div class="m-sub">待商户发货出库</div>
      </div>
      <div class="metric-card bg-green">
        <div class="m-label">已发货运输中</div>
        <div class="m-val">{{ shippedCount }} <span class="unit">单</span></div>
        <div class="m-sub">春播便民速递直达</div>
      </div>
      <div class="metric-card bg-purple">
        <div class="m-label">线上实收流水总额</div>
        <div class="m-val">¥{{ totalMallOrderRevenue.toFixed(2) }}</div>
        <div class="m-sub">商户分润核算基数</div>
      </div>
    </div>

    <!-- 订单状态过滤栏与手动发货说明 -->
    <div style="margin: 14px 0 10px; display: flex; justify-content: space-between; align-items: center; background: #f8fafc; padding: 10px 14px; border-radius: 8px; border: 1px solid #e2e8f0;">
      <el-radio-group v-model="orderFilterStatus" size="small">
        <el-radio-button value="ALL">全部订单 ({{ mallOrdersList.length }})</el-radio-button>
        <el-radio-button value="PENDING">待发货出库 ({{ pendingShipCount }})</el-radio-button>
        <el-radio-button value="SHIPPED">已发货运输中 ({{ shippedCount }})</el-radio-button>
      </el-radio-group>
      <span style="font-size: 13px; color: #475569;">
        💡 发货说明：待发货订单请在右侧操作栏点击 <b style="color: #16a34a;">【📦 一键发货出库】</b> 手动出库（非自动发货）
      </span>
    </div>

    <!-- 搜索 / 删除 / 分页工具栏 -->
    <div class="batch-salary-toolbar">
      <el-input v-model="ordersPager.state.search" placeholder="搜索订单号 / 收货人 / 状态…" size="small" clearable style="width: 240px;" />
      <el-button size="small" type="danger" plain :disabled="!orderSelection.length" @click="batchDeleteRows('/api/admin/mall/order/batch-delete', orderSelection.map(r => r.id), '商城订单', loadMallOrders)">🗑 删除选中</el-button>
      <el-pagination style="margin-left: auto;" v-model:current-page="ordersPager.state.page" v-model:page-size="ordersPager.state.size" :page-sizes="[10, 20, 50]" :total="ordersPager.total" layout="total, sizes, prev, pager, next" size="small" />
    </div>

    <!-- 订单表格 -->
    <div class="admin-table-card">
      <el-table :data="ordersPager.paged" stripe size="small" max-height="560" @selection-change="orderSelection = $event">
        <el-table-column type="selection" width="42" />
        <el-table-column prop="orderNo" label="订单号" width="180">
          <template #default="scope">
            <span style="font-family: monospace; font-weight: bold; color: #0284c7;">{{ scope.row.orderNo }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="buyerName" label="购药客户" width="140" />
        <el-table-column prop="clinicName" label="配送收货地址" min-width="180" show-overflow-tooltip />
        <el-table-column label="购药清单明细" min-width="200">
          <template #default="scope">
            <div v-for="(item, idx) in parseOrderItems(scope.row.itemsJson)" :key="idx" class="order-item-chip">
              <span>💊 {{ item.productName }}</span>
              <span class="item-qty">x{{ item.quantity || 1 }}盒</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="finalAmount" label="实付金额" width="100" align="center">
          <template #default="scope">
            <b class="text-danger">¥{{ scope.row.finalAmount }}</b>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="履约状态" width="180">
          <template #default="scope">
            <el-tag :type="scope.row.status && scope.row.status.includes('已发货') ? 'success' : 'warning'" size="small">
              {{ scope.row.status || '待发货' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="下单时间" width="150">
          <template #default="scope">
            <span>{{ formatTime(scope.row.createTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="发货履约操作" width="240" fixed="right" align="center">
          <template #default="scope">
            <!-- 1. 待发货出库 -->
            <el-button
              v-if="!scope.row.status || (!scope.row.status.includes('已发货') && !scope.row.status.includes('已送达'))"
              type="success"
              size="small"
              style="font-weight: bold; box-shadow: 0 2px 4px rgba(22, 163, 74, 0.2);"
              @click="openShipConfirmModal(scope.row)"
            >
              📦 一键发货出库
            </el-button>
            <!-- 2. 已送达 / 居民已签收 (终态) -->
            <div v-else-if="scope.row.status && scope.row.status.includes('已送达')" style="display: flex; align-items: center; justify-content: center; gap: 6px;">
              <el-tag type="success" size="small" effect="dark" style="font-weight: bold; background: #059669; border-color: #059669;">
                ✅ 已送达 / 居民已签收
              </el-tag>
            </div>
            <!-- 3. 已发货运输中 -> 一行横排：状态 + 确认送达 -->
            <div v-else style="display: flex; align-items: center; justify-content: center; gap: 6px;">
              <el-tag type="success" size="small" effect="plain" style="font-weight: bold;">
                🚚 运输中
              </el-tag>
              <el-button
                type="primary"
                size="small"
                plain
                style="font-size: 11px; padding: 2px 8px; height: 24px; font-weight: bold;"
                @click="confirmDeliverOrder(scope.row)"
              >
                ✅ 确认送达
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 弹窗：商城订单发货出库与顺丰物流生成 -->
    <el-dialog 
      v-model="showShipDialog" 
      title="📦 商城订单发货出库与春播便民速递面单核验" 
      width="520px"
    >
      <div v-if="currentShippingOrder">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="订单单号">{{ currentShippingOrder.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="购药客户">{{ currentShippingOrder.buyerName }}</el-descriptions-item>
          <el-descriptions-item label="配送收货地址">{{ currentShippingOrder.clinicName }}</el-descriptions-item>
          <el-descriptions-item label="订单实付总额">¥{{ currentShippingOrder.finalAmount }}</el-descriptions-item>
        </el-descriptions>

        <div class="mt-4">
          <label style="display: block; font-weight: bold; margin-bottom: 6px; font-size: 13px;">春播便民速递运单号：</label>
          <el-input v-model="shippingTrackingNo" placeholder="系统已自动生成春播速递单号 (CB前缀)" size="large" />
        </div>

        <el-alert 
          type="warning" 
          :closable="false" 
          class="mt-4"
          title="发货出库后，系统将真实扣减对应商品库存，并生成出库台账流水计入进销存大屏！" 
        />
      </div>
      <template #footer>
        <el-button @click="showShipDialog = false">取消</el-button>
        <el-button type="success" :loading="shippingLoading" @click="confirmShipOrder">
          确认发货并扣减库存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import axios from 'axios'
import { ElMessage, ElNotification } from 'element-plus'
import { makeListPager, parseOrderItems, batchDeleteRows } from '../utils/common.js'

const props = defineProps({
  currentUserRole: { type: String, default: '' },
  currentUserName: { type: String, default: '' },
  currentRoleLabel: { type: String, default: '' }
})

// ── 商城订单台账导出 Excel（仅 ADMIN/HR 可见）──
const ordersExportLoading = ref(false)
const exportOrdersExcel = async () => {
  ordersExportLoading.value = true
  try {
    const res = await axios.get('/api/export/orders', { responseType: 'blob' })
    const link = document.createElement('a')
    link.href = URL.createObjectURL(res.data)
    link.download = '商城订单台账.xlsx'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(link.href)
    ElMessage.success('商城订单台账导出成功')
  } catch (e) {
    ElMessage.error('导出失败：' + (e.response?.data?.message || e.message || '网络错误'))
  } finally {
    ordersExportLoading.value = false
  }
}

const formatTime = (val) => {
  if (!val) return '-'
  const d = new Date(val)
  if (isNaN(d.getTime())) return String(val).replace('T', ' ').substring(0, 19)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

const mallOrdersList = ref([])
const showShipDialog = ref(false)
const currentShippingOrder = ref(null)
const shippingTrackingNo = ref('')
const shippingLoading = ref(false)
const orderFilterStatus = ref('ALL')
const orderSelection = ref([])

const filteredMallOrders = computed(() => {
  if (orderFilterStatus.value === 'PENDING') {
    return mallOrdersList.value.filter(o => !o.status || !o.status.includes('已发货'))
  }
  if (orderFilterStatus.value === 'SHIPPED') {
    return mallOrdersList.value.filter(o => o.status && o.status.includes('已发货'))
  }
  return mallOrdersList.value
})

const ordersPager = makeListPager(filteredMallOrders, ['orderNo', 'buyerName', 'status', 'clinicName'])

const loadMallOrders = async () => {
  try {
    const res = await axios.get('/api/admin/mall/user/orders')
    mallOrdersList.value = res.data?.data || []
  } catch (e) {
    console.error("加载商城订单失败", e)
  }
}

const pendingShipCount = computed(() => {
  return mallOrdersList.value.filter(o => !o.status || !o.status.includes('已发货')).length
})

const shippedCount = computed(() => {
  return mallOrdersList.value.filter(o => o.status && o.status.includes('已发货')).length
})

const totalMallOrderRevenue = computed(() => {
  return mallOrdersList.value.reduce((sum, o) => sum + Number(o.finalAmount || 0), 0)
})

const openShipConfirmModal = (order) => {
  currentShippingOrder.value = order
  shippingTrackingNo.value = 'CB' + Date.now().toString().slice(-10)
  showShipDialog.value = true
}

const confirmShipOrder = async () => {
  if (!currentShippingOrder.value) return
  shippingLoading.value = true
  try {
    const res = await axios.post('/api/admin/mall/order/ship', {
      orderNo: currentShippingOrder.value.orderNo,
      trackingNo: shippingTrackingNo.value,
      operator: props.currentUserName + ' (' + props.currentRoleLabel + ')'
    })
    if (res.data && res.data.success) {
      ElNotification({
        title: '🎉 订单发货出库成功',
        message: `春播便民速递运单号【${res.data.trackingNo}】，库存已实时扣减并生成出库台账！`,
        type: 'success',
        duration: 5000
      })
      showShipDialog.value = false
      await loadMallOrders()
      // 商品库存/大屏流水由切到对应标签页时自动刷新（tabLoaders）保证
    } else {
      ElMessage.error(res.data?.message || '发货失败')
    }
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '发货出库遇到错误')
  } finally {
    shippingLoading.value = false
  }
}

const confirmDeliverOrder = async (order) => {
  if (!order || !order.orderNo) return
  try {
    const res = await axios.post('/api/admin/mall/order/deliver', { orderNo: order.orderNo })
    if (res.data && res.data.success) {
      ElNotification({
        title: '🎉 订单已送达妥投',
        message: res.data.message || `订单【${order.orderNo}】已确认由居民成功签收！`,
        type: 'success',
        duration: 4500
      })
      await loadMallOrders()
    } else {
      ElMessage.error(res.data?.message || '确认送达操作失败')
    }
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '确认送达操作遇到异常')
  }
}

onMounted(() => { loadMallOrders() })
</script>
