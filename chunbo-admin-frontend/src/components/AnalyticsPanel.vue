<script setup>
import { ref, onMounted } from 'vue'
import axios from 'axios'
import { marked } from 'marked'
import { ElMessage } from 'element-plus'

const props = defineProps({
  currentUserRole: String,
  currentUserName: String,
  currentUserStaffId: String,
  currentRoleLabel: String
})

const analytics = ref({})
const inventoryRecords = ref([])
const stockWarnings = ref([])
const analyticsPeriod = ref('today')

const renderMarkdown = (text) => {
  marked.setOptions({ breaks: true, gfm: true })
  let html = marked.parse(text || '')
  if (typeof html === 'string') {
    html = html
      .replace(/<table>/g, '<div class="md-table-scroll"><table>')
      .replace(/<\/table>/g, '</table></div>')
  }
  return html
}

const loadAnalytics = async () => {
  try {
    const resSummary = await axios.get('/api/analytics/summary', {
      params: { period: analyticsPeriod.value }
    })
    analytics.value = resSummary.data || {}
    const resInv = await axios.get('/api/pharmacy/inventory-records')
    inventoryRecords.value = resInv.data || []
    const resWarn = await axios.get('/api/pharmacy/warnings')
    stockWarnings.value = resWarn.data || []
  } catch (e) {}
}

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

// 联动右下角全局悬浮 AI 调度中枢
const askAssistant = (query) => {
  window.dispatchEvent(new CustomEvent('open-admin-ai-drawer', { detail: { query } }))
}

onMounted(() => {
  loadAnalytics()
})

// ── AI 运营周报：汇总最近 7 天营收/患者/药品动销，由 LLM 生成自然语言周报 ──
const weeklyReportLoading = ref(false)
const weeklyReportVisible = ref(false)
const weeklyReportData = ref(null)
const weeklyReportText = ref('')
const loadWeeklyReport = async () => {
  weeklyReportLoading.value = true
  try {
    const res = await axios.get('/api/analytics/weekly-report')
    const d = res.data || {}
    weeklyReportData.value = d.data || null
    weeklyReportText.value = d.report || ''
    weeklyReportVisible.value = true
  } catch (e) {
    ElMessage.error('周报生成失败：' + (e.response?.data?.message || e.message))
  } finally {
    weeklyReportLoading.value = false
  }
}
</script>

<template>
  <div class="tab-pane">
    <div class="pane-header">
      <div>
        <h3>📊 基层诊所进销存与营业运营大屏</h3>
        <span class="sub-desc">支持实时流水明细监控、多统计周期切换（今日/本月/年度）与进销存台账</span>
      </div>
      <div style="display: flex; gap: 12px; align-items: center;">
        <el-radio-group v-model="analyticsPeriod" size="small" @change="loadAnalytics">
          <el-radio-button value="today">今日实时</el-radio-button>
          <el-radio-button value="month">本月统计 (近30天)</el-radio-button>
          <el-radio-button value="year">年度统计 (2026年)</el-radio-button>
        </el-radio-group>
        <el-button type="primary" size="small" @click="loadAnalytics">刷新数据</el-button>
        <el-button type="success" size="small" plain :loading="weeklyReportLoading" @click="loadWeeklyReport">📄 AI 运营周报</el-button>
      </div>
    </div>

    <!-- 四大指标卡片（根据今日/本月/年度动态切换） -->
    <div class="metrics-grid">
      <div class="metric-card bg-blue">
        <div class="m-label">{{ analytics.labelRegTitle || '门诊接诊人数' }}</div>
        <div class="m-val">{{ analytics.activeRegistrations !== undefined ? analytics.activeRegistrations : (analytics.todayRegistrations || 0) }} <span class="unit">人次</span></div>
        <div class="m-sub">{{ analytics.labelRegSub || '门诊患者接待与建档' }}</div>
      </div>
      <div class="metric-card bg-green">
        <div class="m-label">{{ analytics.labelFeeTitle || '门诊挂号费流水' }}</div>
        <div class="m-val">¥{{ analytics.activeRegFeeRevenue !== undefined ? analytics.activeRegFeeRevenue : (analytics.todayRegFeeRevenue || 0) }}</div>
        <div class="m-sub">{{ analytics.labelFeeSub || '实收标准 ¥10/人次' }}</div>
      </div>
      <div class="metric-card bg-purple">
        <div class="m-label">{{ analytics.labelMedTitle || '处方药品销售额' }}</div>
        <div class="m-val">¥{{ analytics.activeMedicineRevenue !== undefined ? analytics.activeMedicineRevenue : (analytics.todayMedicineRevenue || 0) }}</div>
        <div class="m-sub">{{ analytics.labelMedSub || '门诊处方药房销售实收' }}</div>
      </div>
      <div class="metric-card bg-orange">
        <div class="m-label">{{ analytics.labelPlasterTitle || '特色中药贴敷理疗创收' }}</div>
        <div class="m-val">¥{{ analytics.activePlasterRevenue !== undefined ? analytics.activePlasterRevenue : (analytics.totalPlasterRevenue || 0) }}</div>
        <div class="m-sub">{{ analytics.labelPlasterSub || '特色中医理疗专案 (综合毛利率 46.8%)' }}</div>
      </div>
    </div>

    <!-- 台账与预警双卡 -->
    <div class="grid-2col mt-16">
      <div class="sub-card">
        <div class="sub-card-header flex-between">
          <div style="display: flex; align-items: center; gap: 6px;">
            <span>📦</span>
            <b>最新药品出入库台账流水 (进销存)</b>
          </div>
          <el-button size="small" type="primary" plain :loading="inventoryExportLoading" @click="exportInventoryExcel">⬇ 导出库存台账</el-button>
        </div>
        <el-table :data="inventoryRecords" stripe size="small" max-height="320">
          <el-table-column prop="refOrderNo" label="单据号" width="185">
            <template #default="scope">
              <span style="font-family: monospace; font-size: 11px; white-space: nowrap;">{{ scope.row.refOrderNo }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="medicineName" label="药品通用名" min-width="130" show-overflow-tooltip />
          <el-table-column prop="recordType" label="类型" width="115" align="center">
            <template #default="scope">
              <el-tag 
                :type="scope.row.recordType && scope.row.recordType.includes('入库') ? 'success' : 'danger'" 
                size="small"
                style="white-space: nowrap; padding: 0 6px; font-weight: 500;"
              >
                {{ scope.row.recordType }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="changeQty" label="变动量" width="80" align="center">
            <template #default="scope">
              <span :style="{ color: scope.row.changeQty > 0 ? '#10b981' : '#ef4444', fontWeight: 'bold' }">
                {{ scope.row.changeQty > 0 ? '+' + scope.row.changeQty : scope.row.changeQty }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="afterStock" label="结余库存" width="85" align="center" />
          <el-table-column prop="operator" label="经办人" width="140" show-overflow-tooltip />
        </el-table>
      </div>

      <div class="sub-card">
        <div class="sub-card-header">
          <span>⚠️</span>
          <b>智慧药房基药低库存预警台账</b>
          <el-button size="small" type="danger" plain style="margin-left: 8px;" @click="askAssistant('智慧药房智能补货与临期药品预警研判')">🤖 AI 智能补货研判</el-button>
          <el-tag type="danger" size="small" class="ml-auto">{{ stockWarnings.length }} 种紧缺</el-tag>
        </div>
        <el-table :data="stockWarnings" stripe size="small" max-height="320">
          <el-table-column label="药品名称" min-width="130">
            <template #default="scope">
              <span class="font-bold">{{ scope.row.medicineName || scope.row.name }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="stock" label="当前库存" width="100">
            <template #default="scope">
              <b class="text-danger">{{ scope.row.stock }} {{ scope.row.unit || '盒' }}</b>
            </template>
          </el-table-column>
          <el-table-column label="预警阈值" width="100">
            <template #default="scope">
              <span>{{ scope.row.minStock || scope.row.warningStock || 50 }} {{ scope.row.unit || '盒' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="建议直供药企" min-width="140">
            <template #default="scope">
              <el-tag size="small" type="info">{{ scope.row.supplier || scope.row.manufacturer || '春播特约药企' }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <!-- AI 运营周报弹窗（最近 7 天真实数据 + LLM 成文） -->
    <el-dialog v-model="weeklyReportVisible" title="📄 AI 运营周报（最近 7 天）" width="720px">
      <div v-if="weeklyReportData" style="margin-bottom: 16px; padding: 12px; background: #f8fafc; border-radius: 8px; font-size: 13px; color: #475569; display: flex; gap: 20px; flex-wrap: wrap;">
        <span>接诊 <b>{{ weeklyReportData.registrations }}</b> 人次</span>
        <span>开方 <b>{{ weeklyReportData.prescriptions }}</b> 张</span>
        <span>营收 <b class="text-danger">¥{{ weeklyReportData.revenue }}</b></span>
      </div>
      <div v-if="weeklyReportText" class="msg-content" v-html="renderMarkdown(weeklyReportText)"></div>
      <div v-else style="color: #94a3b8; font-size: 13px;">AI 周报服务暂不可用，请稍后重试（上方为原始数据摘要）。</div>
    </el-dialog>
  </div>
</template>
