import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import axios from 'axios'
import App from './App.vue'

// Axios 请求拦截器：自动携带商城用户 JWT Token
axios.interceptors.request.use(config => {
  const token = localStorage.getItem('mall_token')
  if (token) {
    config.headers['Authorization'] = `Bearer ${token}`
  }
  return config
})
// 响应拦截器：401 清除本地会话
axios.interceptors.response.use(
  res => res,
  err => {
    if (err.response?.status === 401) {
      localStorage.removeItem('mall_token')
      localStorage.removeItem('mall_user')
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