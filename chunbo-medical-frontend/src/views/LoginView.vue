<template>
  <div class="login-bg">
    <!-- 背景流光与粒子氛围 -->
    <div class="glow-orb orb-1"></div>
    <div class="glow-orb orb-2"></div>
    <div class="glow-orb orb-3"></div>

    <div class="login-container">
      <!-- 登录主卡片 -->
      <div class="login-card">
        <!-- 品牌标识 -->
        <div class="brand-header">
          <div class="brand-logo-wrap">
            <svg class="brand-icon" viewBox="0 0 48 48" fill="none" xmlns="http://www.w3.org/2000/svg">
              <rect width="48" height="48" rx="14" fill="url(#brand-grad)" />
              <path d="M24 14V34M14 24H34" stroke="#ffffff" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"/>
              <defs>
                <linearGradient id="brand-grad" x1="0" y1="0" x2="48" y2="48" gradientUnits="userSpaceOnUse">
                  <stop stop-color="#10B981" />
                  <stop offset="1" stop-color="#059669" />
                </linearGradient>
              </defs>
            </svg>
          </div>
          <div class="brand-info">
            <h1 class="brand-title">春播万象云诊所</h1>
            <span class="brand-subtitle">AI 基层智慧医疗全栈工作台 · v2.0.0</span>
          </div>
        </div>

        <div class="welcome-text">
          <h2>医生工作台登录</h2>
          <p>请使用授权的医护人员工号与密码登录系统</p>
        </div>

        <!-- 登录表单 -->
        <el-form :model="form" @submit.prevent="handleLogin" class="login-form">
          <el-form-item>
            <el-input
              v-model="form.username"
              placeholder="请输入医生工号 / 登录账号"
              size="large"
              class="custom-input"
              prefix-icon="User"
              clearable
              @keyup.enter="handleLogin"
            />
          </el-form-item>

          <el-form-item>
            <el-input
              v-model="form.password"
              type="password"
              placeholder="请输入登录密码"
              size="large"
              class="custom-input"
              prefix-icon="Lock"
              show-password
              @keyup.enter="handleLogin"
            />
          </el-form-item>

          <!-- 错误提示横幅 -->
          <transition name="fade">
            <div v-if="errorMsg" class="error-banner">
              <svg class="error-icon" viewBox="0 0 20 20" fill="currentColor">
                <path fill-rule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7 4a1 1 0 11-2 0 1 1 0 012 0zm-1-9a1 1 0 00-1 1v4a1 1 0 102 0V6a1 1 0 00-1-1z" clip-rule="evenodd" />
              </svg>
              <span>{{ errorMsg }}</span>
            </div>
          </transition>

          <!-- 登录按钮 -->
          <el-button
            type="primary"
            size="large"
            class="submit-btn"
            :loading="loading"
            @click="handleLogin"
            native-type="submit"
          >
            {{ loading ? '正在验证身份...' : '登 录 工 作 台' }}
          </el-button>
        </el-form>

        <div class="card-footer">
          <span>春播万象基层医疗数字化系统 v2.0.0</span>
          <span class="security-badge">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="shield-icon">
              <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
            </svg>
            JWT 安全加密认证
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'

const router = useRouter()
const loading = ref(false)
const errorMsg = ref('')

const form = reactive({
  username: '',
  password: ''
})

const handleLogin = async () => {
  if (!form.username.trim() || !form.password) {
    errorMsg.value = '请输入登录工号和密码'
    return
  }
  loading.value = true
  errorMsg.value = ''
  try {
    const res = await axios.post('/api/auth/login', {
      username: form.username.trim(),
      password: form.password
    })
    if (res.data && res.data.success) {
      localStorage.setItem('chunbo_jwt_token', res.data.token)
      localStorage.setItem('chunbo_username', res.data.username)
      localStorage.setItem('chunbo_display_name', res.data.displayName || res.data.username)
      localStorage.setItem('chunbo_doctor_id', res.data.doctorId || 'DOC_1001')
      localStorage.setItem('chunbo_department', res.data.department || '全科门诊')
      localStorage.setItem('chunbo_title', res.data.title || '主治医师')
      router.push('/')
    } else {
      errorMsg.value = res.data?.message || '登录失败，请检查工号密码或联系院办'
    }
  } catch (err) {
    console.error('Login error:', err)
    if (err.response) {
      if (err.response.status === 401) {
        errorMsg.value = err.response.data?.message || '工号或密码错误，请联系院办OA系统核实'
      } else {
        errorMsg.value = err.response.data?.message || `服务器返回错误 [${err.response.status}]`
      }
    } else if (err.request) {
      errorMsg.value = '无法连接到后端服务(8080端口)，请确认后端已启动！'
    } else {
      errorMsg.value = '登录请求异常: ' + (err.message || '未知错误')
    }
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-bg {
  min-height: 100vh;
  background: radial-gradient(circle at 10% 20%, #064e3b 0%, #022c22 40%, #061715 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif;
  position: relative;
  overflow: hidden;
  padding: 20px;
}

/* 发光环境光球 */
.glow-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(90px);
  opacity: 0.35;
  pointer-events: none;
  animation: orbFloat 10s ease-in-out infinite alternate;
}
.orb-1 {
  width: 520px;
  height: 520px;
  background: radial-gradient(circle, #10b981, #059669);
  top: -120px;
  left: -80px;
}
.orb-2 {
  width: 440px;
  height: 440px;
  background: radial-gradient(circle, #0ea5e9, #0284c7);
  bottom: -100px;
  right: -60px;
  animation-delay: -4s;
}
.orb-3 {
  width: 320px;
  height: 320px;
  background: radial-gradient(circle, #14b8a6, #0f766e);
  top: 45%;
  left: 65%;
  animation-delay: -7s;
}

@keyframes orbFloat {
  0% { transform: translateY(0) scale(1); }
  100% { transform: translateY(-40px) scale(1.08); }
}

.login-container {
  width: 100%;
  max-width: 440px;
  position: relative;
  z-index: 10;
}

.login-card {
  background: rgba(15, 34, 28, 0.78);
  backdrop-filter: blur(28px);
  -webkit-backdrop-filter: blur(28px);
  border: 1px solid rgba(52, 211, 153, 0.22);
  border-radius: 24px;
  padding: 46px 38px 34px;
  box-shadow: 0 30px 70px rgba(0, 0, 0, 0.55), 0 0 30px rgba(16, 185, 129, 0.12);
  animation: cardFadeUp 0.6s cubic-bezier(0.16, 1, 0.3, 1);
}

@keyframes cardFadeUp {
  from {
    opacity: 0;
    transform: translateY(30px) scale(0.97);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

/* 品牌头部 */
.brand-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 28px;
}

.brand-logo-wrap {
  width: 52px;
  height: 52px;
  flex-shrink: 0;
  filter: drop-shadow(0 6px 14px rgba(16, 185, 129, 0.45));
}
.brand-icon {
  width: 100%;
  height: 100%;
}

.brand-title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 0.5px;
  color: #ecfdf5;
  line-height: 1.25;
}

.brand-subtitle {
  font-size: 12px;
  color: #a7f3d0;
  opacity: 0.85;
  display: block;
  margin-top: 4px;
  letter-spacing: 0.2px;
}

/* 欢迎文案 */
.welcome-text {
  margin-bottom: 26px;
}
.welcome-text h2 {
  margin: 0 0 6px;
  font-size: 24px;
  font-weight: 600;
  color: #ffffff;
}
.welcome-text p {
  margin: 0;
  font-size: 13px;
  color: #94a3b8;
}

/* 表单与输入框 */
.login-form :deep(.el-form-item) {
  margin-bottom: 20px;
}

.login-form :deep(.custom-input .el-input__wrapper) {
  background: rgba(2, 44, 34, 0.55) !important;
  border: 1px solid rgba(52, 211, 153, 0.25) !important;
  box-shadow: none !important;
  border-radius: 12px !important;
  padding: 4px 14px !important;
  transition: all 0.25s ease;
}
.login-form :deep(.custom-input .el-input__wrapper:hover) {
  border-color: rgba(52, 211, 153, 0.55) !important;
  background: rgba(2, 44, 34, 0.75) !important;
}
.login-form :deep(.custom-input .el-input__wrapper.is-focus) {
  border-color: #10b981 !important;
  box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.25) !important;
  background: rgba(2, 44, 34, 0.85) !important;
}
.login-form :deep(.custom-input .el-input__inner) {
  color: #ffffff !important;
  font-size: 14px;
  height: 42px;
}
.login-form :deep(.custom-input .el-input__inner::placeholder) {
  color: #6ee7b7 !important;
  opacity: 0.5;
}
.login-form :deep(.custom-input .el-input__prefix-inner .el-icon) {
  color: #34d399 !important;
  font-size: 18px;
}

/* 错误提示 */
.error-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  background: rgba(239, 68, 68, 0.16);
  border: 1px solid rgba(248, 113, 113, 0.4);
  color: #fca5a5;
  padding: 10px 14px;
  border-radius: 10px;
  font-size: 13px;
  margin-bottom: 20px;
  line-height: 1.4;
}
.error-icon {
  width: 18px;
  height: 18px;
  flex-shrink: 0;
  color: #f87171;
}

.fade-enter-active, .fade-leave-active {
  transition: opacity 0.2s, transform 0.2s;
}
.fade-enter-from, .fade-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

/* 提交按钮 */
.submit-btn {
  width: 100%;
  height: 48px;
  border-radius: 12px;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 2px;
  background: linear-gradient(135deg, #10b981 0%, #059669 100%) !important;
  border: none !important;
  box-shadow: 0 8px 24px rgba(16, 185, 129, 0.38) !important;
  color: #ffffff !important;
  margin-top: 4px;
  cursor: pointer;
  transition: all 0.2s ease !important;
}
.submit-btn:hover {
  background: linear-gradient(135deg, #34d399 0%, #10b981 100%) !important;
  box-shadow: 0 10px 30px rgba(16, 185, 129, 0.5) !important;
  transform: translateY(-1.5px);
}
.submit-btn:active {
  transform: translateY(0);
}

/* 页脚 */
.card-footer {
  margin-top: 30px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  color: #6ee7b7;
  opacity: 0.65;
}
.security-badge {
  display: flex;
  align-items: center;
  gap: 5px;
}
.shield-icon {
  width: 14px;
  height: 14px;
}
</style>