<template>
  <a-layout class="fsd-layout">
    <a-layout-sider
      v-model:collapsed="collapsed"
      :width="208"
      :collapsed-width="64"
      :trigger="null"
      collapsible
      class="fsd-sider"
      :class="collapsed ? 'fsd-sider--collapsed' : 'fsd-sider--expanded'"
    >
      <div class="sider-logo">
        <div class="logo-icon">
          <DispatchFlowLogo :size="32" />
        </div>
        <transition name="fade">
          <div v-if="!collapsed" class="logo-text">
            <span class="logo-title">DispatchFlow</span>
            <span class="logo-sub">无人车调度平台</span>
          </div>
        </transition>
      </div>

      <a-menu
        v-if="visibleNavItems.length > 0"
        v-model:selectedKeys="selectedKeys"
        v-model:openKeys="openKeys"
        theme="dark"
        mode="inline"
        @click="handleMenuClick"
      >
        <NavMenuItems
          :items="visibleNavItems"
          :workbench-badge-count="workbenchBadgeCount"
          :exception-badge-count="workbenchStore.openExceptionCount"
        />
      </a-menu>
    </a-layout-sider>

    <a-layout>
      <a-layout-header class="fsd-header">
        <div class="header-left">
          <a-button
            type="text"
            class="trigger-btn"
            @click="toggleCollapsed"
          >
            <MenuFoldOutlined v-if="!collapsed" />
            <MenuUnfoldOutlined v-else />
          </a-button>
          <a-breadcrumb class="header-breadcrumb">
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
        </div>
        <div class="header-right">
          <a-select
            v-model:value="parkScope.selectedParkId"
            :options="parkScope.parkOptions"
            placeholder="全部园区"
            allow-clear
            style="width: 168px; margin-right: 8px;"
            :loading="parkScope.loading"
            @change="onParkScopeChange"
          />
          <a-tooltip title="命令面板 (Ctrl+K)">
            <a-button type="text" class="header-icon-btn" @click="commandPalette.open()">
              <SearchOutlined />
            </a-button>
          </a-tooltip>
          <a-tooltip title="调度快捷指令">
            <a-button type="text" class="header-icon-btn" @click="assistantOpen = true">
              <RobotOutlined />
            </a-button>
          </a-tooltip>
          <a-tooltip title="刷新数据">
            <a-button type="text" class="header-icon-btn" @click="refreshScopedData">
              <ReloadOutlined />
            </a-button>
          </a-tooltip>
          <a-tooltip :title="guardMode.enabled ? '关闭值守模式' : '开启值守模式（降低视觉对比）'">
            <a-button
              type="text"
              class="header-icon-btn"
              :class="{ active: guardMode.enabled }"
              @click="guardMode.toggle()"
            >
              <EyeOutlined />
            </a-button>
          </a-tooltip>
          <a-tooltip :title="realtimeStore.connected ? '实时连接正常' : '实时连接断开'">
            <span class="stream-indicator" :class="{ online: realtimeStore.connected }" />
          </a-tooltip>
          <ApiErrorBadge />
          <a-popover trigger="click" placement="bottomRight" @openChange="handleNotificationOpen">
            <template #content>
              <div class="notification-panel">
                <div class="notification-header">
                  <span>待处理异常</span>
                  <a class="notification-link" @click="router.push('/system/alert-settings')">告警设置</a>
                  <a
                    v-if="notificationCount > 0"
                    class="notification-link"
                    @click="router.push('/exceptions')"
                  >
                    查看全部
                  </a>
                </div>
                <div v-if="alertStore.history.length > 0" class="alert-history-block">
                  <div class="alert-history-title">最近告警</div>
                  <div
                    v-for="item in alertStore.history.slice(0, 5)"
                    :key="item.id"
                    class="alert-history-item"
                    :class="{ unread: !item.read }"
                  >
                    <span class="alert-history-sev">{{ item.severity }}</span>
                    <span>{{ item.message }}</span>
                  </div>
                </div>
                <a-spin :spinning="notificationLoading">
                  <div v-if="notificationItems.length > 0" class="notification-list">
                    <button
                      v-for="item in notificationItems"
                      :key="item.id"
                      type="button"
                      class="notification-item"
                      @click="goException(item)"
                    >
                      <span class="notification-type">{{ item.exceptionType }}</span>
                      <span class="notification-msg">{{ item.exceptionMsg || '调度异常' }}</span>
                      <span class="notification-time">{{ formatNotificationTime(item.occurTime) }}</span>
                    </button>
                  </div>
                  <a-empty v-else :image="simpleImage" description="暂无待处理通知" />
                </a-spin>
              </div>
            </template>
            <a-button type="text" class="header-icon-btn notification-btn">
              <a-badge
                :count="notificationCount"
                :overflow-count="99"
                :show-zero="false"
                :offset="[-4, 4]"
              >
                <BellOutlined />
              </a-badge>
            </a-button>
          </a-popover>
          <a-dropdown>
            <div class="user-info">
              <UserAvatar
                :name="authStore.displayName"
                :username="authStore.user?.username"
                :role="authStore.user?.role"
                :size="32"
              />
              <span class="user-name">{{ authStore.displayName }}</span>
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

      <a-layout-content class="fsd-content" :class="{ 'fullscreen-mode': route.meta.fullscreen }">
        <router-view v-slot="{ Component }">
          <transition name="page-fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </a-layout-content>
    </a-layout>

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
  </a-layout>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Empty } from 'ant-design-vue'
import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import 'dayjs/locale/zh-cn'
import type { ExceptionAdminListItem } from '@/types/exception'

dayjs.extend(relativeTime)
dayjs.locale('zh-cn')
import {
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  BellOutlined,
  ReloadOutlined,
  UserOutlined,
  LogoutOutlined,
  SearchOutlined,
  RobotOutlined,
  EyeOutlined,
} from '@ant-design/icons-vue'
import NavMenuItems from '@/components/layout/NavMenuItems.vue'
import DispatchFlowLogo from '@/components/brand/DispatchFlowLogo.vue'
import UserAvatar from '@/components/brand/UserAvatar.vue'
import CommandPalette from '@/components/command/CommandPalette.vue'
import DispatchAssistantDrawer from '@/components/assistant/DispatchAssistantDrawer.vue'
import ApiErrorBadge from '@/components/error/ApiErrorBadge.vue'
import { useCommandPalette, type CommandPaletteItem } from '@/composables/useCommandPalette'
import { useGuardMode } from '@/composables/useGuardMode'
import { useWorkbenchStore } from '@/stores/workbench'
import { useParkScopeStore } from '@/stores/parkScope'
import { useDashboardStore } from '@/stores/dashboard'
import { useAuthStore } from '@/stores/auth'
import { useRealtimeStore } from '@/stores/realtime'
import { useAlertStore } from '@/stores/alert'
import { ADMIN_AUTH_ENABLED } from '@/config'
import {
  BREADCRUMB_PATH_MAP,
  NAV_PARENT_KEY_MAP,
  NAV_PATH_MAP,
  NAVIGATION_TREE,
  filterNavByRole,
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
const assistantOpen = ref(false)
const paletteItems = computed(() => commandPalette.buildItems())

const collapsed = ref(false)
const simpleImage = Empty.PRESENTED_IMAGE_SIMPLE
const notificationLoading = ref(false)
const notificationItems = ref<ExceptionAdminListItem[]>([])

const notificationCount = computed(() => workbenchStore.openExceptionCount)

const workbenchBadgeCount = computed(
  () => workbenchStore.pendingCount + workbenchStore.manualPendingCount + workbenchStore.openExceptionCount
)

const visibleNavItems = computed(() => filterNavByRole(NAVIGATION_TREE, authStore.user?.role))

const selectedKeys = ref<string[]>(['workbench'])
const openKeys = ref<string[]>([])

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

function formatNotificationTime(value: string) {
  return dayjs(value).fromNow()
}

function goException(item: ExceptionAdminListItem) {
  if (item.taskId) {
    router.push(`/tasks/${item.taskId}`)
    return
  }
  router.push({ path: '/exceptions', query: { status: 'OPEN', exceptionId: String(item.id) } })
}

function handleMenuClick({ key }: { key: string }) {
  const path = NAV_PATH_MAP[key]
  if (path) {
    router.push(path)
  }
}

function syncOpenKeysByPath(path = route.path) {
  const menuKey = resolveMenuKeyFromPath(path)
  if (!menuKey) return
  const parentKey = NAV_PARENT_KEY_MAP[menuKey]
  openKeys.value = !collapsed.value && parentKey ? [parentKey] : []
}

function toggleCollapsed() {
  collapsed.value = !collapsed.value
  if (collapsed.value) {
    openKeys.value = []
    return
  }
  syncOpenKeysByPath()
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
  (path) => {
    const menuKey = resolveMenuKeyFromPath(path)
    if (menuKey) {
      selectedKeys.value = [menuKey]
      syncOpenKeysByPath(path)
    }
  },
  { immediate: true }
)

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

  :deep(.ant-menu-root) {
    border-inline-end: 0;
    background: #0b1118 !important;
  }

  :deep(.ant-menu-sub) {
    background: #0b1118 !important;
  }

  :deep(.ant-menu-root > .ant-menu-item),
  :deep(.ant-menu-root > .ant-menu-submenu > .ant-menu-submenu-title) {
    display: flex !important;
    align-items: center;
    height: 42px;
    margin-block: 4px;
    line-height: 42px;
  }

  &.fsd-sider--expanded {
    :deep(.ant-menu-root > .ant-menu-item),
    :deep(.ant-menu-root > .ant-menu-submenu > .ant-menu-submenu-title) {
      padding-inline: 36px 16px !important;
    }

    :deep(.ant-menu-root > .ant-menu-item .ant-menu-title-content),
    :deep(.ant-menu-root > .ant-menu-submenu > .ant-menu-submenu-title .ant-menu-title-content) {
      display: flex;
      align-items: center;
      flex: 1 1 auto;
      min-width: 0;
      margin-inline-start: 0 !important;
      line-height: 1;
    }

    :deep(.ant-menu-sub .ant-menu-item) {
      display: flex !important;
      align-items: center;
      height: 38px;
      margin-block: 2px;
      padding-inline: 76px 16px !important;
      line-height: 38px;
    }

    :deep(.ant-menu-sub .ant-menu-title-content) {
      margin-inline-start: 0 !important;
    }
  }

  &.fsd-sider--collapsed {
    :deep(.ant-menu-root > .ant-menu-item),
    :deep(.ant-menu-root > .ant-menu-submenu > .ant-menu-submenu-title) {
      display: grid !important;
      width: 56px;
      height: 42px;
      margin-inline: 4px;
      padding-inline: 0 !important;
      place-items: center;
      overflow: hidden;
    }

    :deep(.ant-menu-root > .ant-menu-item:hover),
    :deep(.ant-menu-root > .ant-menu-submenu > .ant-menu-submenu-title:hover),
    :deep(.ant-menu-root > .ant-menu-item-selected),
    :deep(.ant-menu-root > .ant-menu-submenu-selected > .ant-menu-submenu-title) {
      background: var(--fsd-bg-active) !important;
      color: var(--fsd-accent) !important;
      opacity: 1 !important;
    }

    :deep(.ant-menu-title-content) {
      display: grid !important;
      width: 20px;
      height: 20px;
      margin: 0 !important;
      padding: 0 !important;
      place-items: center;
      opacity: 1 !important;
      overflow: hidden;
    }

    :deep(.nav-menu-entry) {
      display: grid;
      grid-template-columns: 20px;
      width: 20px;
      height: 20px;
      place-items: center;
    }

    :deep(.nav-menu-entry__icon),
    :deep(.nav-menu-icon),
    :deep(.nav-menu-icon .anticon),
    :deep(.nav-menu-icon svg),
    :deep(.nav-menu-badge-icon),
    :deep(.nav-menu-badge-icon__slot) {
      display: grid !important;
      width: 20px;
      height: 20px;
      place-items: center;
      opacity: 1 !important;
    }

    :deep(.nav-menu-entry__label),
    :deep(.ant-menu-sub),
    :deep(.ant-menu-submenu-arrow) {
      display: none !important;
    }
  }

  :deep(.ant-menu-item:hover),
  :deep(.ant-menu-submenu-title:hover),
  :deep(.ant-menu-item-selected),
  :deep(.ant-menu-submenu-selected > .ant-menu-submenu-title) {
    color: var(--fsd-text-primary) !important;
    opacity: 1 !important;
  }

  :deep(.ant-menu-item:hover .nav-menu-entry),
  :deep(.ant-menu-submenu-title:hover .nav-menu-entry),
  :deep(.ant-menu-item-selected .nav-menu-entry),
  :deep(.ant-menu-submenu-selected > .ant-menu-submenu-title .nav-menu-entry) {
    color: inherit;
    opacity: 1 !important;
  }
}

:global(.nav-menu-popup) {
  background: #0b1118 !important;

  .ant-menu {
    min-width: 160px;
    padding: 6px;
    border: 1px solid #233042;
    border-radius: 12px;
    background: #0b1118 !important;
    box-shadow: 0 18px 40px rgba(0, 0, 0, 0.44);
  }

  .ant-menu-item {
    display: flex !important;
    height: 36px;
    margin-block: 2px;
    padding-inline: 12px !important;
    align-items: center;
    border-radius: 8px;
    color: var(--fsd-text-secondary) !important;
    line-height: 36px;
  }

  .ant-menu-item:hover,
  .ant-menu-item-selected {
    background: var(--fsd-bg-active) !important;
    color: var(--fsd-text-primary) !important;
  }

  .ant-menu-title-content {
    margin-inline-start: 0 !important;
  }

  .nav-menu-entry {
    display: grid;
    grid-template-columns: 20px minmax(0, 1fr);
    column-gap: 12px;
    width: 100%;
    align-items: center;
  }

  .nav-menu-entry__icon {
    display: grid;
    width: 20px;
    height: 20px;
    place-items: center;
  }

  .nav-menu-entry__label {
    display: block;
  }
}

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
  transition: margin-left 0.2s;

  .header-left {
    display: flex;
    align-items: center;
    gap: 12px;
    flex: 1;
    min-width: 0;
    overflow: hidden;
  }

  .trigger-btn {
    width: 40px;
    height: 40px;
    flex-shrink: 0;
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

  .header-right {
    display: flex;
    align-items: center;
    gap: 4px;
    flex-shrink: 0;
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

:deep(.ant-layout-sider-collapsed) ~ .ant-layout .fsd-header,
:deep(.ant-layout-sider-collapsed) ~ .ant-layout .fsd-content {
  margin-left: 64px;
}

.notification-panel {
  width: 300px;
}

.notification-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
  font-size: 14px;
  color: var(--fsd-text-primary);
  margin-bottom: 12px;
}

.notification-link {
  font-size: 12px;
  font-weight: 500;
  color: var(--fsd-accent);
  text-decoration: none;

  &:hover {
    color: #7ee8ff;
  }
}

.notification-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 320px;
  overflow: auto;
}

.notification-item {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--fsd-border);
  border-radius: 8px;
  background: rgba(22, 27, 34, 0.5);
  text-align: left;
  cursor: pointer;
  transition: border-color 0.2s ease, background 0.2s ease;

  &:hover {
    border-color: rgba(0, 180, 216, 0.35);
    background: rgba(0, 180, 216, 0.08);
  }
}

.notification-type {
  display: block;
  font-size: 11px;
  font-weight: 600;
  color: var(--fsd-warning);
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.notification-msg {
  display: block;
  margin-top: 4px;
  font-size: 13px;
  color: var(--fsd-text-primary);
  line-height: 1.4;
}

.notification-time {
  display: block;
  margin-top: 6px;
  font-size: 11px;
  color: var(--fsd-text-tertiary);
}

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

.alert-history-block {
  margin-bottom: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--fsd-border);
}

.alert-history-title {
  font-size: 12px;
  color: var(--fsd-text-tertiary);
  margin-bottom: 8px;
}

.alert-history-item {
  font-size: 12px;
  color: var(--fsd-text-secondary);
  padding: 6px 0;
  border-bottom: 1px dashed rgba(255, 255, 255, 0.06);

  &.unread {
    color: var(--fsd-text-primary);
  }
}

.alert-history-sev {
  display: inline-block;
  margin-right: 6px;
  font-size: 10px;
  font-weight: 700;
  color: var(--fsd-warning);
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
