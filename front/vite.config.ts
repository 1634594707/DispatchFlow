import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'
import { visualizer } from 'rollup-plugin-visualizer'
import { VitePWA } from 'vite-plugin-pwa'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')

  return {
  plugins: [
    vue(),
    VitePWA({
      strategies: 'injectManifest',
      srcDir: 'src',
      filename: 'sw.ts',
      registerType: 'autoUpdate',
      includeAssets: ['favicon.svg', 'icons/*.svg', 'badge-72x72.svg'],
      manifest: {
        id: '/?source=pwa',
        name: 'DispatchFlow 无人车调度平台',
        short_name: 'DispatchFlow',
        description: '无人车调度管理平台 — 调度工作台、车辆监控、运营分析',
        theme_color: '#0D1117',
        background_color: '#0D1117',
        display: 'standalone',
        orientation: 'any',
        start_url: '/workbench',
        scope: '/',
        categories: ['business', 'productivity', 'utilities'],
        lang: 'zh-CN',
        icons: [
          {
            src: '/icons/icon-192x192.svg',
            sizes: '192x192',
            type: 'image/svg+xml',
            purpose: 'any maskable',
          },
          {
            src: '/icons/icon-512x512.svg',
            sizes: '512x512',
            type: 'image/svg+xml',
            purpose: 'any maskable',
          },
        ],
        shortcuts: [
          {
            name: '调度工作台',
            short_name: '工作台',
            url: '/workbench',
          },
          {
            name: '车辆监控',
            short_name: '车辆',
            url: '/vehicles',
          },
          {
            name: '运营分析',
            short_name: '分析',
            url: '/analytics',
          },
        ],
      },
      workbox: {
        globPatterns: ['**/*.{js,css,html,svg,png,ico,woff2}'],
      },
      injectManifest: {
        globPatterns: ['**/*.{js,css,html,svg,png,ico,woff2}'],
      },
      devOptions: {
        // 开发环境禁用 SW 注册，避免 sw.ts 在 dev 下被作为模块加载导致的 MIME 类型错误；
        // 生产环境仍通过构建产物正常注册。
        enabled: false,
        type: 'module',
        navigateFallback: 'index.html',
      },
    }),
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
        target: env.VITE_API_PROXY || 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    // P2-3: 构建目标与 sourcemap 配置
    target: 'es2020',
    sourcemap: mode === 'analyze',
    // Phase 5 评估结论：antd 组件库拆出图标后仍约 1.2MB（gzip ~373KB），
    // 为框架固有成本且被独立长缓存；阈值放宽至 1300 并保留对更大回归的告警
    chunkSizeWarningLimit: 1300,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) {
            return
          }
          // 路线图 Phase 5：antd 图标库独立分包（可并行加载与独立缓存），
          // 组件库单独成块；仅按包名拆分避免循环 chunk
          if (id.includes('@ant-design/icons-vue')) {
            return 'antd-icons'
          }
          if (id.includes('ant-design-vue')) {
            return 'antd'
          }
          if (id.includes('leaflet')) {
            return 'leaflet'
          }
          if (id.includes('@amap/amap-jsapi-loader') || id.includes('amap-jsapi')) {
            return 'amap'
          }
          if (id.includes('@fontsource')) {
            return 'fonts'
          }
          if (id.includes('vue') || id.includes('pinia') || id.includes('vue-router')) {
            return 'vue-vendor'
          }
          return 'vendor'
        },
      },
    },
  },
  }
})
