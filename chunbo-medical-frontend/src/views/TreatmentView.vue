<template>
  <div class="treatment-container">
    <div class="header-card">
      <div class="header-left">
        <div class="page-title-badge">
          <span class="pulse-dot"></span>
          <span class="title-text">特色中医贴敷理疗执行站</span>
        </div>
        <el-tag type="success" effect="plain" class="header-tag">护士工作站 · 接单核销中枢</el-tag>

        <!-- 实时数量统计 -->
        <div class="stat-pills" v-if="totalCount > 0">
          <span class="stat-pill total">共 {{ totalCount }} 单</span>
          <span class="stat-pill pending">待执行 {{ pendingCount }}</span>
          <span class="stat-pill completed">已核销 {{ completedCount }}</span>
        </div>
        <div class="stat-pills" v-else>
          <span class="stat-pill empty-pill">当日无单据</span>
        </div>
      </div>

      <div class="header-right">
        <!-- 核心日期切换组件（与挂号站与接诊站保持高度统一） -->
        <div class="date-nav-bar">
          <el-button 
            size="small" 
            class="d-arrow-btn" 
            @click="changeDateOffset(-1)" 
            title="查看前一天"
            :disabled="isAllDateMode"
          >
            ◀
          </el-button>
          <el-date-picker
            v-model="selectedDate"
            type="date"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
            size="small"
            :clearable="false"
            class="date-picker-input"
            :disabled="isAllDateMode"
            placeholder="选择日期"
            @change="onDateChange"
          />
          <el-button 
            size="small" 
            class="d-arrow-btn" 
            @click="changeDateOffset(1)" 
            title="查看后一天"
            :disabled="isAllDateMode"
          >
            ▶
          </el-button>
          <el-button 
            size="small" 
            type="success" 
            class="today-btn" 
            :plain="selectedDate !== todayStr || isAllDateMode"
            @click="goToToday"
            title="快速返回今日任务"
          >
            今日
          </el-button>
          <el-button 
            size="small" 
            :type="isAllDateMode ? 'primary' : 'info'" 
            :plain="!isAllDateMode"
            class="all-date-btn" 
            @click="toggleAllDateMode"
            title="切换为查看全部历史单据"
          >
            {{ isAllDateMode ? '📅 按日期查看' : '🌐 查看全部' }}
          </el-button>
        </div>

        <!-- 状态过滤单选组 -->
        <el-radio-group v-model="statusFilter" size="small" class="custom-radio">
          <el-radio-button value="">全部状态</el-radio-button>
          <el-radio-button value="pending">待执行</el-radio-button>
          <el-radio-button value="completed">已完成</el-radio-button>
        </el-radio-group>

        <!-- 关键词搜索框 -->
        <el-input
          v-model="searchKeyword"
          placeholder="患者姓名/单号/项目"
          prefix-icon="Search"
          size="small"
          clearable
          class="search-input"
          @clear="loadTreatments"
          @keyup.enter="loadTreatments"
        />

        <el-button type="primary" size="small" class="gradient-btn" @click="loadTreatments" :loading="loading">
          <el-icon><Refresh /></el-icon> 刷新
        </el-button>
      </div>
    </div>

    <!-- 当前时间范围提示条 -->
    <div class="date-context-banner">
      <div class="context-left">
        <span class="banner-icon">📌</span>
        <span class="banner-text">
          当前展示：
          <b v-if="isAllDateMode" class="text-highlight">全部历史单据</b>
          <b v-else class="text-highlight">{{ selectedDate === todayStr ? '【今日】' : '' }}{{ selectedDate }}</b>
          理疗接单记录
          <span v-if="statusFilter" class="status-tip">（已筛选：{{ statusFilter === 'pending' ? '仅待执行' : '仅已完成' }}）</span>
          <span v-if="searchKeyword" class="search-tip">（关键词："{{ searchKeyword }}"）</span>
        </span>
      </div>
      <div class="context-right">
        <span class="current-nurse-tip">当前执勤核销医护: <b>{{ currentNurseName }}</b></span>
      </div>
    </div>

    <!-- 执行任务卡片网格 -->
    <div v-if="filteredList.length > 0" class="treatment-grid">
      <div 
        v-for="item in filteredList" 
        :key="item.id" 
        class="treatment-card"
        :class="'card-' + item.status"
      >
        <div class="card-header">
          <div class="header-seq">
            <span class="seq-no">#{{ item.recordNo }}</span>
            <el-tag :type="item.status === 'completed' ? 'success' : 'warning'" size="small">
              {{ item.status === 'completed' ? '已执行完成' : '待执行接单' }}
            </el-tag>
          </div>
          <div class="header-right-meta">
            <span class="time-meta" v-if="getItemDate(item)">🕒 {{ getItemDate(item) }}</span>
            <span class="technique-badge">{{ item.technique || '特色穴位贴敷' }} 工艺</span>
          </div>
        </div>

        <div class="patient-bar">
          <span class="p-name">{{ item.patientName }}</span>
          <span class="p-gender">{{ item.patientGender }}</span>
          <span class="p-age">{{ item.patientAge }}</span>
          <span class="p-doc">开单医生: {{ item.doctorName }}</span>
        </div>

        <div class="treatment-detail-box">
          <div class="detail-row">
            <span class="d-label">理疗项目：</span>
            <span class="d-val font-bold">{{ item.treatmentName }}</span>
          </div>
          <div class="detail-row">
            <span class="d-label">辨证穴位：</span>
            <span class="d-val acupoints-text">{{ item.acupoints || '辨证配穴阿是穴' }}</span>
          </div>
          <div class="detail-row">
            <span class="d-label">贴敷时长：</span>
            <span class="d-val">{{ item.durationHours || 4 }} 小时 · 共 {{ item.patchCount || 2 }} 贴</span>
          </div>
          <div class="detail-row">
            <span class="d-label">耗材配方：</span>
            <span class="d-val materials-text">{{ item.materialsUsed || '医用透气胶布、中药透皮贴膏辅料' }}</span>
          </div>
        </div>

        <div class="card-footer">
          <span class="nurse-name">
            执行人: <b>{{ item.nurseName || '待指定护士' }}</b>
          </span>
          <el-button 
            v-if="item.status !== 'completed'" 
            type="primary" 
            class="gradient-exec-btn"
            size="default"
            :loading="executingId === item.id"
            @click="executeTreatment(item)"
          >
            <el-icon><Check /></el-icon> 穴位核对并执行核销
          </el-button>
          <el-tag v-else type="success" effect="dark" size="small">
            已于 {{ formatTime(item.executedAt) }} 执行完成
          </el-tag>
        </div>
      </div>
    </div>

    <!-- 空数据占位状态 -->
    <div v-else class="empty-state-box">
      <el-empty 
        :description="isAllDateMode ? '当前无任何理疗接单记录' : `${selectedDate} 暂无贴敷理疗单据`" 
        :image-size="120"
      >
        <template #extra>
          <div class="empty-actions">
            <el-button v-if="!isAllDateMode" type="primary" plain @click="toggleAllDateMode">
              🌐 查看全部历史单据{{ treatments.length ? ` (共 ${treatments.length} 单)` : '' }}
            </el-button>
            <el-button v-if="selectedDate !== todayStr && !isAllDateMode" type="success" plain @click="goToToday">
              📅 返回今日
            </el-button>
            <el-button v-if="searchKeyword" type="info" plain @click="clearSearch">
              清空搜索关键词
            </el-button>
          </div>
        </template>
      </el-empty>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox, ElNotification } from 'element-plus'
import { Refresh, Check, Search } from '@element-plus/icons-vue'
import axios from 'axios'

// 获取当前本地日期 YYYY-MM-DD
const getLocalDateString = (d = new Date()) => {
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

const todayStr = getLocalDateString()
const selectedDate = ref(todayStr)
const isAllDateMode = ref(false)
const statusFilter = ref('')
const searchKeyword = ref('')
const treatments = ref([])
const loading = ref(false)
const executingId = ref(null)

// 当前登录人角色/姓名
const currentNurseName = ref(localStorage.getItem('chunbo_username') || localStorage.getItem('chunbo_real_name') || '责任护士')

// 提取单据归属日期 YYYY-MM-DD（健壮提取 createdAt / 单号 TR+YYYYMMDD / executedAt）
const getItemDate = (item) => {
  if (!item) return ''
  // 1. 优先从 createdAt 解析
  if (item.createdAt) {
    if (typeof item.createdAt === 'string') {
      const match = item.createdAt.match(/^(\d{4}[-/.]\d{2}[-/.]\d{2})/)
      if (match) return match[1].replace(/[/.]/g, '-')
    } else if (Array.isArray(item.createdAt) && item.createdAt.length >= 3) {
      const y = item.createdAt[0]
      const m = String(item.createdAt[1]).padStart(2, '0')
      const d = String(item.createdAt[2]).padStart(2, '0')
      return `${y}-${m}-${d}`
    }
  }
  // 2. 从 recordNo 单号中解析（单号格式如 TR20260924191823011719）
  if (item.recordNo) {
    const m = item.recordNo.match(/TR(\d{4})(\d{2})(\d{2})/)
    if (m) return `${m[1]}-${m[2]}-${m[3]}`
    const m2 = item.recordNo.match(/(20\d{2})(\d{2})(\d{2})/)
    if (m2) return `${m2[1]}-${m2[2]}-${m2[3]}`
  }
  // 3. 从 executedAt 解析兜底
  if (item.executedAt) {
    if (typeof item.executedAt === 'string') {
      const match = item.executedAt.match(/^(\d{4}[-/.]\d{2}[-/.]\d{2})/)
      if (match) return match[1].replace(/[/.]/g, '-')
    } else if (Array.isArray(item.executedAt) && item.executedAt.length >= 3) {
      const y = item.executedAt[0]
      const m = String(item.executedAt[1]).padStart(2, '0')
      const d = String(item.executedAt[2]).padStart(2, '0')
      return `${y}-${m}-${d}`
    }
  }
  return ''
}

// 经过日期筛选后的当前数据集（前后端双重严格闭环：无论后端是否重启，前端均进行二次精确匹配，实现绝对双保险）
const dateMatchedList = computed(() => {
  if (isAllDateMode.value) {
    return treatments.value
  }
  if (!selectedDate.value) {
    return treatments.value
  }
  return treatments.value.filter(item => {
    const itemDate = getItemDate(item)
    return itemDate === selectedDate.value
  })
})

// 统计数量（与当前选择日期完全同步）
const totalCount = computed(() => dateMatchedList.value.length)
const pendingCount = computed(() => dateMatchedList.value.filter(t => t.status !== 'completed').length)
const completedCount = computed(() => dateMatchedList.value.filter(t => t.status === 'completed').length)

// 最终渲染列表：结合状态与搜索词过滤
const filteredList = computed(() => {
  return dateMatchedList.value.filter(t => {
    if (statusFilter.value && t.status !== statusFilter.value) {
      return false
    }
    if (searchKeyword.value && searchKeyword.value.trim()) {
      const kw = searchKeyword.value.trim().toLowerCase()
      const matchName = t.patientName && t.patientName.toLowerCase().includes(kw)
      const matchNo = t.recordNo && t.recordNo.toLowerCase().includes(kw)
      const matchTreatment = t.treatmentName && t.treatmentName.toLowerCase().includes(kw)
      const matchDoc = t.doctorName && t.doctorName.toLowerCase().includes(kw)
      if (!matchName && !matchNo && !matchTreatment && !matchDoc) {
        return false
      }
    }
    return true
  })
})

const loadTreatments = async () => {
  loading.value = true
  try {
    const params = {}
    if (statusFilter.value) params.status = statusFilter.value
    if (!isAllDateMode.value && selectedDate.value) params.date = selectedDate.value
    if (searchKeyword.value && searchKeyword.value.trim()) params.keyword = searchKeyword.value.trim()

    const res = await axios.get('/api/treatment/list', { params })
    treatments.value = res.data || []
  } catch (e) {
    console.error(e)
    ElMessage.error('获取理疗执行任务失败：' + (e.message || '网络异常'))
  } finally {
    loading.value = false
  }
}

// 切换日期前后偏移天数
const changeDateOffset = (offsetDays) => {
  isAllDateMode.value = false
  const cur = selectedDate.value ? new Date(selectedDate.value) : new Date()
  cur.setDate(cur.getDate() + offsetDays)
  selectedDate.value = getLocalDateString(cur)
  loadTreatments()
}

// 日期选择器变更
const onDateChange = () => {
  isAllDateMode.value = false
  loadTreatments()
}

// 回到今天
const goToToday = () => {
  isAllDateMode.value = false
  selectedDate.value = todayStr
  loadTreatments()
}

// 切换全部/按日期模式
const toggleAllDateMode = () => {
  isAllDateMode.value = !isAllDateMode.value
  loadTreatments()
}

// 清空关键词
const clearSearch = () => {
  searchKeyword.value = ''
  loadTreatments()
}

onMounted(() => {
  // 首次载入优先拉取今日理疗单
  loadTreatments()
})

// 执行核销
const executeTreatment = async (item) => {
  ElMessageBox.confirm(
    `确认对患者【${item.patientName}】进行【${item.treatmentName}】穴位贴敷（${item.acupoints || '阿是穴'}）？\n执行后系统将以当前操作护士【${currentNurseName.value}】签名归档，并真实核销出库药膏耗材！`,
    '贴敷执行核销确认',
    {
      confirmButtonText: '核对无误，确认执行出库',
      cancelButtonText: '取消',
      type: 'warning'
    }
  ).then(async () => {
    executingId.value = item.id
    try {
      const res = await axios.post(`/api/treatment/execute/${item.id}`, {
        nurseName: currentNurseName.value
      })
      if (res.data && res.data.success) {
        item.status = 'completed'
        item.nurseName = res.data.executor || currentNurseName.value
        item.executedAt = res.data.executedAt || new Date().toISOString()
        ElNotification({
          title: '🎉 理疗执行核销成功',
          message: res.data.message || '特色贴敷已执行完成，耗材库存已扣减并写入进销存流水！',
          type: 'success',
          duration: 4000
        })
      } else {
        ElMessage.error(res.data?.message || '执行核销失败')
      }
    } catch (e) {
      ElMessage.error('执行核销网络异常：' + (e.message || '请稍后重试'))
    } finally {
      executingId.value = null
    }
  }).catch(() => {})
}

const formatTime = (t) => {
  if (!t) return ''
  return t.replace('T', ' ').substring(0, 16)
}
</script>

<style scoped>
.treatment-container {
  display: flex;
  flex-direction: column;
  gap: 14px;
  animation: fadeIn 0.3s ease-out;
}

.header-card {
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(12px);
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 12px 18px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.03);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.page-title-badge {
  display: flex;
  align-items: center;
  gap: 8px;
  background: linear-gradient(135deg, #f0fdf4, #dcfce7);
  padding: 5px 12px;
  border-radius: 18px;
  border: 1px solid #bbf7d0;
}

.pulse-dot {
  width: 8px;
  height: 8px;
  background: #16a34a;
  border-radius: 50%;
  animation: pulse 2s infinite;
}

.title-text {
  font-weight: 700;
  color: #14532d;
  font-size: 14px;
}

.stat-pills {
  display: flex;
  align-items: center;
  gap: 6px;
}

.stat-pill {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 12px;
}

.stat-pill.total {
  background: #f1f5f9;
  color: #475569;
}

.stat-pill.pending {
  background: #fef3c7;
  color: #b45309;
}

.stat-pill.completed {
  background: #dcfce7;
  color: #15803d;
}

.stat-pill.empty-pill {
  background: #f1f5f9;
  color: #94a3b8;
}

.header-right-meta {
  display: flex;
  align-items: center;
  gap: 6px;
}

.time-meta {
  font-size: 11px;
  color: #64748b;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  padding: 1px 6px;
  border-radius: 4px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

/* 核心日期导航栏样式（与全院挂号/接诊组件统一设计） */
.date-nav-bar {
  display: flex;
  align-items: center;
  gap: 4px;
  background: #f8fafc;
  padding: 3px 6px;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.d-arrow-btn {
  padding: 0 6px !important;
  font-weight: bold;
}

.date-picker-input {
  width: 125px !important;
}

.today-btn {
  font-weight: 600;
  padding: 0 8px !important;
}

.all-date-btn {
  padding: 0 10px !important;
  font-size: 12px;
}

.search-input {
  width: 180px;
}

.gradient-btn {
  background: linear-gradient(135deg, #0284c7, #0369a1);
  border: none;
  font-weight: 600;
}

/* 时间上下文提示栏 */
.date-context-banner {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #f8fafc;
  border: 1px dashed #cbd5e1;
  border-radius: 8px;
  padding: 8px 16px;
  font-size: 12px;
  color: #64748b;
}

.banner-icon {
  margin-right: 6px;
}

.text-highlight {
  color: #0284c7;
  font-size: 13px;
}

.status-tip {
  color: #d97706;
  font-weight: 600;
}

.search-tip {
  color: #4f46e5;
}

.current-nurse-tip b {
  color: #059669;
}

/* 卡片网格 */
.treatment-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(360px, 1fr));
  gap: 14px;
}

.treatment-card {
  background: #fff;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  padding: 15px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  box-shadow: 0 3px 10px rgba(0, 0, 0, 0.02);
  transition: all 0.2s;
}

.treatment-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 18px rgba(0, 0, 0, 0.06);
}

.card-pending {
  border-left: 5px solid #f59e0b;
}

.card-completed {
  border-left: 5px solid #10b981;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-seq {
  display: flex;
  align-items: center;
  gap: 8px;
}

.seq-no {
  font-weight: 700;
  color: #2563eb;
  font-size: 13px;
}

.technique-badge {
  background: #f1f5f9;
  color: #475569;
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
}

.patient-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-bottom: 6px;
  border-bottom: 1px solid #f1f5f9;
}

.p-name {
  font-size: 15px;
  font-weight: 700;
  color: #0f172a;
}

.p-gender, .p-age {
  font-size: 12px;
  color: #64748b;
}

.p-doc {
  margin-left: auto;
  font-size: 12px;
  color: #475569;
}

.treatment-detail-box {
  background: #f8fafc;
  border-radius: 8px;
  padding: 8px 10px;
  display: flex;
  flex-direction: column;
  gap: 5px;
  font-size: 12px;
}

.detail-row {
  display: flex;
}

.d-label {
  color: #64748b;
  width: 65px;
  flex-shrink: 0;
}

.d-val {
  color: #1e293b;
}

.acupoints-text {
  color: #2563eb;
  font-weight: 600;
}

.materials-text {
  color: #475569;
  font-size: 11px;
}

.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 8px;
  border-top: 1px dashed #f1f5f9;
}

.nurse-name {
  font-size: 12px;
  color: #64748b;
}

.gradient-exec-btn {
  background: linear-gradient(135deg, #16a34a, #15803d);
  border: none;
  font-weight: 600;
}

.empty-state-box {
  background: #fff;
  border-radius: 12px;
  padding: 40px 20px;
  border: 1px solid #e2e8f0;
}

.empty-actions {
  display: flex;
  gap: 10px;
  justify-content: center;
  margin-top: 12px;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(4px); }
  to { opacity: 1; transform: translateY(0); }
}

@keyframes pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.5; transform: scale(1.2); }
}
</style>
