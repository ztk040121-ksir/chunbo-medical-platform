import { createApp } from 'vue'
import AppRoot from './AppRoot.vue'
import ElementPlus, { ElMessage } from 'element-plus'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import router from './router/index.js'
import axios from 'axios'

// Axios 请求拦截器：自动携带 JWT Token
axios.interceptors.request.use(config => {
  const token = localStorage.getItem('chunbo_jwt_token')
  if (token) {
    config.headers['Authorization'] = `Bearer ${token}`
  }
  return config
})

// Axios 响应拦截器：401 自动跳转登录，5xx 统一错误提示
axios.interceptors.response.use(
  res => res,
  err => {
    if (err.response?.status === 401) {
      const path = window.location.pathname
      if (path !== '/login') {
        localStorage.removeItem('chunbo_jwt_token')
        localStorage.removeItem('chunbo_username')
        localStorage.removeItem('chunbo_display_name')
        router.push('/login')
      }
    } else if (err.response?.status >= 500) {
      const msg = err.response?.data?.message || '服务器异常，请稍后重试'
      ElMessage.error(msg)
    }
    return Promise.reject(err)
  }
)

const app = createApp(AppRoot)
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}
app.use(ElementPlus)
app.use(router)
app.mount('#app')