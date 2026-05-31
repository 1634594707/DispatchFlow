import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'
import { visualizer } from 'rollup-plugin-visualizer'

export default defineConfig(({ mode }) => ({
  plugins: [
    vue(),
    mode === 'analyze'
      ? visualizer({
          filename: 'dist/bundle-stats.html',
          gzipSize: true,
          open: false,
        })
      : undefined,
  ].filter(Boolean),
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
    },
  },
  css: {
    preprocessorOptions: {
      less: {
        modifyVars: {
          'font-family': "'Plus Jakarta Sans', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif",
        },
        javascriptEnabled: true,
      },
    },
  },
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
}))
