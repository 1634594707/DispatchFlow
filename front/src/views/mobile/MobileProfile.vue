<template>
  <div class="mobile-profile-page">
    <header class="profile-header">
      <div class="user-card">
        <div class="user-avatar">
          <svg viewBox="0 0 24 24" width="36" height="36" fill="currentColor"><circle cx="12" cy="8" r="4"/><path d="M4 21v-1a6 6 0 0 1 6-6h4a6 6 0 0 1 6 6v1z"/></svg>
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
            class="mode-btn"
            :class="{ 'mode-active': orderMode === 'geo' }"
            @click="switchMode('geo')"
          >
            <span class="mode-icon">🗺️</span>
            <span class="mode-name">真实地图</span>
            <span class="mode-desc">叠石桥沿路配送</span>
          </button>
          <button
            class="mode-btn"
            :class="{ 'mode-active': orderMode === 'schematic' }"
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
              <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 4h6a1 1 0 0 1 1 1v1h3a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h3V5a1 1 0 0 1 1-1z"/><path d="M9 12h6M9 16h4"/></svg>
            </span>
            <span class="menu-label">历史订单</span>
            <span class="menu-arrow">›</span>
          </router-link>
          <a :href="trackingShareUrl" class="menu-item" target="_blank" v-if="trackingShareUrl">
            <span class="menu-icon">
              <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2"><circle cx="18" cy="5" r="3"/><circle cx="6" cy="12" r="3"/><circle cx="18" cy="19" r="3"/><path d="M8.6 13.5l6.8 4M15.4 6.5l-6.8 4"/></svg>
            </span>
            <span class="menu-label">分享实时位置</span>
            <span class="menu-arrow">›</span>
          </a>
          <a href="https://www.aplicity.online" class="menu-item" target="_blank">
            <span class="menu-icon">
              <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="4" width="18" height="14" rx="2"/><path d="M3 10h18"/></svg>
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
import { loadMobileOrderMode, persistMobileOrderMode, buildGeoTrackingLink } from '@/constants/parkDelivery'
import type { MobileOrderMode } from '@/constants/parkDelivery'
import type { ParkOrderSnapshot, ParkStation } from '@/types/park'

const orderMode = ref<MobileOrderMode>(loadMobileOrderMode())
const orders = ref<ParkOrderSnapshot[]>([])
const stations = ref<ParkStation[]>([])
const mobileApiKey = ref('')
const showApiKeySettings = import.meta.env.DEV

const deliveryZones = ZJF_DELIVERY_ZONES

const stats = computed(() => {
  const visible = orderMode.value === 'schematic'
    ? filterSchematicOrders(orders.value)
    : filterGeoDeliveryOrders(orders.value)
  return {
    totalOrders: visible.length,
    activeOrders: visible.filter(o => !['COMPLETED', 'FAILED'].includes(o.runtimeStage)).length,
    completedOrders: visible.filter(o => o.runtimeStage === 'COMPLETED').length,
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
    const parkId = parkResp.data?.find(p => p.defaultPark)?.parkId || parkResp.data?.[0]?.parkId
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
  min-height: 100vh;
  min-height: 100dvh;
  background: #f5f6fa;
  color: #333;
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Helvetica Neue', sans-serif;
  padding-bottom: calc(72px + env(safe-area-inset-bottom, 0px));
}

.profile-header {
  padding: calc(20px + env(safe-area-inset-top, 0px)) 20px 24px;
  background: linear-gradient(135deg, #1989fa 0%, #096dd9 100%);
  color: #fff;
}

.user-card {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 24px;
}

.user-avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.2);
  color: #fff;
  flex-shrink: 0;
}

.user-info {
  flex: 1;
}

.user-name {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #fff;
}

.user-tag {
  margin: 4px 0 0;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.8);
}

.quick-stats {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.15);
  backdrop-filter: blur(10px);
}

.stat-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.stat-value {
  font-size: 22px;
  font-weight: 700;
  color: #fff;
}

.stat-label {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.85);
}

.stat-divider {
  width: 1px;
  height: 28px;
  background: rgba(255, 255, 255, 0.2);
}

.profile-main {
  width: min(100%, var(--fsd-mobile-max-width));
  margin: 0 auto;
  padding: 20px 16px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.menu-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.section-title {
  margin: 0 0 2px;
  font-size: 13px;
  font-weight: 600;
  color: #999;
  letter-spacing: 0.04em;
}

.mode-switcher {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.mode-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 16px 12px;
  border-radius: 12px;
  border: 1px solid #e8e8e8;
  background: #fff;
  cursor: pointer;
  transition: all 0.2s ease;

  &.mode-active {
    border-color: #1989fa;
    background: #e6f4ff;
  }
  &:active { transform: scale(0.97); }
}

.mode-icon { font-size: 24px; }
.mode-name { font-size: 13px; font-weight: 600; color: #333; }
.mode-desc { font-size: 11px; color: #999; }

.zone-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px 16px;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
}

.zone-item {
  display: flex;
  align-items: center;
  gap: 10px;
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

.zone-name { font-size: 13px; font-weight: 500; color: #333; }
.zone-desc { font-size: 11px; color: #999; }

.menu-list {
  display: flex;
  flex-direction: column;
  border-radius: 12px;
  overflow: hidden;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  text-decoration: none;
  color: #333;
  border-bottom: 1px solid #f5f5f5;
  transition: background 0.15s ease;

  &:last-child { border-bottom: none; }
  &:active { background: #f5f5f5; }
}

.menu-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #1989fa;
}

.menu-label {
  flex: 1;
  font-size: 14px;
  font-weight: 500;
}

.menu-arrow {
  font-size: 18px;
  color: #ccc;
}

.api-key-panel {
  padding: 14px 16px;
  border-radius: 12px;
  background: #fff;
  border: 1px dashed #1989fa;
}

.api-key-field {
  display: flex;
  flex-direction: column;
  gap: 6px;

  span {
    font-size: 11px;
    color: #666;
  }

  input {
    width: 100%;
    height: 40px;
    padding: 0 12px;
    border-radius: 8px;
    border: 1px solid #e8e8e8;
    background: #f5f5f5;
    color: #333;
    font-family: monospace;
    font-size: 12px;
    outline: none;

    &:focus { border-color: #1989fa; background: #fff; }
  }
}

.api-key-note {
  margin: 8px 0 0;
  font-size: 11px;
  color: #999;
}

.about-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  padding: 16px;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
}

.about-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.about-label { font-size: 11px; color: #999; }
.about-value { font-size: 13px; font-weight: 500; color: #333; }
</style>
