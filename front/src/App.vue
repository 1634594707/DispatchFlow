<template>
  <a-config-provider :theme="themeConfig">
    <router-view />

    <!-- PWA Install Prompt -->
    <div v-if="pwa.canInstall.value" class="pwa-install-bar">
      <span class="pwa-install-text">安装 DispatchFlow 到桌面，获得更快的访问体验</span>
      <a-button size="small" type="primary" @click="handleInstall">安装</a-button>
      <a-button size="small" type="text" @click="pwa.canInstall.value = false">暂不</a-button>
    </div>

    <!-- PWA Update Notification -->
    <a-notification
      v-if="pwa.hasUpdate.value"
      :visible="pwa.hasUpdate.value"
      message="有新版本可用"
      description="已下载新版本，点击刷新以应用更新。"
      placement="bottomRight"
      @close="pwa.dismissUpdate()"
    >
      <template #btn>
        <a-button size="small" type="primary" @click="pwa.activateUpdate()">刷新</a-button>
      </template>
    </a-notification>
  </a-config-provider>
</template>

<script setup lang="ts">
import { h, watch } from 'vue'
import { Button, notification } from 'ant-design-vue'
import { usePWAInstall } from '@/composables/usePWAInstall'
import { themeConfig } from '@/config/theme'

const pwa = usePWAInstall()

watch(
  () => pwa.hasUpdate.value,
  hasUpdate => {
    if (!hasUpdate) {
      notification.destroy()
      return
    }
    notification.info({
      key: 'pwa-update',
      message: '有新版本可用',
      description: '已下载新版本，点击刷新以应用更新。',
      placement: 'bottomRight',
      duration: 0,
      btn: () => h(Button, { size: 'small', type: 'primary', onClick: pwa.activateUpdate }, '刷新'),
      onClose: pwa.dismissUpdate,
    })
  },
)

async function handleInstall() {
  const ok = await pwa.install()
  if (!ok) {
    console.warn('[PWA] User declined install')
  }
}
</script>

<style scoped>
.pwa-install-bar {
  position: fixed;
  bottom: 24px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 1050;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 20px;
  background: #1A1D23;
  border: 1px solid #30363D;
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.4);
  backdrop-filter: blur(12px);
  white-space: nowrap;
}

.pwa-install-text {
  font-size: 13px;
  color: #8B949E;
}
</style>