import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  // Keep Vite's generated dependency cache outside node_modules. This avoids
  // Windows file-lock issues after the workspace is moved while a preview is open.
  cacheDir: '.vite-cache',
  plugins: [vue()],
  server: {
    proxy: {
      '/api': 'http://127.0.0.1:8080',
    },
  },
})
