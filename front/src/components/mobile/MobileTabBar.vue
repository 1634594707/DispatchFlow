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
  transition:
    transform 0.3s ease,
    opacity 0.3s ease;

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
