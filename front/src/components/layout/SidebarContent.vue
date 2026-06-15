<template>
  <div class="sidebar" :class="{ 'sidebar--collapsed': collapsed }">
    <!-- ════ Logo ════ -->
    <div class="sidebar-logo" :class="{ 'sidebar-logo--collapsed': collapsed }">
      <div class="sidebar-logo__icon">
        <DispatchFlowLogo :size="34" />
      </div>
      <Transition name="sidebar-fade">
        <div v-if="!collapsed" class="sidebar-logo__text">
          <span class="sidebar-logo__title">DispatchFlow</span>
          <span class="sidebar-logo__sub">无人车调度平台</span>
        </div>
      </Transition>
    </div>

    <!-- ════ Navigation ════ -->
    <nav class="sidebar-nav" v-if="visibleNavItems.length > 0">
      <template v-for="item in visibleNavItems" :key="item.key">
        <!-- ── Leaf Item (no children: 调度工作台 / 数字孪生) ── -->
        <button
          v-if="!hasChildren(item)"
          class="nav-item nav-item--leaf"
          :class="{ 'nav-item--active': selectedKeys[0] === item.key }"
          @click="handleNavClick(item.key, item.path)"
        >
          <span class="nav-item__icon">
            <NavMenuBadgeIcon
              v-if="item.badge"
              :badge="item.badge"
              :workbench-badge-count="workbenchBadgeCount"
              :exception-badge-count="exceptionBadgeCount"
            >
              <NavMenuIcon :icon="item.icon" />
            </NavMenuBadgeIcon>
            <NavMenuIcon v-else :icon="item.icon" />
          </span>
          <Transition name="sidebar-fade">
            <span v-if="!collapsed" class="nav-item__label">{{ item.label }}</span>
          </Transition>
          <span v-if="selectedKeys[0] === item.key" class="nav-item__accent" />

          <!-- Collapsed tooltip -->
          <span v-if="collapsed" class="nav-item__tooltip">{{ item.label }}</span>
        </button>

        <!-- ── Group Item (has children) ── -->
        <template v-else>
          <!-- Expanded: group header + items -->
          <template v-if="!collapsed">
            <button
              class="nav-group-header"
              :aria-expanded="expandedGroups.has(item.key)"
              @click="toggleGroup(item.key)"
            >
              <span class="nav-group-header__icon">
                <NavMenuIcon :icon="item.icon" />
              </span>
              <span class="nav-group-header__label">{{ item.label }}</span>
              <span class="nav-group-header__arrow" :class="{ 'is-open': expandedGroups.has(item.key) }">
                <CaretDownOutlined />
              </span>
            </button>

            <Transition name="sidebar-slide">
              <div v-show="expandedGroups.has(item.key)" class="nav-group-items">
                <button
                  v-for="child in item.children"
                  :key="child.key"
                  class="nav-item"
                  :class="{ 'nav-item--active': selectedKeys[0] === child.key }"
                  @click="handleNavClick(child.key, child.path)"
                >
                  <span class="nav-item__label nav-item__label--child">{{ child.label }}</span>
                  <NavMenuBadgeIcon
                    v-if="child.badge"
                    :badge="child.badge"
                    :workbench-badge-count="workbenchBadgeCount"
                    :exception-badge-count="exceptionBadgeCount"
                  />
                  <span v-if="selectedKeys[0] === child.key" class="nav-item__accent" />
                </button>
              </div>
            </Transition>
          </template>

          <!-- Collapsed: icon button with popup -->
          <div
            v-else
            class="nav-collapsed-group"
            @mouseenter="openPopup(item.key)"
            @mouseleave="startPopupClose()"
          >
            <button
              class="nav-item nav-item--leaf"
              :class="{ 'nav-item--active': isGroupActive(item) }"
            >
              <span class="nav-item__icon">
                <NavMenuIcon :icon="item.icon" />
              </span>
            </button>

            <!-- Popup panel -->
            <Transition name="popup">
              <div
                v-if="activePopup === item.key"
                class="nav-popup"
                @mouseenter="cancelPopupClose()"
                @mouseleave="startPopupClose()"
              >
                <div class="nav-popup__title">{{ item.label }}</div>
                <div class="nav-popup__items">
                  <button
                    v-for="child in item.children"
                    :key="child.key"
                    class="nav-popup__item"
                    :class="{ 'nav-popup__item--active': selectedKeys[0] === child.key }"
                    @click="handleNavClick(child.key, child.path); closePopup()"
                  >
                    <span class="nav-popup__label">{{ child.label }}</span>
                    <NavMenuBadgeIcon
                      v-if="child.badge"
                      :badge="child.badge"
                      :workbench-badge-count="workbenchBadgeCount"
                      :exception-badge-count="exceptionBadgeCount"
                    />
                  </button>
                </div>
              </div>
            </Transition>
          </div>
        </template>
      </template>
    </nav>

    <!-- ════ Footer ════ -->
    <div class="sidebar-footer">
      <span class="sidebar-footer__dot" :class="{ online: realtimeConnected }" />
      <Transition name="sidebar-fade">
        <span v-if="!collapsed" class="sidebar-footer__text">
          {{ realtimeConnected ? '实时连接正常' : '连接断开' }}
        </span>
      </Transition>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { CaretDownOutlined } from '@ant-design/icons-vue'
import NavMenuIcon from '@/components/layout/NavMenuIcon.vue'
import NavMenuBadgeIcon from '@/components/layout/NavMenuBadgeIcon.vue'
import DispatchFlowLogo from '@/components/brand/DispatchFlowLogo.vue'
import { useWorkbenchStore } from '@/stores/workbench'
import { useAuthStore } from '@/stores/auth'
import { useRealtimeStore } from '@/stores/realtime'
import {
  NAV_PARENT_KEY_MAP,
  NAV_PATH_MAP,
  NAVIGATION_TREE,
  filterNavByRole,
  resolveMenuKeyFromPath,
} from '@/config/navigation'
import type { NavItem } from '@/config/navigation'

// ── Props & Emits ─────────────────────────────────────────
defineProps<{
  collapsed: boolean
}>()

const emit = defineEmits<{
  navigate: []
}>()

// ── Stores ────────────────────────────────────────────────
const router = useRouter()
const authStore = useAuthStore()
const workbenchStore = useWorkbenchStore()
const realtimeStore = useRealtimeStore()

// ── Navigation State ──────────────────────────────────────
const visibleNavItems = computed(() => filterNavByRole(NAVIGATION_TREE, authStore.user?.role))
const selectedKeys = ref<string[]>(['workbench'])
const expandedGroups = ref<Set<string>>(new Set())
const activePopup = ref<string | null>(null)
let popupTimer: ReturnType<typeof setTimeout> | null = null

const workbenchBadgeCount = computed(
  () => workbenchStore.pendingCount + workbenchStore.manualPendingCount + workbenchStore.openExceptionCount,
)
const exceptionBadgeCount = computed(() => workbenchStore.openExceptionCount)
const realtimeConnected = computed(() => realtimeStore.connected)

// ── Helpers ───────────────────────────────────────────────
function hasChildren(item: NavItem): boolean {
  return (item.children?.length ?? 0) > 0
}

function isGroupActive(item: NavItem): boolean {
  if (!hasChildren(item)) return false
  return item.children!.some((c) => selectedKeys.value[0] === c.key)
}

// ── Group Expand/Collapse ─────────────────────────────────
function toggleGroup(key: string) {
  const next = new Set(expandedGroups.value)
  if (next.has(key)) {
    next.delete(key)
  } else {
    next.add(key)
  }
  expandedGroups.value = next
}

// ── Navigation ────────────────────────────────────────────
function handleNavClick(key: string, path?: string) {
  const resolved = path ?? NAV_PATH_MAP[key]
  if (resolved) {
    router.push(resolved)
    emit('navigate')
  }
}

// ── Collapsed Popup ───────────────────────────────────────
function openPopup(key: string) {
  cancelPopupClose()
  activePopup.value = key
}

function startPopupClose() {
  popupTimer = setTimeout(() => {
    activePopup.value = null
  }, 150)
}

function cancelPopupClose() {
  if (popupTimer) {
    clearTimeout(popupTimer)
    popupTimer = null
  }
}

function closePopup() {
  activePopup.value = null
}

// ── Route sync ────────────────────────────────────────────
watch(
  () => router.currentRoute.value.path,
  (path) => {
    const menuKey = resolveMenuKeyFromPath(path)
    if (menuKey) {
      selectedKeys.value = [menuKey]
      const parentKey = NAV_PARENT_KEY_MAP[menuKey]
      if (parentKey && !expandedGroups.value.has(parentKey)) {
        expandedGroups.value = new Set([...expandedGroups.value, parentKey])
      }
    }
  },
  { immediate: true },
)
</script>

<style scoped lang="less">
/* ═══════════════════════════════════════════════════════════
   DispatchFlow Sidebar — Premium Custom Navigation
   ═══════════════════════════════════════════════════════════ */

.sidebar {
  display: flex;
  flex-direction: column;
  height: 100%;
  position: relative;
  overflow: visible; /* allow popup overflow */

  &::before {
    content: '';
    position: absolute;
    inset: 0;
    pointer-events: none;
    z-index: 0;
    background:
      radial-gradient(ellipse 70% 40% at 0% 20%, rgba(34, 199, 230, 0.03) 0%, transparent 60%),
      radial-gradient(ellipse 50% 30% at 100% 85%, rgba(34, 199, 230, 0.02) 0%, transparent 60%);
  }
}

/* ── Logo ────────────────────────────────────────────────── */
.sidebar-logo {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  height: 64px;
  padding: 0 20px;
  gap: 12px;
  border-bottom: 1px solid var(--fsd-border);
  overflow: hidden;
  flex-shrink: 0;

  &--collapsed {
    padding: 0;
    justify-content: center;
    gap: 0;
  }
}

.sidebar-logo__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border-radius: var(--fsd-radius);
  box-shadow: 0 4px 14px rgba(34, 199, 230, 0.28);

  :deep(svg) {
    width: 34px;
    height: 34px;
    display: block;
    border-radius: var(--fsd-radius);
  }
}

.sidebar-logo__text {
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
}

.sidebar-logo__title {
  font-family: var(--fsd-font-sans);
  font-size: 16px;
  font-weight: var(--fsd-font-bold);
  letter-spacing: -0.01em;
  line-height: 1.2;
  background: linear-gradient(90deg, #eef3f9 0%, #b7e9f4 100%);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.sidebar-logo__sub {
  font-size: 11px;
  color: var(--fsd-text-tertiary);
  letter-spacing: 0.06em;
  margin-top: 1px;
}

/* ── Nav Container ───────────────────────────────────────── */
.sidebar-nav {
  position: relative;
  z-index: 1;
  flex: 1;
  overflow-y: auto;
  overflow-x: visible;
  padding: 8px 10px;

  scrollbar-width: thin;
  scrollbar-color: rgba(255, 255, 255, 0.06) transparent;

  &::-webkit-scrollbar { width: 4px; }
  &::-webkit-scrollbar-track { background: transparent; }
  &::-webkit-scrollbar-thumb {
    background: rgba(255, 255, 255, 0.06);
    border-radius: 2px;
    &:hover { background: rgba(255, 255, 255, 0.12); }
  }
}

/* ── Group Header (Expanded) ─────────────────────────────── */
.nav-group-header {
  display: flex;
  align-items: center;
  width: 100%;
  gap: 12px;
  padding: 10px 10px;
  margin-top: 6px;
  border: none;
  border-radius: var(--fsd-radius);
  background: transparent;
  color: var(--fsd-text-secondary);
  font-family: var(--fsd-font-sans);
  font-size: var(--fsd-text-sm);
  font-weight: var(--fsd-font-semibold);
  cursor: pointer;
  user-select: none;
  text-align: left;
  transition:
    background var(--fsd-duration-fast) var(--fsd-ease),
    color var(--fsd-duration-fast) var(--fsd-ease);

  &:hover {
    background: var(--fsd-bg-hover);
    color: var(--fsd-text-primary);
  }

  &:focus-visible {
    outline: 2px solid var(--fsd-accent);
    outline-offset: -2px;
  }
}

.nav-group-header__icon {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  width: 20px;
  height: 20px;
  color: inherit;

  :deep(svg) {
    color: inherit;
  }
}

.nav-group-header__label {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.nav-group-header__arrow {
  font-size: 12px;
  color: var(--fsd-text-tertiary);
  transition: transform var(--fsd-duration-normal) var(--fsd-ease);

  &.is-open {
    transform: rotate(180deg);
  }
}

/* ── Group Items (Expanded sub-list) ─────────────────────── */
.nav-group-items {
  display: flex;
  flex-direction: column;
  gap: 1px;
  padding: 2px 0;
}

/* ── Nav Item (Shared) ───────────────────────────────────── */
.nav-item {
  position: relative;
  display: flex;
  align-items: center;
  width: 100%;
  gap: 12px;
  padding: 9px 10px;
  border: none;
  border-radius: var(--fsd-radius);
  background: transparent;
  color: var(--fsd-text-secondary);
  font-family: var(--fsd-font-sans);
  font-size: var(--fsd-text-sm);
  font-weight: var(--fsd-font-medium);
  cursor: pointer;
  user-select: none;
  text-align: left;
  overflow: hidden;
  transition:
    background var(--fsd-duration-normal) var(--fsd-ease),
    color var(--fsd-duration-normal) var(--fsd-ease),
    transform 200ms var(--fsd-ease);

  &--leaf {
    padding: 10px 10px;
    margin-bottom: 2px;
  }

  &:hover {
    background: var(--fsd-bg-hover);
    color: var(--fsd-text-primary);
    transform: translateX(2px);
  }

  &--active {
    background: var(--fsd-accent-glow);
    color: var(--fsd-accent);
  }

  &:active {
    transform: scale(0.97);
    transition: transform 80ms var(--fsd-ease);
  }

  &:focus-visible {
    outline: 2px solid var(--fsd-accent);
    outline-offset: -2px;
  }
}

.nav-item__icon {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;

  :deep(svg) {
    color: var(--fsd-text-secondary);
    transition: color var(--fsd-duration-normal) var(--fsd-ease);
  }

  .nav-item:hover & :deep(svg),
  .nav-item--active & :deep(svg) {
    color: inherit;
  }
}

.nav-item__label {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 1;

  &--child {
    font-weight: var(--fsd-font-regular);
    padding-left: 32px; /* indent under group */
  }
}

/* Active accent bar */
.nav-item__accent {
  position: absolute;
  left: -10px;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 16px;
  border-radius: 0 3px 3px 0;
  background: var(--fsd-accent);
  animation: accentIn 250ms var(--fsd-ease) both;
}

@keyframes accentIn {
  from { height: 0; opacity: 0; }
  to   { height: 16px; opacity: 1; }
}

/* ── Collapsed Tooltip (leaf items) ──────────────────────── */
.nav-item__tooltip {
  position: absolute;
  left: calc(100% + 12px);
  top: 50%;
  transform: translateY(-50%);
  padding: 6px 12px;
  background: #1a2332;
  color: var(--fsd-text-primary);
  font-size: 12px;
  font-weight: var(--fsd-font-medium);
  border-radius: 6px;
  white-space: nowrap;
  pointer-events: none;
  z-index: 60;
  opacity: 0;
  transition: opacity 150ms ease;
  box-shadow:
    0 4px 16px rgba(0, 0, 0, 0.4),
    0 0 0 1px var(--fsd-border);

  .nav-item:hover & {
    opacity: 1;
  }
}

/* ── Collapsed Popup (group items) ───────────────────────── */
.nav-collapsed-group {
  position: relative;
  margin-bottom: 2px;
}

.nav-popup {
  position: absolute;
  left: calc(100% + 8px);
  top: 0;
  min-width: 180px;
  background: #141e2b;
  border-radius: var(--fsd-radius-lg, 10px);
  padding: 8px;
  z-index: 70;
  box-shadow:
    0 8px 32px rgba(0, 0, 0, 0.5),
    0 0 0 1px var(--fsd-border);
}

.nav-popup__title {
  padding: 6px 10px 8px;
  font-size: 11px;
  font-weight: var(--fsd-font-semibold);
  color: var(--fsd-text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.nav-popup__items {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.nav-popup__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 7px 10px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--fsd-text-secondary);
  font-family: var(--fsd-font-sans);
  font-size: 13px;
  font-weight: var(--fsd-font-medium);
  cursor: pointer;
  text-align: left;
  transition:
    background var(--fsd-duration-fast) var(--fsd-ease),
    color var(--fsd-duration-fast) var(--fsd-ease);

  &:hover {
    background: var(--fsd-bg-hover);
    color: var(--fsd-text-primary);
  }

  &--active {
    color: var(--fsd-accent);
    background: var(--fsd-accent-glow);
  }
}

.nav-popup__label {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ── Footer ──────────────────────────────────────────────── */
.sidebar-footer {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 20px;
  border-top: 1px solid var(--fsd-border);
  flex-shrink: 0;
  overflow: hidden;
}

.sidebar-footer__dot {
  flex-shrink: 0;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--fsd-text-tertiary);
  transition: background var(--fsd-duration-normal) var(--fsd-ease);

  &.online {
    background: var(--fsd-success, #2de08a);
    box-shadow: 0 0 8px rgba(45, 224, 138, 0.5);
  }
}

.sidebar-footer__text {
  font-size: 12px;
  color: var(--fsd-text-tertiary);
  white-space: nowrap;
}

/* ── Transitions ─────────────────────────────────────────── */
.sidebar-fade-enter-active,
.sidebar-fade-leave-active {
  transition:
    opacity var(--fsd-duration-normal) var(--fsd-ease),
    max-width var(--fsd-duration-normal) var(--fsd-ease);
  overflow: hidden;
}
.sidebar-fade-enter-from,
.sidebar-fade-leave-to {
  opacity: 0;
  max-width: 0;
}

.sidebar-slide-enter-active,
.sidebar-slide-leave-active {
  transition:
    opacity var(--fsd-duration-fast) var(--fsd-ease),
    max-height var(--fsd-duration-fast) var(--fsd-ease);
  overflow: hidden;
}
.sidebar-slide-enter-from,
.sidebar-slide-leave-to {
  opacity: 0;
  max-height: 0;
}
.sidebar-slide-enter-to,
.sidebar-slide-leave-from {
  max-height: 800px;
}

.popup-enter-active {
  transition:
    opacity 120ms ease,
    transform 150ms var(--fsd-ease);
}
.popup-leave-active {
  transition:
    opacity 100ms ease,
    transform 120ms var(--fsd-ease);
}
.popup-enter-from {
  opacity: 0;
  transform: translateX(-6px) scale(0.96);
}
.popup-leave-to {
  opacity: 0;
  transform: translateX(-4px) scale(0.97);
}

/* ── Collapsed State Overrides ───────────────────────────── */
.sidebar--collapsed {
  overflow: visible;

  .sidebar-nav {
    padding: 8px 6px;
    overflow: visible; /* allow popup to render beyond bounds */
  }

  .nav-item {
    justify-content: center;
    padding: 10px 0;
    gap: 0;

    &:hover {
      transform: none; /* no slide in collapsed */
    }
  }

  .nav-item__accent {
    left: auto;
    right: -6px;
  }

  .sidebar-footer {
    justify-content: center;
    padding: 12px 0;
  }
}
</style>
