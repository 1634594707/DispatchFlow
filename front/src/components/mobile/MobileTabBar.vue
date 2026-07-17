<template>
  <nav class="mobile-tab-bar" :class="{ 'tab-bar-hidden': !visible }">
    <router-link
      v-for="tab in tabs"
      :key="tab.path"
      :to="tab.path"
      class="tab-item"
      :class="{ 'tab-active': isActive(tab) }"
    >
      <span class="tab-icon" v-html="isActive(tab) ? tab.iconActive : tab.icon" />
      <span class="tab-label">{{ tab.label }}</span>
      <span v-if="tab.badge && tab.badge > 0" class="tab-badge">{{ tab.badge > 99 ? '99+' : tab.badge }}</span>
    </router-link>
  </nav>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

interface TabItem {
  path: string
  label: string
  icon: string
  iconActive: string
  badge?: number
}

const props = defineProps<{
  activeOrderCount?: number
  visible?: boolean
}>()

const route = useRoute()

const tabs = computed<TabItem[]>(() => [
  {
    path: '/mobile/order',
    label: '首页',
    icon: '<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M3 9.5L12 3l9 6.5V20a1 1 0 0 1-1 1h-5v-7h-6v7H4a1 1 0 0 1-1-1V9.5z"/></svg>',
    iconActive: '<svg viewBox="0 0 24 24" width="24" height="24" fill="#1989fa" stroke="#1989fa" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 9.5L12 3l9 6.5V20a1 1 0 0 1-1 1h-5v-7h-6v7H4a1 1 0 0 1-1-1V9.5z"/></svg>',
  },
  {
    path: '/mobile/orders',
    label: '订单',
    icon: '<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M9 4h6a1 1 0 0 1 1 1v1h3a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h3V5a1 1 0 0 1 1-1z"/><path d="M9 12h6M9 16h4"/></svg>',
    iconActive: '<svg viewBox="0 0 24 24" width="24" height="24" fill="#1989fa" stroke="#1989fa" stroke-width="1" stroke-linecap="round" stroke-linejoin="round"><path d="M9 4h6a1 1 0 0 1 1 1v1h3a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h3V5a1 1 0 0 1 1-1z"/></svg>',
    badge: props.activeOrderCount || 0,
  },
  {
    path: '/mobile/profile',
    label: '我的',
    icon: '<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="8" r="4"/><path d="M4 21v-1a6 6 0 0 1 6-6h4a6 6 0 0 1 6 6v1"/></svg>',
    iconActive: '<svg viewBox="0 0 24 24" width="24" height="24" fill="#1989fa" stroke="#1989fa" stroke-width="1" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="8" r="4"/><path d="M4 21v-1a6 6 0 0 1 6-6h4a6 6 0 0 1 6 6v1"/></svg>',
  },
])

function isActive(tab: TabItem): boolean {
  return route.path === tab.path
}
</script>

<style scoped lang="less">
.mobile-tab-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  z-index: 100;
  display: flex;
  align-items: stretch;
  justify-content: space-around;
  height: calc(56px + env(safe-area-inset-bottom, 0px));
  padding-bottom: env(safe-area-inset-bottom, 0px);
  background: #ffffff;
  border-top: 1px solid #f0f0f0;
  box-shadow: 0 -2px 12px rgba(0, 0, 0, 0.04);
  transition: transform 0.3s ease, opacity 0.3s ease;

  &.tab-bar-hidden {
    transform: translateY(100%);
    opacity: 0;
    pointer-events: none;
  }
}

.tab-item {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  flex: 1;
  text-decoration: none;
  color: #999;
  transition: color 0.2s ease;

  &.tab-active {
    color: #1989fa;
  }

  &:active {
    transform: scale(0.92);
  }
}

.tab-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  transition: transform 0.2s ease;

  .tab-active & {
    transform: translateY(-1px) scale(1.08);
  }
}

.tab-label {
  font-size: 10px;
  font-weight: 500;
  letter-spacing: 0.02em;
  line-height: 1;
}

.tab-badge {
  position: absolute;
  top: 4px;
  right: 50%;
  transform: translateX(18px);
  min-width: 16px;
  height: 16px;
  padding: 0 5px;
  border-radius: 8px;
  background: #ff4d4f;
  color: #fff;
  font-size: 9px;
  font-weight: 700;
  line-height: 16px;
  text-align: center;
  box-shadow: 0 0 0 2px #ffffff;
}
</style>
