<template>
  <a-layout class="fsd-layout">
    <!-- Desktop/Tablet: Fixed Sidebar -->
    <a-layout-sider
      v-if="!resp.isPhone.value"
      v-model:collapsed="sidebarCollapsed"
      :width="sidebarWidth"
      :collapsed-width="64"
      :trigger="null"
      collapsible
      class="fsd-sider"
      :class="sidebarCollapsed ? 'fsd-sider--collapsed' : 'fsd-sider--expanded'"
    >
      <SidebarContent :collapsed="sidebarCollapsed" />
    </a-layout-sider>

    <!-- Phone: Drawer Sidebar -->
    <a-drawer
      v-if="resp.isPhone.value"
      :open="mobileDrawerOpen"
      placement="left"
      :width="280"
      :closable="false"
      :body-style="{ padding: 0, background: '#121821' }"
      class="fsd-mobile-drawer"
      @close="mobileDrawerOpen = false"
    >
      <SidebarContent :collapsed="false" @navigate="mobileDrawerOpen = false" />
    </a-drawer>

    <a-layout class="fsd-main-area">
      <!-- Header -->
      <a-layout-header class="fsd-header" :class="{ 'fsd-header--mobile': resp.isPhone.value }">
        <div class="header-left">
          <!-- Phone: hamburger button -->
          <a-button
            v-if="resp.isPhone.value"
            type="text"
            class="trigger-btn"
            aria-label="打开导航菜单"
            @click="mobileDrawerOpen = true"
          >
            <MenuOutlined />
          </a-button>

          <!-- Desktop/Tablet: collapse toggle -->
          <a-button
            v-else
            type="text"
            class="trigger-btn"
            :aria-label="sidebarCollapsed ? '展开侧边栏' : '折叠侧边栏'"
            @click="toggleCollapsed"
          >
            <MenuFoldOutlined v-if="!sidebarCollapsed" />
            <MenuUnfoldOutlined v-else />
          </a-button>

          <!-- Breadcrumb (hidden on phone) -->
          <a-breadcrumb v-if="!resp.isXs.value" class="header-breadcrumb">
            <a-breadcrumb-item>
              <router-link to="/workbench">首页</router-link>
            </a-breadcrumb-item>
            <a-breadcrumb-item v-for="(item, index) in breadcrumbItems" :key="index">
              <router-link v-if="index < breadcrumbItems.length - 1 && item.path" :to="item.path">
                {{ item.label }}
              </router-link>
              <span v-else>{{ item.label }}</span>
            </a-breadcrumb-item>
          </a-breadcrumb>

          <!-- Phone: page title instead of breadcrumb -->
          <span v-else class="header-page-title">
            {{ pageTitle }}
          </span>
        </div>

        <div class="header-right">
          <!-- Park scope (hidden on phone) -->
          <a-select
            v-if="!resp.isPhone.value"
            v-model:value="parkScope.selectedParkId"
            :options="parkScope.parkOptions"
            placeholder="全部园区"
            allow-clear
            style="width: 168px; margin-right: 8px;"
            :loading="parkScope.loading"
            @change="onParkScopeChange"
          />

          <!-- Desktop: all icon buttons visible -->
          <template v-if="!resp.isMobile.value">
            <a-tooltip title="命令面板 (Ctrl+K)">
              <a-button type="text" class="header-icon-btn" aria-label="打开命令面板" @click="commandPalette.open()">
                <SearchOutlined />
              </a-button>
            </a-tooltip>
            <a-tooltip title="调度快捷指令">
              <a-button type="text" class="header-icon-btn" aria-label="调度助手" @click="assistantOpen = true">
                <RobotOutlined />
              </a-button>
            </a-tooltip>
            <a-tooltip title="刷新数据">
              <a-button type="text" class="header-icon-btn" aria-label="刷新数据" @click="refreshScopedData">
                <ReloadOutlined />
              </a-button>
            </a-tooltip>
            <a-tooltip :title="guardMode.enabled ? '关闭值守模式' : '开启值守模式'">
              <a-button
                type="text"
                class="header-icon-btn"
                :class="{ active: guardMode.enabled }"
                aria-label="切换值守模式"
                @click="guardMode.toggle()"
              >
                <EyeOutlined />
              </a-button>
            </a-tooltip>
          </template>

          <!-- Mobile: collapsed actions into dropdown -->
          <a-dropdown v-else trigger="click">
            <a-button type="text" class="header-icon-btn" aria-label="更多操作">
              <MoreOutlined />
            </a-button>
            <template #overlay>
              <a-menu @click="handleMobileAction">
                <a-menu-item key="park">
                  <EnvironmentOutlined />
                  <span v-if="parkScope.selectedParkId">{{ parkScope.selectedParkName || '已选园区' }}</span>
                  <span v-else>全部园区</span>
                </a-menu-item>
                <a-menu-divider />
                <a-menu-item key="command"><SearchOutlined /> 命令面板</a-menu-item>
                <a-menu-item key="assistant"><RobotOutlined /> 调度助手</a-menu-item>
                <a-menu-item key="refresh"><ReloadOutlined /> 刷新数据</a-menu-item>
                <a-menu-divider />
                <a-menu-item key="guard">
                  <EyeOutlined />
                  {{ guardMode.enabled ? '关闭值守模式' : '开启值守模式' }}
                </a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>

          <!-- Connection indicator -->
          <a-tooltip :title="realtimeStore.connected ? '实时连接正常' : '实时连接断开'">
            <span class="stream-indicator" :class="{ online: realtimeStore.connected }" />
          </a-tooltip>

          <ApiErrorBadge />

          <!-- Notifications -->
          <a-popover
            v-if="!resp.isPhone.value"
            trigger="click"
            placement="bottomRight"
            @openChange="handleNotificationOpen"
          >
            <template #content>
              <NotificationPanel
                :items="notificationItems"
                :loading="notificationLoading"
                :alert-history="alertStore.history"
                @view-all="router.push('/exceptions')"
                @settings="router.push('/system/alert-settings')"
                @click-item="goException"
              />
            </template>
            <a-button type="text" class="header-icon-btn notification-btn" aria-label="通知">
              <a-badge :count="notificationCount" :overflow-count="99" :show-zero="false" :offset="[-4, 4]">
                <BellOutlined />
              </a-badge>
            </a-button>
          </a-popover>

          <!-- Phone: notification opens as drawer -->
          <a-button
            v-else
            type="text"
            class="header-icon-btn notification-btn"
            aria-label="通知"
            @click="mobileNotifyOpen = true"
          >
            <a-badge :count="notificationCount" :overflow-count="99" :show-zero="false" :offset="[-4, 4]">
              <BellOutlined />
            </a-badge>
          </a-button>

          <!-- User menu -->
          <a-dropdown>
            <div class="user-info">
              <UserAvatar
                :name="authStore.displayName"
                :username="authStore.user?.username"
                :role="authStore.user?.role"
                :size="32"
              />
              <span v-if="!resp.isPhone.value" class="user-name">{{ authStore.displayName }}</span>
            </div>
            <template #overlay>
              <a-menu @click="handleUserMenu">
                <a-menu-item key="profile"><UserOutlined /> 个人设置</a-menu-item>
                <a-menu-divider />
                <a-menu-item key="logout"><LogoutOutlined /> 退出登录</a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
        </div>
      </a-layout-header>

      <!-- Main content -->
      <a-layout-content
        class="fsd-content"
        :class="{
          'fullscreen-mode': route.meta.fullscreen,
          'fsd-content--mobile': resp.isMobile.value,
          'fsd-content--phone': resp.isPhone.value,
        }"
      >
        <router-view v-slot="{ Component }">
          <transition name="page-fade" mode="out-in">
            <component :is="Component" :key="route.fullPath" />
          </transition>
        </router-view>
      </a-layout-content>
    </a-layout>

    <!-- Back to top -->
    <BackToTop />

    <!-- Phone: Bottom Navigation Bar -->
    <nav v-if="resp.isPhone.value" class="fsd-bottom-nav" aria-label="底部导航">
      <button
        v-for="item in bottomNavItems"
        :key="item.key"
        type="button"
        class="bottom-nav-item"
        :class="{ 'bottom-nav-item--active': item.active }"
        @click="handleBottomNav(item)"
      >
        <component :is="item.icon" class="bottom-nav-icon" />
        <span class="bottom-nav-label">{{ item.label }}</span>
        <span v-if="item.badge" class="bottom-nav-badge">{{ item.badge > 99 ? '99+' : item.badge }}</span>
      </button>
    </nav>

    <!-- Modals & Overlays -->
    <CommandPalette
      :visible="commandPalette.visible.value"
      :keyword="commandPalette.keyword.value"
      :loading="commandPalette.loading.value"
      :active-index="commandPalette.activeIndex.value"
      :items="paletteItems"
      @close="commandPalette.close()"
      @update:keyword="commandPalette.keyword.value = $event"
      @update:active-index="commandPalette.activeIndex.value = $event"
      @search="commandPalette.runSearch()"
      @run="onPaletteRun"
    />
    <DispatchAssistantDrawer v-model:open="assistantOpen" />

    <!-- Mobile notification drawer -->
    <a-drawer
      v-if="resp.isPhone.value"
      :open="mobileNotifyOpen"
      placement="bottom"
      :height="'70vh'"
      :closable="true"
      title="通知中心"
      class="fsd-mobile-notify-drawer"
      @close="mobileNotifyOpen = false"
      @afterOpenChange="(open: boolean) => { if (open) handleNotificationOpen(true) }"
    >
      <NotificationPanel
        :items="notificationItems"
        :loading="notificationLoading"
        :alert-history="alertStore.history"
        @view-all="mobileNotifyOpen = false; router.push('/exceptions')"
        @settings="mobileNotifyOpen = false; router.push('/system/alert-settings')"
        @click-item="(item: ExceptionAdminListItem) => { mobileNotifyOpen = false; goException(item) }"
      />
    </a-drawer>

    <!-- Mobile park selector drawer -->
    <a-drawer
      v-if="resp.isPhone.value && mobileParkOpen"
      :open="mobileParkOpen"
      placement="bottom"
      :height="'auto'"
      :closable="true"
      title="选择园区"
      class="fsd-mobile-park-drawer"
      @close="mobileParkOpen = false"
    >
      <div class="mobile-park-list">
        <button
          type="button"
          class="mobile-park-item"
          :class="{ active: !parkScope.selectedParkId }"
          @click="parkScope.setParkId(undefined as any); mobileParkOpen = false; onParkScopeChange()"
        >
          全部园区
        </button>
        <button
          v-for="park in parkScope.parkOptions"
          :key="park.value"
          type="button"
          class="mobile-park-item"
          :class="{ active: parkScope.selectedParkId === park.value }"
          @click="parkScope.setParkId(park.value); mobileParkOpen = false; onParkScopeChange()"
        >
          {{ park.label }}
        </button>
      </div>
    </a-drawer>
  </a-layout>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import 'dayjs/locale/zh-cn'
import type { ExceptionAdminListItem } from '@/types/exception'

dayjs.extend(relativeTime)
dayjs.locale('zh-cn')
import {
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  MenuOutlined,
  BellOutlined,
  ReloadOutlined,
  UserOutlined,
  LogoutOutlined,
  SearchOutlined,
  RobotOutlined,
  EyeOutlined,
  MoreOutlined,
  EnvironmentOutlined,
  DashboardOutlined,
  FileTextOutlined,
  CarOutlined,
  AlertOutlined,
  ToolOutlined,
  BarChartOutlined,
  SettingOutlined,
} from '@ant-design/icons-vue'
import UserAvatar from '@/components/brand/UserAvatar.vue'
import CommandPalette from '@/components/command/CommandPalette.vue'
import DispatchAssistantDrawer from '@/components/assistant/DispatchAssistantDrawer.vue'
import ApiErrorBadge from '@/components/error/ApiErrorBadge.vue'
import NotificationPanel from '@/components/layout/NotificationPanel.vue'
import SidebarContent from '@/components/layout/SidebarContent.vue'
import BackToTop from '@/components/common/BackToTop.vue'
import { useCommandPalette, type CommandPaletteItem } from '@/composables/useCommandPalette'
import { useGuardMode } from '@/composables/useGuardMode'
import { useResponsive } from '@/composables/useResponsive'
import { useWorkbenchStore } from '@/stores/workbench'
import { useParkScopeStore } from '@/stores/parkScope'
import { useDashboardStore } from '@/stores/dashboard'
import { useAuthStore } from '@/stores/auth'
import { useRealtimeStore } from '@/stores/realtime'
import { useAlertStore } from '@/stores/alert'
import { ADMIN_AUTH_ENABLED } from '@/config'
import {
  BREADCRUMB_PATH_MAP,
  NAV_PATH_MAP,
  resolveMenuKeyFromPath,
} from '@/config/navigation'

const router = useRouter()
const route = useRoute()
const workbenchStore = useWorkbenchStore()
const dashboardStore = useDashboardStore()
const parkScope = useParkScopeStore()
const authStore = useAuthStore()
const realtimeStore = useRealtimeStore()
const alertStore = useAlertStore()
const commandPalette = useCommandPalette()
const guardMode = useGuardMode()
const resp = useResponsive()

const assistantOpen = ref(false)
const paletteItems = computed(() => commandPalette.buildItems())

const collapsed = ref(false)
const mobileDrawerOpen = ref(false)
const mobileNotifyOpen = ref(false)
const mobileParkOpen = ref(false)
const notificationLoading = ref(false)
const notificationItems = ref<ExceptionAdminListItem[]>([])

// ── Responsive sidebar state ─────────────────────────────
const sidebarCollapsed = computed({
  get: () => {
    if (resp.autoCollapseSidebar.value) return true
    return collapsed.value
  },
  set: (v) => { collapsed.value = v },
})

const sidebarWidth = computed(() =>
  resp.isTablet.value ? 64 : 208
)

// ── Page title for mobile header ─────────────────────────
const pageTitle = computed(() => {
  const crumbs = breadcrumbItems.value
  if (crumbs.length > 0) return crumbs[crumbs.length - 1].label
  const meta = route.meta as Record<string, unknown> | undefined
  return (meta?.title as string) || 'DispatchFlow'
})

// ── Bottom nav items ─────────────────────────────────────
const bottomNavItems = computed(() => {
  const path = route.path
  return [
    {
      key: 'workbench',
      label: '工作台',
      icon: DashboardOutlined,
      path: '/workbench',
      active: path.startsWith('/workbench'),
      badge: workbenchStore.openExceptionCount || undefined,
    },
    {
      key: 'orders',
      label: '订单',
      icon: FileTextOutlined,
      path: '/orders',
      active: path.startsWith('/orders'),
    },
    {
      key: 'vehicles',
      label: '车辆',
      icon: CarOutlined,
      path: '/vehicles',
      active: path.startsWith('/vehicles'),
    },
    {
      key: 'analytics',
      label: '分析',
      icon: BarChartOutlined,
      path: '/analytics',
      active: path.startsWith('/analytics'),
    },
    {
      key: 'more',
      label: '更多',
      icon: MoreOutlined,
      path: '',
      active: false,
      badge: undefined,
    },
  ]
})

const notificationCount = computed(() => workbenchStore.openExceptionCount)

const breadcrumbItems = computed(() => {
  const meta = route.meta
  const crumbs = meta?.breadcrumb as string[] | undefined
  if (!crumbs) return []

  const pathMap = BREADCRUMB_PATH_MAP

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

async function handleNotificationOpen(open: boolean) {
  if (!open) return
  notificationLoading.value = true
  try {
    await workbenchStore.fetchQueue()
    notificationItems.value = workbenchStore.openExceptions.slice(0, 8)
    alertStore.markAllRead()
  } catch {
    notificationItems.value = []
  } finally {
    notificationLoading.value = false
  }
}

function goException(item: ExceptionAdminListItem) {
  if (item.taskId) {
    router.push(`/tasks/${item.taskId}`)
    return
  }
  router.push({ path: '/exceptions', query: { status: 'OPEN', exceptionId: String(item.id) } })
}

function toggleCollapsed() {
  collapsed.value = !collapsed.value
}

function handleMobileAction({ key }: { key: string }) {
  switch (key) {
    case 'park':
      mobileParkOpen.value = true
      break
    case 'command':
      commandPalette.open()
      break
    case 'assistant':
      assistantOpen.value = true
      break
    case 'refresh':
      refreshScopedData()
      break
    case 'guard':
      guardMode.toggle()
      break
  }
}

function handleBottomNav(item: typeof bottomNavItems.value[number]) {
  if (item.key === 'more') {
    mobileDrawerOpen.value = true
    return
  }
  if (item.path) {
    router.push(item.path)
  }
}

async function onPaletteRun(item: CommandPaletteItem) {
  await item.action()
  commandPalette.close()
}

async function refreshScopedData() {
  await workbenchStore.fetchQueue()
  await dashboardStore.fetchSummary()
}

async function handleUserMenu({ key }: { key: string }) {
  if (key === 'profile') {
    router.push('/profile')
    return
  }
  if (key === 'logout') {
    await authStore.logout()
    if (ADMIN_AUTH_ENABLED) {
      router.replace('/login')
    }
  }
}

async function refreshBadgeCounts() {
  await workbenchStore.fetchQueue()
}

async function onParkScopeChange() {
  parkScope.setParkId(parkScope.selectedParkId)
  await Promise.all([
    workbenchStore.fetchQueue({ silent: true }),
    dashboardStore.fetchSummary({ silent: true }),
  ])
}

watch(
  () => route.path,
  () => {
    // Close mobile drawer on navigation
    mobileDrawerOpen.value = false
  }
)

// Auto-init responsive detection
onMounted(() => {
  parkScope.loadParks()
  refreshBadgeCounts()
  realtimeStore.start()
})

onUnmounted(() => {
  realtimeStore.stop()
})
</script>

<style scoped lang="less">
/* ── Layout ─────────────────────────────────────────────── */
.fsd-layout {
  height: 100vh;
  height: 100dvh;
  overflow: hidden;
}

.fsd-main-area {
  transition: margin-left 0.2s var(--fsd-ease);
}

/* ── Sidebar ────────────────────────────────────────────── */
.fsd-sider {
  position: fixed;
  left: 0;
  top: 0;
  bottom: 0;
  z-index: var(--fsd-z-sticky);
  overflow: hidden;

  :deep(.ant-layout-sider-children) {
    display: flex;
    flex-direction: column;
    height: 100%;
    overflow: hidden;
  }
}

/* Allow popup to overflow when sidebar is collapsed */
.fsd-sider--collapsed {
  overflow: visible;

  :deep(.ant-layout-sider-children) {
    overflow: visible;
  }
}

/* ── Mobile Drawer ──────────────────────────────────────── */
:global(.fsd-mobile-drawer) {
  .ant-drawer-body {
    padding: 0 !important;
    background: #121821 !important;
  }

  /* SidebarContent now renders custom nav, not ant-menu */
  .sidebar-logo {
    height: 56px;
    padding: 0 20px;
    display: flex;
    align-items: center;
    gap: 12px;
    border-bottom: 1px solid var(--fsd-border);
  }
}

/* ── Header ─────────────────────────────────────────────── */
.fsd-header {
  position: sticky;
  top: 0;
  z-index: 9;
  margin-left: 208px;
  height: 64px;
  padding: 0 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  transition: margin-left 0.2s var(--fsd-ease);

  .header-left {
    display: flex;
    align-items: center;
    gap: 12px;
    flex: 1;
    min-width: 0;
    overflow: hidden;
  }

  .trigger-btn {
    width: 44px;
    height: 44px;
    flex-shrink: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: var(--fsd-radius) !important;
    color: var(--fsd-text-secondary) !important;
    font-size: 20px;

    &:hover {
      background: var(--fsd-bg-hover) !important;
      color: var(--fsd-text-primary) !important;
    }
  }

  .header-breadcrumb {
    flex: 1;
    min-width: 0;
    font-size: 14px;

    :deep(ol) {
      flex-wrap: nowrap;
    }

    :deep(li:last-child) {
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      max-width: 100%;
    }
  }

  .header-page-title {
    font-size: 16px;
    font-weight: 600;
    color: var(--fsd-text-primary);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .header-right {
    display: flex;
    align-items: center;
    gap: 4px;
    flex-shrink: 0;
  }

  .header-icon-btn {
    width: 44px;
    height: 44px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: var(--fsd-radius) !important;
    color: var(--fsd-text-secondary) !important;
    font-size: 20px;

    &:hover {
      background: var(--fsd-bg-hover) !important;
      color: var(--fsd-text-primary) !important;
    }

    &.active {
      color: var(--fsd-accent) !important;
      background: var(--fsd-accent-glow) !important;
    }
  }

  .user-info {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 4px 8px 4px 4px;
    border-radius: 999px;
    cursor: pointer;
    transition: background 0.2s;

    &:hover {
      background: var(--fsd-bg-hover);
    }
  }

  .user-name {
    font-size: 13px;
    font-weight: 500;
    color: var(--fsd-text-primary);
    max-width: 120px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  /* Mobile header adjustments */
  &.fsd-header--mobile {
    margin-left: 0;
    padding: 0 12px;
    height: 56px;
  }
}

/* ── Content area ───────────────────────────────────────── */
.fsd-content {
  margin-left: 208px;
  min-height: calc(100vh - 64px);
  min-height: calc(100dvh - 64px);
  padding: var(--fsd-space-6);
  overflow: auto;
  background: var(--fsd-bg-deep);
  transition: margin-left 0.2s var(--fsd-ease);

  &.fullscreen-mode {
    padding: 0;
    overflow: hidden;
    position: relative;
  }

  &.fsd-content--mobile {
    margin-left: 0;
  }

  &.fsd-content--phone {
    padding: var(--fsd-space-4);
    min-height: calc(100dvh - 56px - 64px);
    padding-bottom: calc(var(--fsd-space-4) + 64px + env(safe-area-inset-bottom, 0px));
  }
}

/* Sidebar collapsed state */
:deep(.ant-layout-sider-collapsed) ~ .fsd-main-area .fsd-header,
:deep(.ant-layout-sider-collapsed) ~ .fsd-main-area .fsd-content {
  margin-left: 64px;
}

/* ── Bottom Navigation ──────────────────────────────────── */
.fsd-bottom-nav {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  z-index: var(--fsd-z-sticky);
  display: flex;
  justify-content: space-around;
  align-items: center;
  height: 64px;
  padding-bottom: env(safe-area-inset-bottom, 0px);
  background: var(--fsd-bg-base);
  border-top: 1px solid var(--fsd-border);
  backdrop-filter: blur(16px) saturate(140%);
}

.bottom-nav-item {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  flex: 1;
  height: 100%;
  padding: 8px 4px;
  border: none;
  background: transparent;
  color: var(--fsd-text-tertiary);
  cursor: pointer;
  transition: color 0.2s var(--fsd-ease);
  -webkit-tap-highlight-color: transparent;

  &:active {
    transform: scale(0.95);
  }

  &--active {
    color: var(--fsd-accent);

    .bottom-nav-icon {
      color: var(--fsd-accent);
    }
  }
}

.bottom-nav-icon {
  font-size: 22px;
  transition: color 0.2s var(--fsd-ease);
}

.bottom-nav-label {
  font-size: 11px;
  font-weight: 500;
  line-height: 1;
}

.bottom-nav-badge {
  position: absolute;
  top: 6px;
  right: calc(50% - 20px);
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background: var(--fsd-error);
  color: #fff;
  font-size: 10px;
  font-weight: 700;
  line-height: 18px;
  text-align: center;
}

/* ── Stream indicator ───────────────────────────────────── */
.stream-indicator {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--fsd-text-tertiary);
  display: inline-block;
  margin-right: 8px;

  &.online {
    background: #00e676;
    box-shadow: 0 0 8px rgba(0, 230, 118, 0.6);
  }
}

/* ── Mobile park list ───────────────────────────────────── */
.mobile-park-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 8px 0;
  max-height: 50vh;
  overflow-y: auto;
}

.mobile-park-item {
  width: 100%;
  padding: 14px 16px;
  border: none;
  border-radius: var(--fsd-radius);
  background: transparent;
  color: var(--fsd-text-primary);
  font-size: 15px;
  text-align: left;
  cursor: pointer;
  transition: background 0.15s var(--fsd-ease);

  &:hover {
    background: var(--fsd-bg-hover);
  }

  &.active {
    background: var(--fsd-accent-glow);
    color: var(--fsd-accent);
    font-weight: 600;
  }
}

/* ── Transitions ────────────────────────────────────────── */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.page-fade-enter-active {
  transition: opacity 0.25s var(--fsd-ease), transform 0.25s var(--fsd-ease);
}

.page-fade-leave-active {
  transition: opacity 0.15s var(--fsd-ease);
}

.page-fade-enter-from {
  opacity: 0;
  transform: translateY(8px);
}

.page-fade-leave-to {
  opacity: 0;
}

/* ── Responsive: Tablet ─────────────────────────────────── */
@media (max-width: 991px) {
  /* SidebarContent handles its own responsive sizing */
}

/* ── Responsive: Phone ──────────────────────────────────── */
@media (max-width: 575px) {
  .fsd-header {
    .header-icon-btn {
      width: 40px;
      height: 40px;
      font-size: 18px;
    }
  }
}
</style>
