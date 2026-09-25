<template>
  <div id="admin-app">
    <!-- ============================================== -->
    <!-- 1. 未登录状态：全屏高质感中台登录卡片 -->
    <!-- ============================================== -->
    <div v-if="!isLoggedIn" class="admin-login-screen">
      <div class="login-bg-glow glow-1"></div>
      <div class="login-bg-glow glow-2"></div>

      <div class="login-modal-box">
        <div class="login-brand">
          <div class="brand-logo-icon">🏛️</div>
          <div>
            <h2 class="brand-main-title">春播云综合运营与人事OA中台</h2>
            <p class="brand-sub-title">医院运营 · 人事薪酬 · OA独立审批 · 商城进销存</p>
          </div>
        </div>

        <div class="login-tab-title">
          <h3>中台统一身份认证</h3>
          <span class="version-tag">RBAC 安全系统 v4.0.0</span>
        </div>


        <el-form :model="loginForm" class="login-form" @submit.prevent="handleAdminLogin">
          <el-form-item>
            <el-input 
              v-model="loginForm.username" 
              placeholder="请输入管理员 / 人事 / 医护工号" 
              size="large"
              prefix-icon="User"
              clearable
              class="custom-login-input"
            />
          </el-form-item>
          <el-form-item>
            <el-input 
              v-model="loginForm.password" 
              type="password" 
              placeholder="请输入登录密码" 
              size="large"
              prefix-icon="Lock"
              show-password
              class="custom-login-input"
              @keyup.enter="handleAdminLogin"
            />
          </el-form-item>

          <div v-if="loginError" class="login-error-tip">
            <span>⚠️ {{ loginError }}</span>
          </div>

          <el-button 
            type="primary" 
            size="large" 
            class="admin-login-btn" 
            :loading="loginLoading"
            @click="handleAdminLogin"
          >
            {{ loginLoading ? '正在核验权限身份...' : '登 录 综 合 中 台' }}
          </el-button>
        </el-form>

        <div class="login-bottom-info">
          <span>春播万象全栈医疗中台 · BCrypt 安全加密通道</span>
          <el-link type="info" underline="never" @click="openDoctorPlatform">打开医生工作台 →</el-link>
        </div>
      </div>
    </div>

    <!-- ============================================== -->
    <!-- 2. 已登录状态：中台主工作台 -->
    <!-- ============================================== -->
    <template v-else>
      <!-- 顶栏导航 -->
      <header class="admin-header">
        <div class="brand-zone">
          <div class="logo-box">🏛️</div>
          <div>
            <div class="brand-title">春播云管理系统 · 医院综合运营与人事OA中台</div>
            <div class="brand-sub">MySQL 8.0 & Spring AI · 全平台 RBAC 授权架构 (当前权限: {{ currentRoleLabel }})</div>
          </div>
        </div>



        <!-- 登录个人工牌与退出 -->
        <div class="user-header-group">
          <!-- 全局 AI 运营调度快捷入口 -->
          <button class="header-ai-quick-btn" @click="handleOpenAiAssistant" title="随时呼出 AI 综合调度（支持发货、改价、审批、发薪）">
            <span class="ai-live-dot"></span>
            <span>🤖 AI 运营调度</span>
          </button>

          <div class="user-badge" @click="showProfileDialog = true">
            <div class="badge-avatar">{{ currentUserRole === 'DOCTOR' ? '👨‍⚕️' : (currentUserRole === 'HR' ? '💼' : '👑') }}</div>
            <div class="badge-info">
              <div class="badge-name">
                {{ currentUserName }}
                <el-tag size="small" :type="currentUserRole === 'ADMIN' ? 'danger' : (currentUserRole === 'HR' ? 'warning' : 'success')" effect="dark">
                  {{ currentRoleLabel }}
                </el-tag>
              </div>
              <div class="badge-role">{{ currentUserDept }} · {{ currentUserTitle }}</div>
            </div>
          </div>

          <button class="header-logout-btn" @click="handleLogoutConfirm" title="安全退出系统">
            <el-icon><SwitchButton /></el-icon>
            <span>退出</span>
          </button>
        </div>
      </header>

      <!-- 主体区域：左侧导航 + 右侧内容 -->
      <div class="admin-main">
        <aside class="admin-sidebar">
          <!-- 1. 经营分析大屏（按角色中台权限配置动态显示） -->
          <div
            v-if="canSeeTab('analytics')"
            class="menu-item"
            :class="{ active: currentTab === 'analytics' }"
            @click="currentTab = 'analytics'"
          >
            <span class="menu-icon">📊</span>
            <span class="menu-label">诊所经营分析大屏</span>
          </div>

          <!-- 2. 商城订单履约与发货 -->
          <div
            v-if="canSeeTab('mall-orders')"
            class="menu-item"
            :class="{ active: currentTab === 'mall-orders' }"
            @click="currentTab = 'mall-orders'"
          >
            <span class="menu-icon">📦</span>
            <span class="menu-label">商城订单履约与发货</span>
          </div>

          <!-- 3. 商城商品管理与进销存 -->
          <div
            v-if="canSeeTab('mall-products')"
            class="menu-item"
            :class="{ active: currentTab === 'mall-products' }"
            @click="currentTab = 'mall-products'"
          >
            <span class="menu-icon">🛍️</span>
            <span class="menu-label">商城商品与进销存</span>
          </div>

          <!-- 4. 商城注册用户管理 -->
          <div
            v-if="canSeeTab('mall-users')"
            class="menu-item"
            :class="{ active: currentTab === 'mall-users' }"
            @click="currentTab = 'mall-users'"
          >
            <span class="menu-icon">👤</span>
            <span class="menu-label">商城注册用户管理</span>
          </div>

          <!-- 5. 工资条管理 (医生/商户看自己，人事/管理员看全院并能发放) -->
          <div
            v-if="canSeeTab('salary')"
            class="menu-item"
            :class="{ active: currentTab === 'salary' }"
            @click="currentTab = 'salary'"
          >
            <span class="menu-icon">💼</span>
            <span class="menu-label">{{ (currentUserRole === 'DOCTOR' || currentUserRole === 'MERCHANT') ? '我的工资条明细' : '工资条发放与核算' }}</span>
          </div>

          <!-- 6. OA 请假独立审批中心 (医生发起，人事/管理员审批) -->
          <div
            v-if="canSeeTab('approval')"
            class="menu-item"
            :class="{ active: currentTab === 'approval' }"
            @click="currentTab = 'approval'"
          >
            <span class="menu-icon">📑</span>
            <span class="menu-label">{{ currentUserRole === 'DOCTOR' ? '我的OA请假申请' : 'OA 请假独立审批中心' }}</span>
          </div>

          <!-- 7. 医护账号与权限管理 (仅人事、管理员) -->
          <div
            v-if="canSeeTab('doctors')"
            class="menu-item"
            :class="{ active: currentTab === 'doctors' }"
            @click="currentTab = 'doctors'"
          >
            <span class="menu-icon">👨‍⚕️</span>
            <span class="menu-label">医护账号与权限管理</span>
          </div>

          <!-- 8. AI 大模型网关配置中心 (管理员专属) -->
          <div
            v-if="canSeeTab('ai-config')"
            class="menu-item"
            :class="{ active: currentTab === 'ai-config' }"
            @click="currentTab = 'ai-config'"
          >
            <span class="menu-icon">🤖</span>
            <span class="menu-label">AI模型网关与热切换</span>
          </div>

          <!-- 9. 人事与商户账号管理已并入「医护账号与权限管理」 -->
        </aside>

        <main class="admin-content">
          <!-- 1. 经营分析大屏 -->
          <AnalyticsPanel v-if="currentTab === 'analytics'" :current-user-role="currentUserRole" :current-user-name="currentUserName" :current-user-staff-id="currentUserStaffId" :current-role-label="currentRoleLabel" />
                    <!-- 2. 商城订单履约与发货工作台 -->
          <MallOrdersPanel v-else-if="currentTab === 'mall-orders'" :current-user-role="currentUserRole" :current-user-name="currentUserName" :current-role-label="currentRoleLabel" />
          <SalaryPanel v-else-if="currentTab === 'salary'" :current-user-role="currentUserRole" :current-user-name="currentUserName" :current-user-staff-id="currentUserStaffId" />
          <!-- 3. OA 独立审批中心 -->
          <ApprovalPanel v-else-if="currentTab === 'approval'" :current-user-role="currentUserRole" :current-user-name="currentUserName" :current-user-staff-id="currentUserStaffId" :current-role-label="currentRoleLabel" />
          <DoctorsPanel v-else-if="currentTab === 'doctors'" @perm-saved="loadAdminRolePermissions" />
          <!-- 6. 商城商品管理与进销存 (管理员最高权限专属) -->
          <MallProductsPanel v-else-if="currentTab === 'mall-products'" :current-user-role="currentUserRole" :current-user-name="currentUserName" />

          <!-- 7. 商城注册用户管理 (管理员专属) -->
          <MallUsersPanel v-else-if="currentTab === 'mall-users'" />

          <!-- 8. AI 大模型网关与热切换 (管理员专属) -->
          <AiConfigPanel v-else-if="currentTab === 'ai-config'" />

          <!-- 9. 人事账号注册与管理已并入「医护账号与权限管理」统一总台账 -->
        </main>
      </div>

      <!-- 全局 AI 运营调度抽屉（在任意页面随时调度） -->
      <GlobalAssistantDrawer
        v-model:visible="showGlobalAiDrawer"
        :current-user-role="currentUserRole"
        :current-user-name="currentUserName"
        :current-user-staff-id="currentUserStaffId"
        :current-role-label="currentRoleLabel"
      />

      <!-- 全局右下角常驻 AI 调度悬浮球 -->
      <div class="global-floating-ai-ball" @click="handleOpenAiAssistant" title="随时呼出 AI 综合调度指挥官">
        <div class="ai-ball-icon">🤖</div>
        <div class="ai-ball-tip">AI调度</div>
      </div>

      <!-- 个人工牌弹窗 -->
      <el-dialog v-model="showProfileDialog" title="👨‍⚕️ 综合中台执业人员工牌与档案" width="460px">
        <div class="profile-card">
          <div class="p-row"><b>员工工号:</b> <el-tag size="small" type="success">{{ currentUserStaffId }}</el-tag></div>
          <div class="p-row"><b>真实姓名:</b> {{ currentUserName }}</div>
          <div class="p-row"><b>安全角色:</b> <el-tag size="small" :type="currentUserRole === 'ADMIN' ? 'danger' : 'primary'">{{ currentRoleLabel }}</el-tag></div>
          <div class="p-row"><b>所属部门:</b> {{ currentUserDept }}</div>
          <div class="p-row"><b>岗位职称:</b> {{ currentUserTitle }}</div>
          <div class="p-row"><b>签约机构:</b> 春播万象基层医疗服务平台</div>
        </div>
        <template #footer>
          <div class="flex-between">
            <el-button type="danger" plain size="small" @click="handleLogoutConfirm">退出登录</el-button>
            <el-button type="primary" size="small" @click="showProfileDialog = false">关闭工牌</el-button>
          </div>
        </template>
      </el-dialog>

    <!-- 弹窗：注册商户账号 -->
    <el-dialog v-model="showAddMerchantModal" title="🏪 注册春播商城商户账号" width="460px">
      <el-form label-width="100px">
        <el-form-item label="登录账号 *" required>
          <el-input v-model="merchantForm.username" placeholder="如 merchant_02" />
        </el-form-item>
        <el-form-item label="登录密码 *" required>
          <el-input v-model="merchantForm.password" type="password" show-password placeholder="默认 123456" />
        </el-form-item>
        <el-form-item label="商户负责人 *" required>
          <el-input v-model="merchantForm.realName" placeholder="如 李商户" />
        </el-form-item>
        <el-form-item label="联系手机号">
          <el-input v-model="merchantForm.phone" placeholder="11位手机号" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddMerchantModal = false">取消</el-button>
        <el-button type="success" :loading="merchantSubmitting" @click="submitRegisterMerchant">
          确认注册商户
        </el-button>
      </template>
    </el-dialog>


    </template>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, computed, nextTick , watch } from 'vue'
import axios from 'axios'
import { marked } from 'marked'
import { ElMessage, ElNotification, ElMessageBox } from 'element-plus'
import { SwitchButton } from '@element-plus/icons-vue'
import MallUsersPanel from './components/MallUsersPanel.vue'
import MallOrdersPanel from './components/MallOrdersPanel.vue'
import MallProductsPanel from './components/MallProductsPanel.vue'
import ApprovalPanel from './components/ApprovalPanel.vue'
import SalaryPanel from './components/SalaryPanel.vue'
import DoctorsPanel from './components/DoctorsPanel.vue'
import AnalyticsPanel from './components/AnalyticsPanel.vue'
import AiConfigPanel from './components/AiConfigPanel.vue'
import GlobalAssistantDrawer from './components/GlobalAssistantDrawer.vue'
import { makeListPager, parseOrderItems, batchDeleteRows } from './utils/common.js'

const openDoctorPlatform = () => {
  const protocol = window.location.protocol || 'http:'
  const hostname = window.location.hostname || 'localhost'
  window.open(`${protocol}//${hostname}:5173/login`, '_blank')
}

const showGlobalAiDrawer = ref(false)
const handleOpenAiAssistant = () => {
  showGlobalAiDrawer.value = !showGlobalAiDrawer.value
}

// 防 XSS：转义 LLM/用户输出中的原始 HTML（marked 默认原样透传 HTML 标签，存在存储型 XSS 风险）
const escapeHtml = (s) => String(s ?? '')
  .replace(/&/g, '&amp;')
  .replace(/</g, '&lt;')
  .replace(/>/g, '&gt;')
  .replace(/"/g, '&quot;')
  .replace(/'/g, '&#39;')
marked.use({ renderer: { html(token) { return escapeHtml(token.text) } } })

// ==============================================
// 认证与 RBAC 状态
// ==============================================
const isLoggedIn = ref(!!localStorage.getItem('chunbo_admin_token'))
const currentUserRole = ref(localStorage.getItem('chunbo_admin_role') || 'ADMIN')
const currentUserName = ref(localStorage.getItem('chunbo_admin_name') || '系统管理员')
const currentUserStaffId = ref(localStorage.getItem('chunbo_admin_staff_id') || 'ADM_0001')
const currentUserDept = ref(localStorage.getItem('chunbo_admin_dept') || '医院院办 / 信息中心')
const currentUserTitle = ref(localStorage.getItem('chunbo_admin_title') || '总院管理员')

const currentRoleLabel = computed(() => {
  if (currentUserRole.value === 'ADMIN') return '最高管理员'
  if (currentUserRole.value === 'HR') return '人事主管'
  if (currentUserRole.value === 'MERCHANT') return '商城商户店长'
  if (currentUserRole.value === 'DOCTOR') return '门诊医生'
  return '中台员工'
})

// 登录表单
const loginForm = ref({ username: 'admin', password: '123456' })
const loginLoading = ref(false)
const loginError = ref('')


const handleAdminLogin = async () => {
  if (!loginForm.value.username || !loginForm.value.password) {
    loginError.value = '请输入工号和密码'
    return
  }
  loginLoading.value = true
  loginError.value = ''
  try {
    const res = await axios.post('/api/auth/login', loginForm.value)
    if (res.data && res.data.success) {
      localStorage.setItem('chunbo_admin_token', res.data.token)
      localStorage.setItem('chunbo_admin_role', res.data.role || 'ADMIN')
      localStorage.setItem('chunbo_admin_name', res.data.displayName || res.data.username)
      localStorage.setItem('chunbo_admin_staff_id', res.data.staffId || 'STAFF_001')
      localStorage.setItem('chunbo_admin_dept', res.data.department || '全科门诊')
      localStorage.setItem('chunbo_admin_title', res.data.title || '主治医师')

      currentUserRole.value = res.data.role || 'ADMIN'
      currentUserName.value = res.data.displayName || res.data.username
      currentUserStaffId.value = res.data.staffId || 'STAFF_001'
      currentUserDept.value = res.data.department || '全科门诊'
      currentUserTitle.value = res.data.title || '主治医师'
      isLoggedIn.value = true

      // 登录后按当前角色重新拉取管理系统权限配置（scope=ADMIN），替代页面初始加载时可能拉到的旧角色配置
      allowedAdminTabs.value = null
      await loadAdminRolePermissions()

      // 根据角色智能跳转默认首屏
      if (currentUserRole.value === 'DOCTOR') {
        currentTab.value = 'analytics'
      } else if (currentUserRole.value === 'MERCHANT') {
        currentTab.value = 'mall-orders'
      } else {
        currentTab.value = 'analytics'
      }

      ElNotification({
        title: '登录成功',
        message: `欢迎进入中台，您的当前权限为: 【${currentRoleLabel.value}】`,
        type: 'success'
      })
      loadAllData()
    } else {
      loginError.value = res.data?.message || '登录失败，请核对工号密码'
    }
  } catch (err) {
    if (err.response?.status === 401) {
      loginError.value = err.response.data?.message || '工号或密码错误'
    } else {
      loginError.value = '无法连接到后端(8080端口)，请确保后端服务正常运行'
    }
  } finally {
    loginLoading.value = false
  }
}

const handleLogoutConfirm = () => {
  ElMessageBox.confirm('您确定要退出春播综合中台系统吗？', '退出确认', {
    confirmButtonText: '确定退出',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    localStorage.removeItem('chunbo_admin_token')
    localStorage.removeItem('chunbo_admin_role')
    localStorage.removeItem('chunbo_admin_name')
    localStorage.removeItem('chunbo_admin_staff_id')
    localStorage.removeItem('chunbo_admin_dept')
    localStorage.removeItem('chunbo_admin_title')
    allowedAdminTabs.value = null
    isLoggedIn.value = false
    showProfileDialog.value = false
    ElMessage.success('已安全注销退出')
  }).catch(() => {})
}

// ==============================================
// 业务数据状态
// ==============================================
// 当前主标签：刷新后保持在原标签页（localStorage 持久化）
const currentTab = ref(localStorage.getItem('chunbo_admin_tab') || 'analytics')
// 员工角色矩阵、人事与商户中台管理均已并入「医护账号与权限管理」(doctors)，纠正历史缓存
if (currentTab.value === 'roles' || currentTab.value === 'hr-management') {
  currentTab.value = 'doctors'
}

// ── 角色允许的中台模块（sys_role_permission scope=ADMIN 动态配置，替代写死的 v-if）──
const allowedAdminTabs = ref(null)
const defaultAdminTabs = (role) => {
  const r = (role || '').toUpperCase()
  if (r === 'MERCHANT') return ['analytics', 'salary', 'approval', 'mall-orders', 'mall-products', 'mall-users']
  if (r === 'HR') return ['analytics', 'salary', 'approval', 'doctors']
  if (r === 'DOCTOR' || r === 'NURSE') return ['analytics', 'salary', 'approval']
  return ['analytics', 'mall-orders', 'mall-products', 'mall-users', 'salary', 'approval', 'doctors', 'ai-config']
}
const loadAdminRolePermissions = async () => {
  try {
    const res = await axios.get('/api/role-permissions')
    const cfg = (res.data || []).find(r =>
      String(r.role || '').toUpperCase() === (currentUserRole.value || '').toUpperCase()
      && String(r.scope || '').toUpperCase() === 'ADMIN')
    if (cfg) {
      const mods = JSON.parse(cfg.modulesJson || '[]')
      allowedAdminTabs.value = Array.isArray(mods) ? mods : []
    }
  } catch (e) {}
}
const canSeeTab = (key) => {
  if (key === 'ai-config') return (currentUserRole.value || '').toUpperCase() === 'ADMIN'
  return allowedAdminTabs.value
    ? allowedAdminTabs.value.includes(key)
    : defaultAdminTabs(currentUserRole.value).includes(key)
}
watch(allowedAdminTabs, () => {
  if (allowedAdminTabs.value && allowedAdminTabs.value.length && !allowedAdminTabs.value.includes(currentTab.value)) {
    const first = defaultAdminTabs(currentUserRole.value).find(k => allowedAdminTabs.value.includes(k))
    if (first) currentTab.value = first
  }
}, { immediate: true })
loadAdminRolePermissions()
watch(currentTab, (v) => {
  try { localStorage.setItem('chunbo_admin_tab', v) } catch (e) {}
})
// 商户无经营大屏权限：恢复到不可见标签时自动纠正
if (currentUserRole.value === 'MERCHANT' && currentTab.value === 'analytics') {
  currentTab.value = 'mall-orders'
}
const showProfileDialog = ref(false)

const handleOpenAdminAiDrawer = () => {
  showGlobalAiDrawer.value = true
}

onMounted(() => {
  if (isLoggedIn.value) {
    loadAllData()
  }
  window.addEventListener('open-admin-ai-drawer', handleOpenAdminAiDrawer)
})

onUnmounted(() => {
  window.removeEventListener('open-admin-ai-drawer', handleOpenAdminAiDrawer)
})
const openSystem = (url) => window.open(url, '_blank')
const loadAllData = async () => {
  loadDoctorAccounts()
  if (currentUserRole.value === 'ADMIN' || currentUserRole.value === 'HR') {
    loadStaffList()
  }
}
const loadDoctorAccounts = async () => {
  doctorLoading.value = true
  try {
    const res = await axios.get('/api/doctor/list')
    if (res.data?.success) doctorAccounts.value = res.data.data || []
  } catch (e) {} finally { doctorLoading.value = false }
}

// 人事管理 (Admin 专属)
const loadStaffList = async () => {
  staffLoading.value = true
  try {
    const res = await axios.get('/api/admin/staff/list')
    if (res.data?.success) allStaffList.value = res.data.data || []
  } catch (e) {} finally { staffLoading.value = false }
}

const showAddMerchantModal = ref(false)
const merchantSubmitting = ref(false)
const merchantForm = ref({
  username: '',
  password: 'password123',
  realName: '',
  phone: ''
})

const openAddMerchantDialog = () => {
  merchantForm.value = {
    username: 'merchant_' + Date.now().toString().slice(-4),
    password: 'password123',
    realName: '',
    phone: ''
  }
  showAddMerchantModal.value = true
}

const submitRegisterMerchant = async () => {
  if (!merchantForm.value.username || !merchantForm.value.realName) {
    ElMessage.warning('请填写账号和商户真实姓名')
    return
  }
  merchantSubmitting.value = true
  try {
    const res = await axios.post('/api/admin/merchant/register', merchantForm.value)
    if (res.data && res.data.success) {
      ElNotification({
        title: '注册成功',
        message: res.data.message,
        type: 'success'
      })
      showAddMerchantModal.value = false
      await loadStaffList()
    } else {
      ElMessage.error(res.data?.message || '注册失败')
    }
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '注册商户遇到错误')
  } finally {
    merchantSubmitting.value = false
  }
}

</script>

<style>
* { box-sizing: border-box; margin: 0; padding: 0; }
body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  background-color: #0f172a;
  color: #334155;
}

#admin-app {
  display: flex;
  flex-direction: column;
  height: 100vh;
  overflow: hidden;
  background: #f8fafc;
}

/* 登录大屏 */
.admin-login-screen {
  min-height: 100vh;
  background: radial-gradient(circle at 10% 20%, #064e3b 0%, #022c22 45%, #051311 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
  padding: 20px;
}
.login-bg-glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(100px);
  opacity: 0.3;
  pointer-events: none;
}
.glow-1 { width: 500px; height: 500px; background: #10b981; top: -100px; left: -100px; }
.glow-2 { width: 450px; height: 450px; background: #0ea5e9; bottom: -80px; right: -80px; }

.login-modal-box {
  width: 100%;
  max-width: 460px;
  background: rgba(15, 34, 28, 0.85);
  backdrop-filter: blur(30px);
  -webkit-backdrop-filter: blur(30px);
  border: 1px solid rgba(52, 211, 153, 0.25);
  border-radius: 24px;
  padding: 44px 38px 30px;
  box-shadow: 0 30px 70px rgba(0,0,0,0.6), 0 0 30px rgba(16,185,129,0.15);
  position: relative;
  z-index: 10;
}

.login-brand { display: flex; align-items: center; gap: 14px; margin-bottom: 24px; }
.brand-logo-icon { font-size: 38px; }
.brand-main-title { font-size: 19px; font-weight: 700; color: #ecfdf5; margin-bottom: 4px; }
.brand-sub-title { font-size: 12px; color: #a7f3d0; opacity: 0.85; }

.login-tab-title { display: flex; align-items: center; justify-content: space-between; margin-bottom: 20px; }
.login-tab-title h3 { color: #fff; font-size: 20px; margin: 0; }
.version-tag { background: rgba(16, 185, 129, 0.2); border: 1px solid #10b981; color: #a7f3d0; font-size: 11px; padding: 2px 8px; border-radius: 6px; }

.role-quick-selector {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  background: rgba(2, 44, 34, 0.6);
  border: 1px dashed rgba(52, 211, 153, 0.4);
  padding: 8px 12px;
  border-radius: 10px;
  margin-bottom: 20px;
}
.quick-label { font-size: 12px; color: #94a3b8; }
.quick-tag { cursor: pointer; transition: transform 0.15s; }
.quick-tag:hover { transform: scale(1.05); }

.custom-login-input :deep(.el-input__wrapper) {
  background: rgba(2, 44, 34, 0.6) !important;
  border: 1px solid rgba(52, 211, 153, 0.3) !important;
  box-shadow: none !important;
  border-radius: 12px !important;
  height: 44px;
}
.custom-login-input :deep(.el-input__inner) { color: #fff !important; }

.login-error-tip {
  background: rgba(239, 68, 68, 0.2);
  border: 1px solid #f87171;
  color: #fca5a5;
  padding: 8px 12px;
  border-radius: 8px;
  font-size: 13px;
  margin-bottom: 16px;
}

.admin-login-btn {
  width: 100%;
  height: 46px;
  border-radius: 12px;
  background: linear-gradient(135deg, #10b981, #059669) !important;
  border: none !important;
  font-size: 15px;
  font-weight: 600;
  box-shadow: 0 8px 24px rgba(16,185,129,0.4) !important;
}

.login-bottom-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 24px;
  font-size: 12px;
  color: #6ee7b7;
  opacity: 0.7;
}

/* 顶栏 */
.admin-header {
  height: 64px;
  background: #0f172a;
  color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.15);
  z-index: 50;
}

.brand-zone { display: flex; align-items: center; gap: 12px; }
.logo-box { font-size: 28px; }
.brand-title { font-size: 16px; font-weight: 800; letter-spacing: 0.5px; }
.brand-sub { font-size: 11px; color: #94a3b8; }
.cross-jump-bar { display: flex; gap: 10px; }

.user-header-group { display: flex; align-items: center; gap: 12px; }

.user-badge {
  display: flex;
  align-items: center;
  gap: 10px;
  background: #1e293b;
  padding: 6px 14px;
  border-radius: 20px;
  cursor: pointer;
  transition: all 0.2s;
  border: 1px solid #334155;
}
.user-badge:hover { border-color: #3b82f6; background: #334155; }
.badge-avatar { font-size: 20px; }
.badge-name { font-size: 12px; font-weight: 700; color: #f8fafc; display: flex; align-items: center; gap: 6px; }
.badge-role { font-size: 10px; color: #94a3b8; }

.header-logout-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  background: rgba(239, 68, 68, 0.18);
  border: 1px solid #ef4444;
  color: #fca5a5;
  padding: 6px 12px;
  border-radius: 16px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
}
.header-logout-btn:hover { background: #ef4444; color: #fff; }

/* 侧边栏与主体 */
.admin-main { flex: 1; display: flex; overflow: hidden; }
.admin-sidebar {
  width: 232px;
  background: #ffffff;
  border-right: 1px solid #e2e8f0;
  display: flex;
  flex-direction: column;
  padding: 14px 10px;
  gap: 6px;
  flex-shrink: 0;
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 14px;
  font-size: 13.5px;
  font-weight: 500;
  color: #475569;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}
.menu-item:hover { background: #f1f5f9; color: #0f172a; }
.menu-item.active { background: #eff6ff; color: #2563eb; font-weight: 600; }
.menu-icon { font-size: 16px; }

.admin-content { flex: 1; min-width: 0; padding: 20px 24px; overflow-y: auto; background: #f1f5f9; }
.tab-pane { display: flex; flex-direction: column; width: 100%; }
.tab-pane > .el-card { width: 100%; }

.pane-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.pane-header h3 { margin: 0; font-size: 17px; color: #1e293b; }
.sub-desc { font-size: 12px; color: #64748b; margin-top: 3px; display: block; }

.metrics-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; }
.metric-card { padding: 16px; border-radius: 10px; color: #ffffff; }
.metric-card.bg-blue { background: linear-gradient(135deg, #2563eb, #1d4ed8); }
.metric-card.bg-green { background: linear-gradient(135deg, #10b981, #059669); }
.metric-card.bg-purple { background: linear-gradient(135deg, #8b5cf6, #6d28d9); }
.metric-card.bg-orange { background: linear-gradient(135deg, #f59e0b, #d97706); }

.m-label { font-size: 11.5px; opacity: 0.9; margin-bottom: 6px; }
.m-val { font-size: 24px; font-weight: 800; margin-bottom: 4px; }
.m-val .unit { font-size: 12px; font-weight: normal; }
.m-sub { font-size: 11px; opacity: 0.85; }

.grid-2col { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.sub-card { background: #ffffff; border: 1px solid #e2e8f0; border-radius: 10px; padding: 16px; }
.sub-card-header { display: flex; align-items: center; gap: 8px; font-size: 13.5px; font-weight: 700; color: #1e293b; margin-bottom: 12px; }

.mt-16 { margin-top: 16px; }
.mb-16 { margin-bottom: 16px; }
.ml-auto { margin-left: auto; }
.text-success { color: #10b981; }
.text-danger { color: #ef4444; }
.text-muted { color: #94a3b8; }
.font-bold { font-weight: 700; }
.font-mono { font-family: monospace; }
.flex-between { display: flex; justify-content: space-between; align-items: center; }

.salary-grid { display: grid; grid-template-columns: 1.25fr 1fr; gap: 16px; }
.salary-table-card, .salary-chat-card {
  background: #ffffff; border: 1px solid #e2e8f0; border-radius: 10px; padding: 16px; display: flex; flex-direction: column;
}
.quick-pills { display: flex; gap: 8px; margin-bottom: 10px; }
.chat-box {
  height: 380px; overflow-y: auto; border: 1px solid #f1f5f9; border-radius: 8px; padding: 12px; background: #f8fafc; display: flex; flex-direction: column; gap: 12px;
}
.chat-msg { padding: 10px 12px; border-radius: 8px; max-width: 85%; font-size: 12.5px; line-height: 1.5; }
.chat-msg.user { align-self: flex-end; background: #eff6ff; border: 1px solid #bfdbfe; }
.chat-msg.assistant { align-self: flex-start; background: #ffffff; border: 1px solid #e2e8f0; }

/* ── AI 回复 Markdown 排版（列表/表格/代码不再溢出气泡） ── */
.chat-msg .msg-content { overflow-wrap: break-word; }
.chat-msg .msg-content p { margin: 4px 0; }
.chat-msg .msg-content ul,
.chat-msg .msg-content ol { margin: 4px 0; padding-left: 18px; }
.chat-msg .msg-content li { margin: 2px 0; }
.chat-msg .msg-content h1,
.chat-msg .msg-content h2,
.chat-msg .msg-content h3,
.chat-msg .msg-content h4 { margin: 8px 0 4px; font-size: 13px; font-weight: 800; }
.chat-msg .msg-content blockquote {
  margin: 6px 0; padding: 5px 10px; border-left: 3px solid #93c5fd;
  background: #f8fafc; border-radius: 0 6px 6px 0; color: #475569;
}
.chat-msg .msg-content code {
  background: #e2e8f0; color: #0f172a; padding: 1px 4px;
  border-radius: 4px; font-size: 11.5px;
}
.chat-msg .msg-content strong { font-weight: 800; }
.md-table-scroll { overflow-x: auto; margin: 6px 0; border: 1px solid #e2e8f0; border-radius: 6px; }
.chat-msg .msg-content table { border-collapse: collapse; width: 100%; font-size: 11.5px; line-height: 1.5; }
.chat-msg .msg-content th,
.chat-msg .msg-content td { border: 1px solid #e2e8f0; padding: 5px 8px; text-align: left; white-space: nowrap; }
.chat-msg .msg-content th { background: #f1f5f9; font-weight: 700; color: #334155; }
.msg-sender { font-size: 10.5px; color: #64748b; margin-bottom: 4px; font-weight: 700; }
.chat-input-bar { display: flex; gap: 8px; margin-top: 12px; }

.mic-btn-admin {
  flex: 0 0 auto;
  width: 40px; height: 40px;
  display: flex; align-items: center; justify-content: center;
  font-size: 18px;
  background: #f1f5f9; border: 1px solid #e2e8f0; border-radius: 50%;
  cursor: pointer; transition: all 0.15s;
}
.mic-btn-admin:hover { background: #e0f2f1; border-color: #0f766e; }
.mic-btn-admin.recording { background: #fee2e2; border-color: #ef4444; animation: mic-pulse 1s ease-in-out infinite; }
@keyframes mic-pulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(239, 68, 68, 0.35); }
  50% { box-shadow: 0 0 0 6px rgba(239, 68, 68, 0); }
}
/* 附件预览（工资表图片 / Excel） */
/* 附件独立一行容器：不挤占输入栏 */
.batch-salary-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 10px 0;
}
/* 工资表里存在但系统无账号的人员行：整行红底警示，不可发放 */
:deep(.no-account-row) {
  background: #fef2f2 !important;
}
/* 发放记录弹窗：右下角可自由拖拽缩放（双保险：class 可能落在 overlay 或 dialog 元素上） */
.slip-history-dialog {
  resize: both;
  overflow: auto;
}
.slip-history-dialog .el-dialog {
  resize: both;
  overflow: auto;
  max-width: 96vw;
}
/* 附件文件卡片（上传预览与气泡内同款，对齐主流 AI 对话样式） */
.file-card {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.06);
}
.file-card-icon {
  width: 38px;
  height: 38px;
  border-radius: 9px;
  background: #22c55e;
  color: #fff;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.5px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
}
.file-card-icon-img {
  width: 38px;
  height: 38px;
  border-radius: 9px;
  object-fit: cover;
  flex: 0 0 auto;
}
.file-card-info {
  min-width: 0;
}
.file-card-name {
  font-size: 13px;
  font-weight: 600;
  color: #0f172a;
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.file-card-size {
  font-size: 11px;
  color: #94a3b8;
  margin-top: 2px;
}
.file-card-remove {
  cursor: pointer;
  color: #94a3b8;
  font-size: 14px;
  padding: 2px 4px;
  border-radius: 50%;
  flex: 0 0 auto;
}
.file-card-remove:hover {
  color: #ef4444;
  background: #fee2e2;
}
.msg-file-card {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.06);
  margin-bottom: 8px;
}
.chat-attachment-row {
  display: flex;
  padding: 0 2px;
}
.chat-attachment-row .chat-attachment-preview {
  flex: 1;
  max-width: 420px;
}
/* 用户气泡里的图片预览 */
.msg-image { margin: 4px 0 6px; }
.msg-image img { max-width: 180px; max-height: 140px; object-fit: cover; border-radius: 8px; border: 1px solid #e2e8f0; display: block; }
.chat-attachment-preview {
  display: flex; align-items: center; gap: 8px;
  padding: 6px 10px; margin: 6px 0;
  background: #f0fdfa; border: 1px dashed #0f766e; border-radius: 8px;
}
.chat-attachment-thumb { width: 36px; height: 36px; object-fit: cover; border-radius: 6px; border: 1px solid #e2e8f0; flex: 0 0 auto; }
.chat-attachment-excel { font-size: 20px; flex: 0 0 auto; }
.chat-attachment-name { flex: 1; font-size: 12px; color: #0f766e; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.chat-attachment-remove { cursor: pointer; color: #ef4444; font-size: 14px; padding: 2px 6px; }

.msg-tts-line { margin-top: 4px; text-align: right; }
.tts-link { font-size: 12px; color: #0f766e; cursor: pointer; user-select: none; }
.tts-link:hover { text-decoration: underline; }

.text-assistant-body { display: flex; flex-direction: column; gap: 12px; }
.ta-templates { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.ta-label { font-weight: 600; color: #334155; font-size: 13px; }
.ta-actions { display: flex; gap: 10px; }
.ta-result { background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 12px; }
.ta-result-title { font-weight: 700; color: #334155; margin-bottom: 6px; font-size: 13px; }
.ta-result-content { font-size: 13px; line-height: 1.7; color: #1e293b; white-space: pre-wrap; }

.user-order-card {
  background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 12px; margin-bottom: 12px; font-size: 12.5px;
}
.order-sub-info { display: flex; justify-content: space-between; color: #64748b; font-size: 11.5px; margin: 6px 0; }
.order-json-box { background: #ffffff; border: 1px dashed #cbd5e1; border-radius: 6px; padding: 8px; font-family: monospace; font-size: 11px; color: #334155; word-break: break-all; }
.order-items-box { background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 6px; padding: 4px 8px; }
.perm-full-text { font-size: 12.5px; color: #166534; font-weight: 500; }
.perm-none-text { font-size: 12.5px; color: #9a3412; display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.order-item-line { display: flex; align-items: center; gap: 8px; padding: 5px 0; font-size: 12px; color: #334155; }
.order-item-line + .order-item-line { border-top: 1px dashed #e2e8f0; }
.order-item-line .oi-name { font-weight: 500; color: #1e293b; }
.order-item-line .oi-spec { color: #94a3b8; font-size: 11px; }
.order-item-line .oi-qty { margin-left: auto; color: #64748b; }
.order-item-line .oi-price { min-width: 64px; text-align: right; font-weight: 500; color: #dc2626; }

.profile-card { font-size: 13px; line-height: 2.2; }

.analytics-ai-card {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.salary-full-card {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 16px;
  display: flex;
  flex-direction: column;
}

/* AI 对话气泡样式全面升级：消除浏览器圆点溢出，支持 Markdown 表格 */
.msg-content :deep(ul),
.msg-content :deep(ol),
.msg-content ul,
.msg-content ol {
  list-style: none !important;
  list-style-type: none !important;
  padding: 0 !important;
  padding-left: 0 !important;
  margin: 6px 0 !important;
}

.msg-content :deep(li),
.msg-content li {
  list-style: none !important;
  list-style-type: none !important;
  padding-left: 0 !important;
  margin: 3px 0 !important;
  line-height: 1.6 !important;
}

.msg-content :deep(table),
.msg-content table {
  width: 100%;
  border-collapse: collapse;
  margin: 10px 0;
  font-size: 12px;
  background: #ffffff;
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid #cbd5e1;
}

.msg-content :deep(th),
.msg-content :deep(td),
.msg-content th,
.msg-content td {
  border: 1px solid #cbd5e1;
  padding: 6px 10px;
  text-align: left;
}

.msg-content :deep(th),
.msg-content th {
  background: #f1f5f9;
  font-weight: 700;
  color: #1e293b;
}

.msg-content :deep(tr:nth-child(even) td),
.msg-content tr:nth-child(even) td {
  background: #f8fafc;
}

.msg-content :deep(code),
.msg-content code {
  background: #f1f5f9;
  padding: 2px 6px;
  border-radius: 4px;
  font-family: monospace;
  color: #0284c7;
  font-size: 11.5px;
}

.msg-content :deep(blockquote),
.msg-content blockquote {
  margin: 8px 0;
  padding: 8px 12px;
  background: #f0fdf4;
  border-left: 4px solid #16a34a;
  border-radius: 4px;
  color: #166534;
  font-size: 12px;
}

/* 商城订单与履约发货样式 */
.order-item-chip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #f8fafc;
  padding: 2px 8px;
  border-radius: 4px;
  margin-bottom: 3px;
  font-size: 11.5px;
  border: 1px solid #e2e8f0;
}
.item-qty {
  color: #059669;
  font-weight: bold;
}
.salary-calc-box {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 16px;
  margin-top: 10px;
}
.calc-row {
  display: flex;
  gap: 16px;
}
.calc-row :deep(.el-form-item) {
  flex: 1;
}
.net-salary-highlight {
  font-size: 20px;
  font-weight: 800;
  color: #dc2626;
  line-height: 32px;
}
.ai-calc-btn {
  background: linear-gradient(135deg, #3b82f6, #6366f1);
  border: none;
  font-weight: bold;
}

.ai-reasoning-card {
  background: linear-gradient(135deg, #f0fdf4 0%, #f0f9ff 100%);
  border: 1px solid #bbf7d0;
  border-radius: 8px;
  padding: 12px 14px;
  margin-bottom: 16px;
  box-shadow: 0 2px 5px rgba(0, 0, 0, 0.02);
}
.ai-reasoning-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 700;
  color: #166534;
  margin-bottom: 8px;
  font-size: 13px;
}
.ai-reasoning-item {
  font-size: 12px;
  line-height: 1.6;
  margin-bottom: 4px;
}
.ai-k {
  color: #475569;
  font-weight: 600;
}
.ai-v {
  color: #1e293b;
}
.ai-reasoning-tags {
  display: flex;
  gap: 8px;
  margin-top: 8px;
  flex-wrap: wrap;
}
.ai-reasoning-tags .badge {
  font-size: 11px;
  background: #ffffff;
  color: #0369a1;
  border: 1px solid #bae6fd;
  padding: 2px 8px;
  border-radius: 12px;
  font-weight: 500;
}


/* ── 中台 AI 调度会话历史抽屉与标签样式 ── */
.csb-session-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: #0284c7;
  background: #e0f2fe;
  padding: 2px 10px;
  border-radius: 9999px;
  border: 1px solid #bae6fd;
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.csb-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #0284c7;
}
.ah-drawer-body {
  display: flex;
  flex-direction: column;
  height: 100%;
}
.ah-drawer-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 12px;
  margin-bottom: 12px;
  border-bottom: 1px solid #e2e8f0;
}
.ah-count {
  font-size: 12px;
  color: #64748b;
  font-weight: 600;
}
.ah-session-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  overflow-y: auto;
  max-height: calc(100vh - 160px);
}
.ah-session-card {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 10px 12px;
  background: #f8fafc;
  cursor: pointer;
  transition: all 0.2s;
}
.ah-session-card:hover {
  border-color: #3b82f6;
  background: #eff6ff;
}
.ah-session-card.active {
  border-color: #2563eb;
  background: #dbeafe;
  box-shadow: 0 2px 6px rgba(37, 99, 235, 0.12);
}
.ah-card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}
.ah-sess-title {
  font-size: 13px;
  font-weight: 700;
  color: #1e293b;
  display: flex;
  align-items: center;
  gap: 6px;
}
.ah-active-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #2563eb;
}
.ah-card-bottom {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 11px;
  color: #64748b;
}
.ah-empty {
  text-align: center;
  color: #94a3b8;
  padding: 40px 0;
  font-size: 13px;
}

/* 会话历史时间分组 */
.ah-session-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 10px;
}
.ah-group-label {
  font-size: 12px;
  font-weight: 700;
  color: #94a3b8;
  padding-bottom: 3px;
  border-bottom: 1px dashed #e2e8f0;
  position: sticky;
  top: 0;
  background: #ffffff;
  z-index: 1;
}

/* 商品图片上传区 */
.upload-image-zone {
  display: flex;
  align-items: center;
  gap: 14px;
}
.product-img-uploader :deep(.el-upload) {
  border: 1px dashed #cbd5e1;
  border-radius: 8px;
  cursor: pointer;
  overflow: hidden;
  width: 90px;
  height: 90px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f8fafc;
  transition: border-color 0.2s;
}
.product-img-uploader :deep(.el-upload:hover) {
  border-color: #2563eb;
}
.upload-placeholder-icon {
  font-size: 24px;
  color: #94a3b8;
}
.upload-preview-img {
  width: 90px;
  height: 90px;
  object-fit: cover;
  display: block;
}
.upload-tips .tip-main {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}
.upload-tips .tip-sub {
  font-size: 11px;
  color: #94a3b8;
  margin: 4px 0 6px;
}
.no-img-tip {
  font-size: 11px;
  color: #cbd5e1;
}
.row-img-upload-placeholder {
  width: 52px;
  height: 52px;
  border: 1px dashed #cbd5e1;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  color: #94a3b8;
  cursor: pointer;
  transition: all 0.2s;
}
.row-img-upload-placeholder:hover {
  border-color: #2563eb;
  color: #2563eb;
}

/* 顶栏全局 AI 运营调度入口按钮 */
.header-ai-quick-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  background: linear-gradient(135deg, #0284c7, #0369a1);
  color: #ffffff;
  border: 1px solid rgba(255, 255, 255, 0.25);
  border-radius: 20px;
  padding: 6px 14px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(2, 132, 199, 0.35);
  transition: all 0.25s ease;
}
.header-ai-quick-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 14px rgba(2, 132, 199, 0.5);
  background: linear-gradient(135deg, #0369a1, #075985);
}
.ai-live-dot {
  width: 8px;
  height: 8px;
  background: #10b981;
  border-radius: 50%;
  box-shadow: 0 0 0 2px rgba(16, 185, 129, 0.4);
  animation: pulse-dot 1.8s infinite;
}
@keyframes pulse-dot {
  0% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(16, 185, 129, 0.7); }
  70% { transform: scale(1); box-shadow: 0 0 0 6px rgba(16, 185, 129, 0); }
  100% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(16, 185, 129, 0); }
}

/* 右下角全局常驻 AI 调度悬浮球 */
.global-floating-ai-ball {
  position: fixed;
  right: 28px;
  bottom: 32px;
  width: 58px;
  height: 58px;
  border-radius: 50%;
  background: linear-gradient(135deg, #0284c7, #0f172a);
  color: #ffffff;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 6px 20px rgba(2, 132, 199, 0.45);
  z-index: 2500;
  transition: all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
  border: 2px solid rgba(255, 255, 255, 0.3);
}
.global-floating-ai-ball:hover {
  transform: scale(1.1) translateY(-3px);
  box-shadow: 0 10px 25px rgba(2, 132, 199, 0.6);
}
.ai-ball-icon {
  font-size: 20px;
  line-height: 1;
}
.ai-ball-tip {
  font-size: 10px;
  font-weight: bold;
  letter-spacing: 0.5px;
  margin-top: 2px;
}
</style>
