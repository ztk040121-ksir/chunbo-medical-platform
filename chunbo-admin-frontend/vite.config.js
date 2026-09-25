import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  // 三个前端共享 node_modules，必须用独立缓存目录，避免同时 dev 时预优化产物互相踩踏
  cacheDir: 'node_modules/.vite-admin',
  server: {
    port: 5174,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true
      },
      '/uploads': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true
      }
    }
  }
})
