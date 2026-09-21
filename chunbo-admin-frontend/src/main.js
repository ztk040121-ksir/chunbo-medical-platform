import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import axios from 'axios'
import App from './App.vue'

// Axios 请求拦截器：自动携带管理员/员工 JWT Token
axios.interceptors.request.use(config => {
  const token = localStorage.getItem('chunbo_admin_token')
  if (token) {
    config.headers['Authorization'] = `Bearer ${token}`
  }
  return config
})
// 响应拦截器：401 跳转登录
// 仅当原本持有 token（登录态失效/过期）时才清理并刷新一次；
// 未登录态下的 401 不刷新，否则页面加载期的 401 会触发 reload 死循环（闪屏）
axios.interceptors.response.use(
  res => res,
  err => {
    if (err.response?.status === 401) {
      const hadToken = !!localStorage.getItem('chunbo_admin_token')
      localStorage.removeItem('chunbo_admin_token')
      localStorage.removeItem('chunbo_admin_role')
      localStorage.removeItem('chunbo_admin_name')
      localStorage.removeItem('chunbo_admin_staff_id')
      localStorage.removeItem('chunbo_admin_dept')
      localStorage.removeItem('chunbo_admin_title')
      if (hadToken) location.reload()
    }
    return Promise.reject(err)
  }
)

const app = createApp(App)
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}
app.use(ElementPlus)
app.mount('#app')