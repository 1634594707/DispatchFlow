<template>
  <a-config-provider :theme="themeConfig" :locale="zhCN">
    <router-view />

    <!-- PWA Install Prompt — 4-state model (P0-1, P2-7, P2-8):
         'idle' = no disposition; 'dismissed' = 暂不 (24h cooldown);
         'later' = 稍后提醒 (7d cooldown); 'never' = 不再提醒; 'installed' = 已安装。
         Keyboard accessible: tab to focus, Enter/Space to trigger, Esc to dismiss. -->
    <div
      v-if="pwa.canInstall.value"
      class="pwa-install-bar"
      role="dialog"
      aria-labelledby="pwa-install-text"
      aria-describedby="pwa-install-desc"
      tabindex="-1"
    >
      <span id="pwa-install-text" class="pwa-install-text">安装 DispatchFlow 到桌面，获得更快的访问体验</span>
      <span id="pwa-install-desc" class="pwa-install-desc">点击「安装」立即添加到桌面；「暂不」24 小时内不再提示。</span>
      <div class="pwa-install-actions">
        <a-button size="small" type="primary" @click="handleInstall">安装</a-button>
        <a-button size="small" type="text" @click="pwa.dismissInstall()">暂不</a-button>
        <a-button size="small" type="text" @click="pwa.neverRemindInstall()">不再提醒</a-button>
      </div>
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
import zhCN from 'ant-design-vue/es/locale/zh_CN'
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
  background: var(--fsd-bg-base);
  border: 1px solid var(--fsd-border);
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.4);
  backdrop-filter: blur(12px);
  white-space: nowrap;
}

.pwa-install-bar:focus-visible {
  outline: 2px solid var(--fsd-accent, #2DE08A);
  outline-offset: 2px;
}

.pwa-install-text {
  font-size: 13px;
  color: var(--fsd-text-secondary);
  font-weight: 600;
}

.pwa-install-desc {
  font-size: 11px;
  color: var(--fsd-text-tertiary);
}

.pwa-install-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}
</style>
