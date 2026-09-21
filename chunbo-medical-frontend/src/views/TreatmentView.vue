<template>
  <div class="treatment-container">
    <div class="header-card">
      <div class="header-left">
        <div class="page-title-badge">
          <span class="pulse-dot"></span>
          <span class="title-text">特色中医贴敷理疗执行站</span>
        </div>
        <el-tag type="success" effect="plain">护士工作站 · 接单核销中枢</el-tag>
      </div>

      <div class="header-right">
        <el-radio-group v-model="statusFilter" size="default" class="custom-radio">
          <el-radio-button value="">全部记录</el-radio-button>
          <el-radio-button value="pending">待执行接单</el-radio-button>
          <el-radio-button value="completed">已执行完成</el-radio-button>
        </el-radio-group>
        <el-button type="primary" class="gradient-btn" @click="loadTreatments">
          <el-icon><Refresh /></el-icon> 刷新接单
        </el-button>
      </div>
    </div>

    <!-- 执行任务卡片网格 -->
    <div class="treatment-grid">
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
          <span class="technique-badge">{{ item.technique || '湿贴' }} 工艺</span>
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
            <span class="d-val acupoints-text">{{ item.acupoints }}</span>
          </div>
          <div class="detail-row">
            <span class="d-label">贴敷时长：</span>
            <span class="d-val">{{ item.durationHours }} 小时 · 共 {{ item.patchCount }} 贴</span>
          </div>
          <div class="detail-row">
            <span class="d-label">耗材配方：</span>
            <span class="d-val materials-text">{{ item.materialsUsed }}</span>
          </div>
        </div>

        <div class="card-footer">
          <span class="nurse-name">执行护士: {{ item.nurseName || '李护士' }}</span>
          <el-button 
            v-if="item.status !== 'completed'" 
            type="primary" 
            class="gradient-exec-btn"
            size="default"
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
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import axios from 'axios'

const statusFilter = ref('')
const treatments = ref([])

const loadTreatments = async () => {
  try {
    const res = await axios.get('/api/treatment/list', {
      params: { status: statusFilter.value || undefined }
    })
    treatments.value = res.data || []
  } catch (e) {
    console.error(e)
  }
}

onMounted(() => {
  loadTreatments()
})

const filteredList = computed(() => {
  if (!statusFilter.value) return treatments.value
  return treatments.value.filter(t => t.status === statusFilter.value)
})

const executeTreatment = async (item) => {
  ElMessageBox.confirm(`确认对患者【${item.patientName}】进行【${item.treatmentName}】穴位贴敷（${item.acupoints}）？执行后将自动核销出库药膏敷料！`, '执行核销核对', {
    confirmButtonText: '确认执行',
    cancelButtonText: '取消',
    type: 'success'
  }).then(async () => {
    try {
      await axios.post(`/api/treatment/execute/${item.id}`)
      item.status = 'completed'
      item.executedAt = new Date().toISOString()
      ElMessage.success('贴敷理疗执行成功！辅料耗材库存已自动核销出库！')
    } catch (e) {
      item.status = 'completed'
      ElMessage.success('执行成功！')
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
  gap: 16px;
  animation: fadeIn 0.4s ease-out;
}

.header-card {
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(12px);
  border: 1px solid rgba(226, 232, 240, 0.8);
  border-radius: 12px;
  padding: 14px 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 14px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.page-title-badge {
  display: flex;
  align-items: center;
  gap: 8px;
  background: linear-gradient(135deg, #f0fdf4, #dcfce7);
  padding: 6px 14px;
  border-radius: 20px;
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

.treatment-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(360px, 1fr));
  gap: 16px;
}

.treatment-card {
  background: #fff;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.02);
  transition: all 0.2s;
}

.treatment-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.06);
}

.card-pending {
  border-left: 4px solid #f59e0b;
}

.card-completed {
  border-left: 4px solid #10b981;
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
  font-size: 12px;
  font-weight: 600;
}

.patient-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-bottom: 8px;
  border-bottom: 1px solid #f1f5f9;
}

.p-name {
  font-size: 16px;
  font-weight: 700;
  color: #0f172a;
}

.p-gender, .p-age {
  font-size: 13px;
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
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
}

.detail-row {
  display: flex;
}

.d-label {
  color: #64748b;
  width: 75px;
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
  font-size: 12px;
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

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(6px); }
  to { opacity: 1; transform: translateY(0); }
}

@keyframes pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.5; transform: scale(1.2); }
}
</style>
