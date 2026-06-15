import { createApp } from 'vue'
import { createPinia } from 'pinia'
import Antd from 'ant-design-vue'
import App from './App.vue'
import router from './router'
import 'ant-design-vue/dist/reset.css'
import './styles/tokens.css'
import './styles/global.less'
import { stopAllSSEConnections } from '@/utils/sseConnectionRegistry'
import { initResponsive } from '@/composables/useResponsive'

// Eagerly initialize responsive breakpoint detection
initResponsive()

if (import.meta.hot) {
  import.meta.hot.dispose(() => stopAllSSEConnections())
}

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(Antd)

app.mount('#app')
