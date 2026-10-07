import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'
import { resolve } from 'path'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
    },
  },
  server: {
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,                                 // 改变源，解决跨域问题
      },
      // 上传文件静态资源代理：图片地址改为相对路径后由这里转发到后端
      '/files': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
    },
  },
})
