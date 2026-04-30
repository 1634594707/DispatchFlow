<template>
  <a-layout class="fsd-layout">
    <a-layout-sider
      v-model:collapsed="collapsed"
      :width="208"
      :collapsed-width="64"
      :trigger="null"
      collapsible
      class="fsd-sider"
    >
      <div class="sider-logo">
        <div class="logo-icon">
          <svg viewBox="0 0 32 32" fill="none" xmlns="http://www.w3.org/2000/svg">
            <rect width="32" height="32" rx="8" fill="url(#logo-grad)" />
            <path d="M8 22L16 10L24 22H8Z" fill="white" opacity="0.9" />
            <circle cx="16" cy="18" r="2" fill="white" />
            <defs>
              <linearGradient id="logo-grad" x1="0" y1="0" x2="32" y2="32">
                <stop stop-color="#00B4D8" />
                <stop offset="1" stop-color="#0077B6" />
              </linearGradient>
            </defs>
          </svg>
        </div>
        <transition name="fade">
          <div v-if="!collapsed" class="logo-text">
            <span class="logo-title">FSD-Core</span>
            <span class="logo-sub">智能调度平台</span>
          </div>
        </transition>
      </div>

      <a-menu
        v-model:selectedKeys="selectedKeys"
        theme="dark"
        mode="inline"
        @click="handleMenuClick"
      >
        <a-menu-item key="dashboard">
          <template #icon><DashboardOutlined /></template>
          <span>调度看板</span>
        </a-menu-item>
        <a-menu-item key="orders">
          <template #icon><FileTextOutlined /></template>
          <span>订单管理</span>
        </a-menu-item>
        <a-menu-item key="tasks">
          <template #icon><CarOutlined /></template>
          <span>调度任务</span>
        </a-menu-item>
        <a-menu-item key="vehicles">
          <template #icon><ToolOutlined /></template>
          <span>车辆管理</span>
        </a-menu-item>
        <a-menu-item key="vehicle-tracking">
          <template #icon><HeatMapOutlined /></template>
          <span>车辆监控大屏</span>
        </a-menu-item>
        <a-menu-item key="exceptions">
          <template #icon>
            <a-badge :count="exceptionStore.openCount" :offset="[6, 0]" :overflow-count="99">
              <AlertOutlined />
            </a-badge>
          </template>
          <span>异常任务</span>
        </a-menu-item>
      </a-menu>
    </a-layout-sider>

    <a-layout>
      <a-layout-header class="fsd-header">
        <div class="header-left">
          <a-button
            type="text"
            class="trigger-btn"
            @click="collapsed = !collapsed"
          >
            <MenuFoldOutlined v-if="!collapsed" />
            <MenuUnfoldOutlined v-else />
          </a-button>
          <a-breadcrumb class="header-breadcrumb">
            <a-breadcrumb-item>
              <router-link to="/dashboard">首页</router-link>
            </a-breadcrumb-item>
            <a-breadcrumb-item v-for="(item, index) in breadcrumbItems" :key="index">
              <router-link v-if="index < breadcrumbItems.length - 1 && item.path" :to="item.path">
                {{ item.label }}
              </router-link>
              <span v-else>{{ item.label }}</span>
            </a-breadcrumb-item>
          </a-breadcrumb>
        </div>
        <div class="header-right">
          <a-tooltip title="刷新数据">
            <a-button type="text" class="header-icon-btn">
              <ReloadOutlined />
            </a-button>
          </a-tooltip>
          <a-popover trigger="click" placement="bottomRight">
            <template #content>
              <div style="width: 280px;">
                <div class="notification-header">系统通知</div>
                <a-empty :image="simpleImage" description="暂无通知" />
              </div>
            </template>
            <a-button type="text" class="header-icon-btn notification-btn">
              <a-badge :count="3" :offset="[-4, 4]">
                <BellOutlined />
              </a-badge>
            </a-button>
          </a-popover>
          <a-dropdown>
            <div class="user-info">
              <a-avatar :size="32" style="background: var(--fsd-accent);">
                <template #icon><UserOutlined /></template>
              </a-avatar>
              <span class="user-name">管理员</span>
            </div>
            <template #overlay>
              <a-menu>
                <a-menu-item key="profile"><UserOutlined /> 个人设置</a-menu-item>
                <a-menu-divider />
                <a-menu-item key="logout"><LogoutOutlined /> 退出登录</a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
        </div>
      </a-layout-header>

      <a-layout-content class="fsd-content" :class="{ 'fullscreen-mode': route.meta.fullscreen }">
        <router-view v-slot="{ Component }">
          <transition name="page-fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </a-layout-content>
    </a-layout>
  </a-layout>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Empty } from 'ant-design-vue'
import {
  DashboardOutlined,
  FileTextOutlined,
  CarOutlined,
  ToolOutlined,
  AlertOutlined,
  HeatMapOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  BellOutlined,
  ReloadOutlined,
  UserOutlined,
  LogoutOutlined,
} from '@ant-design/icons-vue'
import { useExceptionStore } from '@/stores/exception'
import { DASHBOARD_POLL_INTERVAL } from '@/config'

const router = useRouter()
const route = useRoute()
const exceptionStore = useExceptionStore()

const collapsed = ref(false)
const simpleImage = Empty.PRESENTED_IMAGE_SIMPLE

const menuKeyMap: Record<string, string> = {
  '/dashboard': 'dashboard',
  '/orders': 'orders',
  '/tasks': 'tasks',
  '/vehicle-tracking': 'vehicle-tracking',
  '/vehicles': 'vehicles',
  '/exceptions': 'exceptions',
}

const selectedKeys = ref<string[]>(['dashboard'])

const breadcrumbItems = computed(() => {
  const meta = route.meta
  const crumbs = meta?.breadcrumb as string[] | undefined
  if (!crumbs) return []

  const pathMap: Record<string, string> = {
    '订单管理': '/orders',
    '调度任务': '/tasks',
    '车辆管理': '/vehicles',
    '车辆监控大屏': '/vehicle-tracking',
    '异常任务': '/exceptions',
  }

  return crumbs.map((label, i) => {
    const isLast = i === crumbs.length - 1
    let path = ''
    if (!isLast) {
      path = pathMap[label] || ''
    }
    if (isLast) {
      const orderId = route.params.orderId
      const taskId = route.params.taskId
      const vehicleId = route.params.vehicleId
      if (orderId) label = `${label}：${orderId}`
      if (taskId) label = `${label}：${taskId}`
      if (vehicleId) label = `${label}：${vehicleId}`
    }
    return { label, path }
  })
})

function handleMenuClick({ key }: { key: string }) {
  const pathMap: Record<string, string> = {
    dashboard: '/dashboard',
    orders: '/orders',
    tasks: '/tasks',
    vehicles: '/vehicles',
    'vehicle-tracking': '/vehicle-tracking',
    exceptions: '/exceptions',
  }
  if (pathMap[key]) {
    router.push(pathMap[key])
  }
}

watch(
  () => route.path,
  (path) => {
    const key = Object.keys(menuKeyMap).find((k) => path.startsWith(k))
    if (key) {
      selectedKeys.value = [menuKeyMap[key]]
    }
  },
  { immediate: true }
)

let pollTimer: ReturnType<typeof setInterval> | null = null

onMounted(() => {
  exceptionStore.fetchOpenCount()
  pollTimer = setInterval(() => {
    exceptionStore.fetchOpenCount()
  }, DASHBOARD_POLL_INTERVAL)
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
})
</script>

<style scoped lang="less">
.fsd-layout {
  height: 100vh;
  overflow: hidden;
}

.fsd-sider {
  position: fixed;
  left: 0;
  top: 0;
  bottom: 0;
  z-index: 10;
  overflow: auto;

  .sider-logo {
    height: 64px;
    display: flex;
    align-items: center;
    padding: 0 20px;
    gap: 12px;
    border-bottom: 1px solid var(--fsd-border);
    overflow: hidden;
  }

  .logo-icon svg {
    width: 32px;
    height: 32px;
    flex-shrink: 0;
  }

  .logo-text {
    display: flex;
    flex-direction: column;
    min-width: 0;
  }

  .logo-title {
    font-size: 16px;
    font-weight: 700;
    color: var(--fsd-text-primary);
    letter-spacing: -0.02em;
    line-height: 1.2;
  }

  .logo-sub {
    font-size: 11px;
    color: var(--fsd-text-tertiary);
    letter-spacing: 0.04em;
  }
}

.fsd-header {
  position: sticky;
  top: 0;
  z-index: 9;
  height: 64px;
  padding: 0 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;

  .header-left {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .trigger-btn {
    width: 40px;
    height: 40px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: var(--fsd-radius) !important;
    color: var(--fsd-text-secondary) !important;
    font-size: 18px;

    &:hover {
      background: var(--fsd-bg-hover) !important;
      color: var(--fsd-text-primary) !important;
    }
  }

  .header-breadcrumb {
    font-size: 14px;
  }

  .header-right {
    display: flex;
    align-items: center;
    gap: 4px;
  }

  .header-icon-btn {
    width: 40px;
    height: 40px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: var(--fsd-radius) !important;
    color: var(--fsd-text-secondary) !important;
    font-size: 18px;

    &:hover {
      background: var(--fsd-bg-hover) !important;
      color: var(--fsd-text-primary) !important;
    }
  }

  .user-info {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 4px 12px 4px 4px;
    border-radius: var(--fsd-radius);
    cursor: pointer;
    transition: background 0.2s;

    &:hover {
      background: var(--fsd-bg-hover);
    }
  }

  .user-name {
    font-size: 14px;
    color: var(--fsd-text-secondary);
  }
}

.fsd-content {
  margin-left: 208px;
  min-height: calc(100vh - 64px);
  padding: 24px;
  overflow: auto;
  background: var(--fsd-bg-deep);
  transition: margin-left 0.2s;

  &.fullscreen-mode {
    padding: 0;
    overflow: hidden;
    position: relative;
  }
}

:deep(.ant-layout-sider-collapsed) ~ .ant-layout .fsd-content {
  margin-left: 64px;
}

.notification-header {
  font-weight: 600;
  font-size: 14px;
  color: var(--fsd-text-primary);
  margin-bottom: 12px;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.page-fade-enter-active {
  transition: opacity 0.25s ease, transform 0.25s ease;
}

.page-fade-leave-active {
  transition: opacity 0.15s ease;
}

.page-fade-enter-from {
  opacity: 0;
  transform: translateY(8px);
}

.page-fade-leave-to {
  opacity: 0;
}
</style>
