<template>
  <nav class="mobile-tab-bar" :class="{ 'tab-bar-hidden': !visible }">
    <router-link
      v-for="tab in tabs"
      :key="tab.path"
      :to="tab.path"
      class="tab-item"
      :class="{ 'tab-active': isActive(tab) }"
    >
      <span class="tab-icon"><component :is="tab.icon" /></span>
      <span class="tab-label">{{ tab.label }}</span>
      <span v-if="tab.badge && tab.badge > 0" class="tab-badge">{{
        tab.badge > 99 ? '99+' : tab.badge
      }}</span>
    </router-link>
  </nav>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { Component } from 'vue'
import { useRoute } from 'vue-router'
import { FileTextOutlined, SendOutlined, UserOutlined } from '@ant-design/icons-vue'

interface TabItem {
  path: string
  label: string
  icon: Component
  badge?: number
}

const props = withDefaults(
  defineProps<{
    activeOrderCount?: number
    visible?: boolean
  }>(),
  {
    activeOrderCount: 0,
    visible: true,
  },
)

const route = useRoute()

const tabs = computed<TabItem[]>(() => [
  {
    path: '/mobile/order',
    label: '下单',
    icon: SendOutlined,
  },
  {
    path: '/mobile/orders',
    label: '订单',
    icon: FileTextOutlined,
    badge: props.activeOrderCount || 0,
  },
  {
    path: '/mobile/profile',
    label: '我的',
    icon: UserOutlined,
  },
])

function isActive(tab: TabItem): boolean {
  return route.path === tab.path
}
</script>

<style scoped lang="less">
.mobile-tab-bar {
  position: fixed;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 100;
  display: flex;
  align-items: stretch;
  justify-content: space-around;
  height: calc(56px + env(safe-area-inset-bottom, 0px));
  padding-bottom: env(safe-area-inset-bottom, 0px);
  border-top: 1px solid var(--fsd-border);
  background: var(--fsd-bg-base);
  transition: opacity var(--fsd-transition-base);

  &.tab-bar-hidden {
    opacity: 0;
    pointer-events: none;
  }
}

.tab-item {
  position: relative;
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  min-height: var(--fsd-touch-target-min);
  border-top: 2px solid transparent;
  color: var(--fsd-text-tertiary);
  text-decoration: none;
  transition:
    color var(--fsd-transition-base),
    background-color var(--fsd-transition-base);

  &.tab-active {
    border-top-color: var(--fsd-accent);
    background: var(--fsd-accent-selected);
    color: var(--fsd-accent-strong);
  }

  &:active {
    background: var(--fsd-bg-hover);
  }

  &:focus-visible {
    outline: 2px solid var(--fsd-accent);
    outline-offset: -2px;
  }
}

.tab-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
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
  min-width: 16px;
  height: 16px;
  padding: 0 5px;
  border: 1px solid var(--fsd-bg-base);
  border-radius: var(--fsd-radius-sm);
  background: var(--fsd-error);
  color: var(--fsd-text-on-action);
  font-size: 9px;
  font-weight: var(--fsd-font-semibold);
  line-height: 14px;
  text-align: center;
  transform: translateX(18px);
}
</style>
