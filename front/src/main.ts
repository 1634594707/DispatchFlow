import { createApp } from 'vue'
import { createPinia } from 'pinia'
import Antd from 'ant-design-vue'
import App from './App.vue'
import router from './router'
import 'ant-design-vue/dist/reset.css'
import './styles/tokens.css'
import './styles/global.less'
// P0-1: 自托管字体（替代 Google Fonts CDN，消除 CSP 违规与外部延迟）
import '@fontsource/geist/400.css'
import '@fontsource/geist/500.css'
import '@fontsource/geist/600.css'
import '@fontsource/geist/700.css'
import '@fontsource/geist-mono/400.css'
import '@fontsource/geist-mono/500.css'
import '@fontsource/geist-mono/600.css'
import '@fontsource/plus-jakarta-sans/500.css'
import '@fontsource/plus-jakarta-sans/600.css'
import '@fontsource/plus-jakarta-sans/700.css'
import '@fontsource/plus-jakarta-sans/800.css'
import { stopAllSSEConnections } from '@/utils/sseConnectionRegistry'
import { initResponsive } from '@/composables/useResponsive'

// Eagerly initialize responsive breakpoint detection
initResponsive()

if (import.meta.hot) {
  import.meta.hot.dispose(() => stopAllSSEConnections())
}

const app = createApp(App)

// P2-1: 全局错误处理器 — 捕获 Vue 组件内未处理异常
app.config.errorHandler = (err, _instance, info) => {
  console.error('[Vue Error]', info, err)
}

// P2-1: 捕获未处理的 Promise 拒绝
window.addEventListener('unhandledrejection', (event) => {
  console.error('[Unhandled Rejection]', event.reason)
})

// P2-1: 捕获全局资源加载错误
window.addEventListener('error', (event) => {
  const target = event.target as EventTarget | null
  if (!target) return
  const el = target as HTMLElement
  if (!el.tagName) return
  // src / href 仅存在于特定元素上，需分别断言类型
  const src = (el as HTMLImageElement | HTMLScriptElement).src
  const href = (el as HTMLLinkElement).href
  console.error('[Resource Error]', src || href)
}, true)

app.use(createPinia())
app.use(router)
app.use(Antd)

app.mount('#app')
