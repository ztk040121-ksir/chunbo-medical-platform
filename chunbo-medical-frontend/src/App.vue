<template>
  <div id="app-wrapper">
    <!-- 现代医疗 SaaS 顶栏 -->
    <header class="top-nav-header">
      <div class="brand-zone">
        <div class="logo-icon">🌿</div>
        <div class="brand-texts">
          <span class="brand-title">春播万象云诊所 · 智能门诊与智慧药房</span>
        </div>
      </div>

      <!-- 核心业务导航 -->
      <nav class="subsystem-tabs">
        <div 
          class="nav-tab" 
          :class="{ active: currentTab === 'registration' }"
          @click="currentTab = 'registration'"
        >
          📋 门诊挂号
        </div>
        <div 
          class="nav-tab" 
          :class="{ active: currentTab === 'clinic' }"
          @click="currentTab = 'clinic'"
        >
          🩺 门诊接诊
        </div>
        <div 
          class="nav-tab" 
          :class="{ active: currentTab === 'billing' }"
          @click="currentTab = 'billing'"
        >
          💰 划价收费
        </div>
        <div 
          class="nav-tab" 
          :class="{ active: currentTab === 'treatment' }"
          @click="currentTab = 'treatment'"
        >
          💉 特色执行站
        </div>
        <div 
          class="nav-tab" 
          :class="{ active: currentTab === 'pharmacy' }"
          @click="currentTab = 'pharmacy'"
        >
          💊 智慧药房
          <span class="warn-dot" v-if="stockWarningCount > 0">{{ stockWarningCount }}</span>
        </div>
        <div 
          class="nav-tab" 
          :class="{ active: currentTab === 'patient' }"
          @click="currentTab = 'patient'"
        >
          📁 患者档案
        </div>
        <div 
          class="nav-tab" 
          :class="{ active: currentTab === 'ai-settings' }"
          @click="currentTab = 'ai-settings'"
        >
          ⚙️ 设置
        </div>
      </nav>

      <!-- 登录医生状态与安全退出 -->
      <div class="header-user-bar">
        <div class="user-profile-badge" @click="showProfileDialog = true" title="点击查看执业信息与数字工牌">
          <span class="status-online-dot"></span>
          <span class="user-name">{{ currentDoctorName }} ({{ currentDoctorRole }})</span>
          <span class="user-jobno-chip">工号: {{ currentDoctorId }}</span>
        </div>
        <button class="logout-btn-action" @click="handleLogoutConfirm" title="安全退出医生工作台">
          <el-icon class="logout-action-icon"><SwitchButton /></el-icon>
          <span>退出</span>
        </button>
      </div>
    </header>

    <!-- 视图路由切换 -->
    <main class="main-content-viewport">
      <RegistrationView v-if="currentTab === 'registration'" />
      <ClinicWorkstation v-else-if="currentTab === 'clinic'" />
      <BillingView v-else-if="currentTab === 'billing'" />
      <TreatmentView v-else-if="currentTab === 'treatment'" />
      <PharmacyView v-else-if="currentTab === 'pharmacy'" />
      <PatientManageView v-else-if="currentTab === 'patient'" />
      <AiSettingsView v-else-if="currentTab === 'ai-settings'" />
    </main>

    <!-- 医生执业信息与数字工牌弹窗 -->
    <el-dialog
      v-model="showProfileDialog"
      title="👨‍⚕️ 医生执业信息与数字工牌"
      width="580px"
      append-to-body
      destroy-on-close
    >
      <div class="doctor-badge-card">
        <div class="badge-card-header">
          <div class="doc-avatar-large">👨‍⚕️</div>
          <div class="doc-badge-titles">
            <div class="name-line">
              <span class="doc-main-name">{{ currentDoctorName }}</span>
              <el-tag type="success" size="small" effect="dark">在岗接诊中</el-tag>
              <el-tag type="primary" size="small">{{ currentDoctorRole }}</el-tag>
            </div>
            <div class="doc-id-line">系统工号：<b>{{ currentDoctorId }}</b> · 药房工号：<b>PHA_2001</b></div>
            <div class="doc-dept-line">{{ currentDepartment }} · 春播第001社区卫生服务站</div>
          </div>
        </div>

        <div class="badge-details-grid">
          <div class="b-item"><span class="b-label">医师资格证号：</span><span class="b-val font-mono">199843110430105002819</span></div>
          <div class="b-item"><span class="b-label">执业证书编号：</span><span class="b-val font-mono">110430105002819</span></div>
          <div class="b-item"><span class="b-label">执业范围：</span><span class="b-val">全科医学专业 / 中西医结合</span></div>
          <div class="b-item"><span class="b-label">CA数字证书：</span><span class="b-val text-success">已绑定认证 (有效期至2028-12-31)</span></div>
          <div class="b-item"><span class="b-label">处方权限：</span><span class="b-val">普通处方 / 抗菌药物二线 / 特色贴敷开立</span></div>
          <div class="b-item"><span class="b-label">今日门诊班次：</span><span class="b-val">全天班 (08:00 - 17:30 · 1诊室)</span></div>
        </div>

        <div class="badge-footer-note">
          <span>🔒 处方电子签名密码与 CA 数字证书已接入国家卫健委互联网医疗安全认证体系。</span>
        </div>
      </div>
      <template #footer>
        <div class="profile-dialog-footer">
          <el-button type="danger" plain @click="handleLogoutConfirm" class="dialog-logout-btn">
            <el-icon style="margin-right: 4px"><SwitchButton /></el-icon>
            <span>退出当前登录</span>
          </el-button>
          <el-button type="primary" @click="showProfileDialog = false">确认关闭</el-button>
        </div>
      </template>
    </el-dialog>

  </div>
</template>

<script setup>
import { ref, onMounted, provide, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import { SwitchButton } from '@element-plus/icons-vue'
import axios from 'axios'

import RegistrationView from './views/RegistrationView.vue'
import ClinicWorkstation from './views/ClinicWorkstation.vue'
import BillingView from './views/BillingView.vue'
import TreatmentView from './views/TreatmentView.vue'
import PharmacyView from './views/PharmacyView.vue'
import PatientManageView from './views/PatientManageView.vue'
import AiSettingsView from './views/AiSettingsView.vue'

const router = useRouter()

const TAB_KEYS = ['registration', 'clinic', 'billing', 'treatment', 'pharmacy', 'patient', 'ai-settings']
const readTabFromHash = () => {
  const h = window.location.hash.replace(/^#\/?/, '')
  if (TAB_KEYS.includes(h)) return h
  return localStorage.getItem('chunbo_active_tab') || 'registration'
}
const currentTab = ref(readTabFromHash())

watch(currentTab, (newTab) => {
  if (newTab) {
    localStorage.setItem('chunbo_active_tab', newTab)
    // 同步 URL hash，支持刷新定位与浏览器前进/后退
    if (window.location.hash !== '#' + newTab) {
      window.location.hash = newTab
    }
  }
}, { immediate: true })

window.addEventListener('hashchange', () => {
  const h = readTabFromHash()
  if (h !== currentTab.value) currentTab.value = h
})

const switchTab = (tab) => {
  currentTab.value = tab
  localStorage.setItem('chunbo_active_tab', tab)
}
provide('switchTab', switchTab)
window.addEventListener('switch-tab', (e) => {
  if (e.detail) {
    currentTab.value = e.detail
    localStorage.setItem('chunbo_active_tab', e.detail)
  }
})

// 响应式读取登录医生的真实身份信息
const currentDoctorName = ref(localStorage.getItem('chunbo_display_name') || '张文浩')
const currentDoctorId = ref(localStorage.getItem('chunbo_doctor_id') || 'DOC_1002')
const currentDoctorRole = ref(localStorage.getItem('chunbo_title') || '主治医师 / 调剂药师')
const currentDepartment = ref(localStorage.getItem('chunbo_department') || '全科慢病门诊 / 智慧药房')

const showProfileDialog = ref(false)
const stockWarningCount = ref(3)

const loadWarnings = async () => {
  try {
    const res = await axios.get('/api/pharmacy/warnings')
    if (res.data) {
      stockWarningCount.value = res.data.length
    }
  } catch (e) {}
}

onMounted(() => {
  loadWarnings()
  // 刷新时同步 localStorage 最新的医生信息
  const name = localStorage.getItem('chunbo_display_name')
  if (name) currentDoctorName.value = name
  const docId = localStorage.getItem('chunbo_doctor_id')
  if (docId) currentDoctorId.value = docId
  const title = localStorage.getItem('chunbo_title')
  if (title) currentDoctorRole.value = title
  const dept = localStorage.getItem('chunbo_department')
  if (dept) currentDepartment.value = dept
})

// 安全退出登录逻辑
const handleLogoutConfirm = () => {
  ElMessageBox.confirm(
    '您确定要退出当前春播万象基层医生工作台吗？退出后需重新输入工号密码登录。',
    '退出登录确认',
    {
      confirmButtonText: '确定退出',
      cancelButtonText: '取消',
      type: 'warning',
      confirmButtonClass: 'el-button--danger'
    }
  ).then(() => {
    localStorage.removeItem('chunbo_jwt_token')
    localStorage.removeItem('chunbo_username')
    localStorage.removeItem('chunbo_display_name')
    localStorage.removeItem('chunbo_doctor_id')
    localStorage.removeItem('chunbo_department')
    localStorage.removeItem('chunbo_title')
    ElMessage.success('已安全退出登录')
    showProfileDialog.value = false
    router.push('/login')
  }).catch(() => {})
}
</script>

<style>
/* 全局样式基准 */
* {
  box-sizing: border-box;
  margin: 0;
  padding: 0;
}

body {
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
  background-color: #f1f5f9;
  color: #0f172a;
}

#app-wrapper {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

/* 顶栏 */
.top-nav-header {
  height: 62px;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid rgba(226, 232, 240, 0.9);
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 24px;
  position: sticky;
  top: 0;
  z-index: 1000;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.03);
}

.brand-zone {
  display: flex;
  align-items: center;
  gap: 12px;
}

.logo-icon {
  width: 36px;
  height: 36px;
  background: linear-gradient(135deg, #10b981, #059669);
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  box-shadow: 0 3px 8px rgba(16, 185, 129, 0.25);
}

.brand-texts {
  display: flex;
  flex-direction: column;
}

.brand-title {
  font-size: 15px;
  font-weight: 800;
  color: #0f172a;
  letter-spacing: -0.3px;
}

.brand-badge {
  font-size: 11px;
  color: #64748b;
  font-weight: 500;
}

/* 业务 Tab 栏 */
.subsystem-tabs {
  display: flex;
  align-items: center;
  gap: 6px;
  background: #f1f5f9;
  padding: 4px;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
}

.nav-tab {
  padding: 6px 14px;
  font-size: 13px;
  font-weight: 600;
  color: #475569;
  border-radius: 7px;
  cursor: pointer;
  transition: all 0.2s ease;
  position: relative;
  display: flex;
  align-items: center;
  gap: 4px;
}

.nav-tab:hover {
  color: #1e293b;
  background: rgba(255, 255, 255, 0.6);
}

.nav-tab.active {
  background: #fff;
  color: #2563eb;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.06);
}

.warn-dot {
  background: #ef4444;
  color: #fff;
  font-size: 10px;
  padding: 1px 5px;
  border-radius: 10px;
  font-weight: 700;
}

/* 右侧工牌与退出栏 */
.header-user-bar {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-profile-badge {
  display: flex;
  align-items: center;
  gap: 8px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  padding: 5px 12px;
  border-radius: 20px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.user-profile-badge:hover {
  background: #f0fdf4;
  border-color: #86efac;
}

.status-online-dot {
  width: 7px;
  height: 7px;
  background: #10b981;
  border-radius: 50%;
  box-shadow: 0 0 6px #10b981;
}

.user-name {
  font-size: 12px;
  font-weight: 600;
  color: #334155;
}

.user-jobno-chip {
  background: #1e3a8a;
  color: #93c5fd;
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 4px;
  font-weight: 700;
  margin-left: 4px;
}

.logout-btn-action {
  display: flex;
  align-items: center;
  gap: 4px;
  background: #fee2e2;
  border: 1px solid #fca5a5;
  color: #dc2626;
  padding: 5px 10px;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
}

.logout-btn-action:hover {
  background: #dc2626;
  border-color: #dc2626;
  color: #ffffff;
  transform: translateY(-1px);
  box-shadow: 0 2px 6px rgba(220, 38, 38, 0.25);
}

.logout-action-icon {
  font-size: 13px;
}

/* 主容器 */
.main-content-viewport {
  flex: 1;
  padding: 16px 24px;
  max-width: 1680px;
  width: 100%;
  margin: 0 auto;
}

/* 医生工牌卡片样式 */
.doctor-badge-card {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.badge-card-header {
  display: flex;
  align-items: center;
  gap: 16px;
  background: linear-gradient(135deg, #f0fdf4, #dcfce7);
  border: 1px solid #bbf7d0;
  border-radius: 12px;
  padding: 16px;
}

.doc-avatar-large {
  font-size: 42px;
}

.doc-badge-titles {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.name-line {
  display: flex;
  align-items: center;
  gap: 8px;
}

.doc-main-name {
  font-size: 18px;
  font-weight: 800;
  color: #0f172a;
}

.doc-id-line {
  font-size: 13px;
  color: #166534;
}

.doc-dept-line {
  font-size: 12px;
  color: #475569;
}

.badge-details-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  background: #f8fafc;
  padding: 16px;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
}

.b-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 12.5px;
}

.b-label {
  color: #64748b;
  font-size: 11.5px;
}

.b-val {
  color: #1e293b;
  font-weight: 600;
}

.font-mono {
  font-family: monospace;
}

.text-success {
  color: #16a34a;
}

.badge-footer-note {
  font-size: 11.5px;
  color: #64748b;
  background: #f1f5f9;
  padding: 8px 12px;
  border-radius: 6px;
}

.profile-dialog-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.dialog-logout-btn {
  display: flex;
  align-items: center;
  gap: 4px;
}
</style>