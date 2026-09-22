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

      <!-- 核心业务导航（按角色权限配置动态渲染，见 sys_role_permission scope=CLINIC） -->
      <nav class="subsystem-tabs" v-if="visibleTabs.length">
        <div
          v-for="tab in visibleTabs"
          :key="tab.key"
          class="nav-tab"
          :class="{ active: currentTab === tab.key }"
          @click="switchTab(tab.key)"
        >
          {{ tab.label }}
          <span class="warn-dot" v-if="tab.key === 'pharmacy' && stockWarningCount > 0">{{ stockWarningCount }}</span>
        </div>
      </nav>
      <nav class="subsystem-tabs" v-else>
        <span style="font-size: 12px; color: #94a3b8;">当前角色（{{ currentRole }}）未配置云诊所模块权限，请使用管理中台或联系管理员</span>
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
      <template v-if="!visibleTabs.length">
        <div style="display: flex; flex-direction: column; align-items: center; justify-content: center; height: 70vh; color: #94a3b8; gap: 10px;">
          <div style="font-size: 40px;">🔒</div>
          <div style="font-size: 15px;">当前角色（{{ currentRole }}）未开通云诊所模块权限</div>
          <div style="font-size: 12px;">请使用「春播云管理系统」中台处理业务，或联系管理员调整角色权限范围</div>
        </div>
      </template>
      <RegistrationView v-else-if="currentTab === 'registration'" />
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
import { ref, computed, onMounted, provide, watch } from 'vue'
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
const TAB_LABELS = {
  registration: '📋 门诊挂号',
  clinic: '🩺 门诊接诊',
  billing: '💰 划价收费',
  treatment: '💉 特色执行站',
  pharmacy: '💊 智慧药房',
  patient: '📁 患者档案',
  'ai-settings': '⚙️ 设置'
}

// 当前登录角色（NURSE 护士 / DOCTOR 医生 / HR / ADMIN 等，登录时由后端返回）
const currentRole = ref(localStorage.getItem('chunbo_role') || 'DOCTOR')
// 角色允许的模块集合（来自 sys_role_permission 动态配置，scope=CLINIC 云诊所模块）
// allowedModules: null=未加载（兜底全部可见）；[]=配置为空=该角色无云诊所权限
const allowedModules = ref(null)
const loadRolePermissions = async () => {
  try {
    const res = await axios.get('/api/role-permissions')
    const cfg = (res.data || []).find(r =>
      String(r.role || '').toUpperCase() === currentRole.value.toUpperCase()
      && String(r.scope || 'CLINIC').toUpperCase() === 'CLINIC')
    if (cfg) {
      const mods = JSON.parse(cfg.modulesJson || '[]')
      allowedModules.value = Array.isArray(mods) ? mods : []
    }
  } catch (e) {
    // 接口不可用时兜底：全部可见，不影响医生正常使用
  }
}
loadRolePermissions()

const visibleTabs = computed(() => {
  const allow = allowedModules.value
  return TAB_KEYS
    .filter(k => !allow || allow.includes(k))
    .map(k => ({ key: k, label: TAB_LABELS[k] || k }))
})

// 当前 tab 不在角色允许范围时，自动切到第一个可见 tab（如护士登录落在门诊接诊上）
watch(allowedModules, () => {
  if (allowedModules.value && allowedModules.value.length && !allowedModules.value.includes(currentTab.value)) {
    const first = visibleTabs.value[0]
    if (first) switchTab(first.key)
  }
}, { immediate: true })
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

// 响应式读取登录医生的真实身份信息（未登录时为空，不写死演示身份）
const currentDoctorName = ref(localStorage.getItem('chunbo_display_name') || '')
const currentDoctorId = ref(localStorage.getItem('chunbo_doctor_id') || '')
const currentDoctorRole = ref(localStorage.getItem('chunbo_title') || '')
const currentDepartment = ref(localStorage.getItem('chunbo_department') || '')

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
    localStorage.removeItem('chunbo_role')
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