<template>
  <div class="mobile-profile-page">
    <header class="profile-header">
      <div class="user-card">
        <div class="user-avatar">
          <svg viewBox="0 0 24 24" width="36" height="36" fill="currentColor">
            <circle cx="12" cy="8" r="4" />
            <path d="M4 21v-1a6 6 0 0 1 6-6h4a6 6 0 0 1 6 6v1z" />
          </svg>
        </div>
        <div class="user-info">
          <h2 class="user-name">找家纺商家</h2>
          <p class="user-tag">叠石桥 · L1 试点</p>
        </div>
      </div>
      <div class="quick-stats">
        <div class="stat-item">
          <span class="stat-value">{{ stats.totalOrders }}</span>
          <span class="stat-label">总订单</span>
        </div>
        <div class="stat-divider" />
        <div class="stat-item">
          <span class="stat-value">{{ stats.activeOrders }}</span>
          <span class="stat-label">进行中</span>
        </div>
        <div class="stat-divider" />
        <div class="stat-item">
          <span class="stat-value">{{ stats.completedOrders }}</span>
          <span class="stat-label">已完成</span>
        </div>
      </div>
    </header>

    <main class="profile-main">
      <section class="menu-section">
        <h3 class="section-title">配送模式</h3>
        <div class="mode-switcher">
          <button
            type="button"
            class="mode-btn"
            :class="{ 'mode-active': orderMode === 'geo' }"
            :aria-pressed="orderMode === 'geo'"
            @click="switchMode('geo')"
          >
            <span class="mode-icon">🗺️</span>
            <span class="mode-name">真实地图</span>
            <span class="mode-desc">叠石桥沿路配送</span>
          </button>
          <button
            type="button"
            class="mode-btn"
            :class="{ 'mode-active': orderMode === 'schematic' }"
            :aria-pressed="orderMode === 'schematic'"
            @click="switchMode('schematic')"
          >
            <span class="mode-icon">🏭</span>
            <span class="mode-name">园区示意</span>
            <span class="mode-desc">仿真园区内部</span>
          </button>
        </div>
      </section>

      <section class="menu-section">
        <h3 class="section-title">服务区域</h3>
        <div class="zone-list">
          <div v-for="zone in deliveryZones" :key="zone.code" class="zone-item">
            <span class="zone-color" :style="{ background: zone.color }" />
            <div class="zone-text">
              <span class="zone-name">{{ zone.name }}</span>
              <span class="zone-desc">{{ zone.description }}</span>
            </div>
          </div>
        </div>
      </section>

      <section class="menu-section">
        <h3 class="section-title">常用功能</h3>
        <div class="menu-list">
          <router-link to="/mobile/orders" class="menu-item">
            <span class="menu-icon">
              <svg
                viewBox="0 0 24 24"
                width="20"
                height="20"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
              >
                <path
                  d="M9 4h6a1 1 0 0 1 1 1v1h3a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h3V5a1 1 0 0 1 1-1z"
                />
                <path d="M9 12h6M9 16h4" />
              </svg>
            </span>
            <span class="menu-label">历史订单</span>
            <span class="menu-arrow">›</span>
          </router-link>
          <a :href="trackingShareUrl" class="menu-item" target="_blank" v-if="trackingShareUrl">
            <span class="menu-icon">
              <svg
                viewBox="0 0 24 24"
                width="20"
                height="20"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
              >
                <circle cx="18" cy="5" r="3" />
                <circle cx="6" cy="12" r="3" />
                <circle cx="18" cy="19" r="3" />
                <path d="M8.6 13.5l6.8 4M15.4 6.5l-6.8 4" />
              </svg>
            </span>
            <span class="menu-label">分享实时位置</span>
            <span class="menu-arrow">›</span>
          </a>
          <a href="https://www.aplicity.online" class="menu-item" target="_blank">
            <span class="menu-icon">
              <svg
                viewBox="0 0 24 24"
                width="20"
                height="20"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
              >
                <rect x="3" y="4" width="18" height="14" rx="2" />
                <path d="M3 10h18" />
              </svg>
            </span>
            <span class="menu-label">访问管理后台</span>
            <span class="menu-arrow">›</span>
          </a>
        </div>
      </section>

      <section v-if="showApiKeySettings" class="menu-section">
        <h3 class="section-title">开发者设置</h3>
        <div class="api-key-panel">
          <label class="api-key-field">
            <span>X-Mobile-Api-Key</span>
            <input
              v-model="mobileApiKey"
              type="password"
              autocomplete="off"
              placeholder="留空则使用环境变量"
              @change="persistMobileApiKey"
            />
          </label>
          <p class="api-key-note">仅开发环境可见，用于下单接口鉴权。</p>
        </div>
      </section>

      <section class="menu-section">
        <h3 class="section-title">关于</h3>
        <div class="about-grid">
          <div class="about-item">
            <span class="about-label">版本</span>
            <span class="about-value">v2.0 · V37</span>
          </div>
          <div class="about-item">
            <span class="about-label">服务区域</span>
            <span class="about-value">叠石桥家纺城</span>
          </div>
          <div class="about-item">
            <span class="about-label">配送站点</span>
            <span class="about-value">{{ stationCount }} 个</span>
          </div>
          <div class="about-item">
            <span class="about-label">配送分区</span>
            <span class="about-value">{{ deliveryZones.length }} 个</span>
          </div>
        </div>
      </section>
    </main>

    <MobileTabBar :active-order-count="stats.activeOrders" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import MobileTabBar from '@/components/mobile/MobileTabBar.vue'
import { getParkOrders, getParkStations, listParks } from '@/api/park'
import {
  filterGeoDeliveryOrders,
  filterSchematicOrders,
  filterGeoDeliveryStations,
  ZJF_DELIVERY_ZONES,
} from '@/maps'
import {
  loadMobileOrderMode,
  persistMobileOrderMode,
  buildGeoTrackingLink,
} from '@/constants/parkDelivery'
import type { MobileOrderMode } from '@/constants/parkDelivery'
import type { ParkOrderSnapshot, ParkStation } from '@/types/park'

const orderMode = ref<MobileOrderMode>(loadMobileOrderMode())
const orders = ref<ParkOrderSnapshot[]>([])
const stations = ref<ParkStation[]>([])
const mobileApiKey = ref('')
const showApiKeySettings = import.meta.env.DEV

const deliveryZones = ZJF_DELIVERY_ZONES

const stats = computed(() => {
  const visible =
    orderMode.value === 'schematic'
      ? filterSchematicOrders(orders.value)
      : filterGeoDeliveryOrders(orders.value)
  return {
    totalOrders: visible.length,
    activeOrders: visible.filter((o) => !['COMPLETED', 'FAILED'].includes(o.runtimeStage)).length,
    completedOrders: visible.filter((o) => o.runtimeStage === 'COMPLETED').length,
  }
})

const stationCount = computed(() => filterGeoDeliveryStations(stations.value).length)

const trackingShareUrl = computed(() => {
  const link = buildGeoTrackingLink(undefined, undefined)
  return link ? `${window.location.origin}${link}` : null
})

function switchMode(mode: MobileOrderMode) {
  if (orderMode.value === mode) return
  orderMode.value = mode
  persistMobileOrderMode(mode)
}

function resolveDefaultMobileApiKey() {
  return (
    sessionStorage.getItem('fsd_mobile_api_key')?.trim() ||
    (import.meta.env.VITE_MOBILE_API_KEY as string | undefined)?.trim() ||
    ''
  )
}

function persistMobileApiKey() {
  const trimmed = mobileApiKey.value.trim()
  if (trimmed) sessionStorage.setItem('fsd_mobile_api_key', trimmed)
  else sessionStorage.removeItem('fsd_mobile_api_key')
}

onMounted(async () => {
  mobileApiKey.value = resolveDefaultMobileApiKey()
  try {
    const parkResp = await listParks()
    const parkId = parkResp.data?.find((p) => p.defaultPark)?.parkId || parkResp.data?.[0]?.parkId
    if (parkId) {
      const [orderResp, stationResp] = await Promise.all([
        getParkOrders({}),
        getParkStations(parkId),
      ])
      orders.value = orderResp.data || []
      stations.value = stationResp.data || []
    }
  } catch {
    // 忽略错误，页面仍可显示
  }
})
</script>

<style scoped lang="less">
.mobile-profile-page {
  --mobile-page: #f5f7f8;
  --mobile-surface: #ffffff;
  --mobile-border: #dbe1e5;
  --mobile-text: #172027;
  --mobile-secondary: #5f6c76;
  --mobile-tertiary: #7f8a92;
  min-height: 100vh;
  min-height: 100dvh;
  background: var(--mobile-page);
  color: var(--mobile-text);
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Helvetica Neue', sans-serif;
  padding-bottom: calc(72px + env(safe-area-inset-bottom, 0px));
}

.profile-header {
  padding: calc(var(--fsd-space-4) + env(safe-area-inset-top, 0px)) var(--fsd-space-4)
    var(--fsd-space-4);
  background: var(--fsd-surface-raised);
  color: var(--fsd-text-primary);
}

.user-card {
  display: flex;
  align-items: center;
  gap: var(--fsd-space-3);
  margin-bottom: var(--fsd-space-4);
}

.user-avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  border-radius: var(--fsd-radius-full);
  background: var(--fsd-neutral-bg);
  color: var(--fsd-text-primary);
  flex-shrink: 0;
}

.user-info {
  flex: 1;
}

.user-name {
  margin: 0;
  color: var(--fsd-text-primary);
  font-size: var(--fsd-text-lg);
  font-weight: var(--fsd-font-semibold);
}

.user-tag {
  margin: var(--fsd-space-1) 0 0;
  color: var(--fsd-text-secondary);
  font-size: var(--fsd-text-xs);
}

.quick-stats {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--fsd-space-3) 0 0;
  border-top: 1px solid var(--fsd-border-split);
}

.stat-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.stat-value {
  color: var(--fsd-text-primary);
  font-family: var(--fsd-font-mono);
  font-size: var(--fsd-text-lg);
  font-weight: var(--fsd-font-semibold);
}

.stat-label {
  color: var(--fsd-text-secondary);
  font-size: 11px;
}

.stat-divider {
  width: 1px;
  height: 28px;
  background: var(--fsd-border-split);
}

.profile-main {
  display: flex;
  flex-direction: column;
  gap: 28px;
  width: min(100%, var(--fsd-mobile-max-width));
  margin: 0 auto;
  padding: var(--fsd-space-4);
}

.menu-section {
  display: flex;
  flex-direction: column;
  gap: var(--fsd-space-2);
}

.section-title {
  margin: 0;
  color: var(--mobile-secondary);
  font-size: var(--fsd-text-sm);
  font-weight: var(--fsd-font-semibold);
}

.mode-switcher {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--fsd-space-2);
}

.mode-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--fsd-space-1);
  min-height: 96px;
  padding: var(--fsd-space-3);
  border: 1px solid var(--mobile-border);
  border-radius: var(--fsd-radius-sm);
  background: var(--mobile-surface);
  cursor: pointer;
  transition:
    background-color var(--fsd-transition-base),
    border-color var(--fsd-transition-base);

  &.mode-active {
    border-color: var(--fsd-accent-border);
    background: var(--fsd-accent-selected);
  }

  &:focus-visible {
    outline: 2px solid var(--fsd-accent-strong);
    outline-offset: 2px;
  }
}

.mode-icon {
  font-size: 24px;
}
.mode-name {
  color: var(--mobile-text);
  font-size: var(--fsd-text-sm);
  font-weight: var(--fsd-font-semibold);
}
.mode-desc {
  color: var(--mobile-secondary);
  font-size: 11px;
  text-align: center;
}

.zone-list {
  display: flex;
  flex-direction: column;
  border-top: 1px solid var(--mobile-border);
}

.zone-item {
  display: flex;
  align-items: center;
  gap: var(--fsd-space-2);
  min-height: var(--fsd-touch-target-min);
  padding: var(--fsd-space-2) 0;
  border-bottom: 1px solid var(--mobile-border);
}

.zone-color {
  width: 12px;
  height: 12px;
  border-radius: 4px;
  flex-shrink: 0;
}

.zone-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.zone-name {
  color: var(--mobile-text);
  font-size: var(--fsd-text-sm);
  font-weight: var(--fsd-font-medium);
}
.zone-desc {
  color: var(--mobile-secondary);
  font-size: 11px;
}

.menu-list {
  display: flex;
  flex-direction: column;
  border-top: 1px solid var(--mobile-border);
}

.menu-item {
  display: flex;
  align-items: center;
  gap: var(--fsd-space-3);
  min-height: var(--fsd-touch-target-min);
  padding: var(--fsd-space-3) 0;
  border-bottom: 1px solid var(--mobile-border);
  color: var(--mobile-text);
  text-decoration: none;
  transition: background-color var(--fsd-transition-base);

  &:active {
    background: var(--fsd-bg-active);
  }
}

.menu-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--mobile-secondary);
}

.menu-label {
  flex: 1;
  font-size: var(--fsd-text-sm);
  font-weight: var(--fsd-font-medium);
}

.menu-arrow {
  color: var(--mobile-tertiary);
  font-size: 18px;
}

.api-key-panel {
  padding: var(--fsd-space-3);
  border: 1px solid var(--mobile-border);
  border-radius: var(--fsd-radius-sm);
  background: var(--mobile-surface);
}

.api-key-field {
  display: flex;
  flex-direction: column;
  gap: var(--fsd-space-1);

  span {
    color: var(--mobile-secondary);
    font-size: 11px;
  }

  input {
    width: 100%;
    min-height: var(--fsd-touch-target-min);
    padding: 0 var(--fsd-space-3);
    border: 1px solid var(--mobile-border);
    border-radius: var(--fsd-radius-sm);
    outline: none;
    background: var(--mobile-page);
    color: var(--mobile-text);
    font-family: var(--fsd-font-mono);
    font-size: var(--fsd-text-xs);

    &:focus {
      border-color: var(--fsd-border-active);
      outline: 2px solid var(--fsd-accent-strong);
      outline-offset: 1px;
      background: var(--mobile-surface);
    }
  }
}

.api-key-note {
  margin: var(--fsd-space-2) 0 0;
  color: var(--mobile-secondary);
  font-size: 11px;
}

.about-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  border-top: 1px solid var(--mobile-border);
  border-left: 1px solid var(--mobile-border);
}

.about-item {
  display: flex;
  flex-direction: column;
  gap: var(--fsd-space-1);
  min-width: 0;
  padding: var(--fsd-space-3);
  border-right: 1px solid var(--mobile-border);
  border-bottom: 1px solid var(--mobile-border);
}

.about-label {
  color: var(--mobile-secondary);
  font-size: 11px;
}
.about-value {
  overflow: hidden;
  color: var(--mobile-text);
  font-size: var(--fsd-text-sm);
  font-weight: var(--fsd-font-medium);
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
