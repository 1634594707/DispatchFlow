<template>
  <div class="mobile-orders-page">
    <header class="orders-header">
      <div class="header-top">
        <h1>我的订单</h1>
        <button class="refresh-btn" :class="{ 'refreshing': loading }" @click="refresh">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M21 12a9 9 0 1 1-3-6.7L21 8" />
            <path d="M21 3v5h-5" />
          </svg>
        </button>
      </div>
      <div class="filter-tabs">
        <button
          v-for="f in filters"
          :key="f.value"
          class="filter-tab"
          :class="{ 'filter-active': activeFilter === f.value }"
          @click="activeFilter = f.value"
        >
          {{ f.label }}
          <span v-if="f.count > 0" class="filter-count">{{ f.count }}</span>
        </button>
      </div>
    </header>

    <main class="orders-main">
      <div v-if="loading && orders.length === 0" class="loading-state">
        <div class="loading-spinner" />
        <p>加载中…</p>
      </div>

      <div v-else-if="filteredOrders.length === 0" class="empty-state">
        <div class="empty-icon">
          <svg viewBox="0 0 24 24" width="48" height="48" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
            <path d="M9 4h6a1 1 0 0 1 1 1v1h3a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h3V5a1 1 0 0 1 1-1z" />
            <path d="M9 12h6M9 16h4" />
          </svg>
        </div>
        <p class="empty-title">暂无订单</p>
        <p class="empty-hint">去首页下单试试吧</p>
        <router-link to="/mobile/order" class="empty-cta">去下单</router-link>
      </div>

      <div v-else class="orders-list">
        <article
          v-for="order in filteredOrders"
          :key="order.orderId"
          class="order-card"
          :class="`order-${stageClass(order.runtimeStage)}`"
          @click="goToTracking(order.orderId)"
        >
          <div class="order-card-head">
            <div class="order-id">
              <span class="order-no">#{{ order.orderId }}</span>
              <span v-if="order.deliveryZone === 'GEO_DELIVERY'" class="zone-tag zone-geo">地理配送</span>
              <span v-else class="zone-tag zone-schematic">园区内部</span>
            </div>
            <span class="order-stage" :class="`stage-${stageClass(order.runtimeStage)}`">
              {{ stageLabel(order.runtimeStage) }}
            </span>
          </div>

          <div class="order-route">
            <div class="route-point route-pickup">
              <span class="route-dot" />
              <div class="route-info">
                <span class="route-label">取</span>
                <span class="route-name">{{ order.pickupStation.stationName || order.pickupStation.stationCode }}</span>
              </div>
            </div>
            <div class="route-connector" />
            <div class="route-point route-dropoff">
              <span class="route-dot route-dot-end" />
              <div class="route-info">
                <span class="route-label">送</span>
                <span class="route-name">{{ order.dropoffStation.stationName || order.dropoffStation.stationCode }}</span>
              </div>
            </div>
          </div>

          <div class="order-card-foot">
            <span class="order-time">{{ formatTime(order.startTime || order.assignTime || order.updatedAt) }}</span>
            <span v-if="order.weight" class="order-weight">{{ order.weight }}kg</span>
            <span v-if="order.vehicleCode" class="order-vehicle">{{ order.vehicleCode }}</span>
          </div>
        </article>
      </div>
    </main>

    <MobileTabBar :active-order-count="activeCount" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import MobileTabBar from '@/components/mobile/MobileTabBar.vue'
import { getParkOrders } from '@/api/park'
import {
  filterGeoDeliveryOrders,
  filterSchematicOrders,
} from '@/maps'
import { loadMobileOrderMode } from '@/constants/parkDelivery'
import type { MobileOrderMode } from '@/constants/parkDelivery'
import type { ParkOrderSnapshot } from '@/types/park'

const router = useRouter()
const orders = ref<ParkOrderSnapshot[]>([])
const loading = ref(false)
const activeFilter = ref<'all' | 'active' | 'completed' | 'failed'>('all')
const orderMode = ref<MobileOrderMode>(loadMobileOrderMode())

const visibleOrders = computed(() =>
  orderMode.value === 'schematic'
    ? filterSchematicOrders(orders.value)
    : filterGeoDeliveryOrders(orders.value),
)

const activeOrders = computed(() =>
  visibleOrders.value.filter(o => !['COMPLETED', 'FAILED'].includes(o.runtimeStage)),
)

const completedOrders = computed(() =>
  visibleOrders.value.filter(o => o.runtimeStage === 'COMPLETED'),
)

const failedOrders = computed(() =>
  visibleOrders.value.filter(o => o.runtimeStage === 'FAILED'),
)

const activeCount = computed(() => activeOrders.value.length)

const filters = computed(() => [
  { label: '全部', value: 'all' as const, count: visibleOrders.value.length },
  { label: '进行中', value: 'active' as const, count: activeOrders.value.length },
  { label: '已完成', value: 'completed' as const, count: completedOrders.value.length },
  { label: '异常', value: 'failed' as const, count: failedOrders.value.length },
])

const filteredOrders = computed(() => {
  switch (activeFilter.value) {
    case 'active': return activeOrders.value
    case 'completed': return completedOrders.value
    case 'failed': return failedOrders.value
    default: return visibleOrders.value
  }
})

function stageLabel(stage: string): string {
  const labels: Record<string, string> = {
    PENDING: '待接单',
    ASSIGNED: '已派单',
    HEADING_TO_PICKUP: '前往取货',
    LOADING: '装货中',
    HEADING_TO_DROPOFF: '配送中',
    UNLOADING: '卸货中',
    RETURNING: '返程中',
    COMPLETED: '已完成',
    FAILED: '配送失败',
  }
  return labels[stage] || stage
}

function stageClass(stage: string): string {
  const classes: Record<string, string> = {
    PENDING: 'pending',
    ASSIGNED: 'assigned',
    HEADING_TO_PICKUP: 'transit',
    LOADING: 'loading',
    HEADING_TO_DROPOFF: 'transit',
    UNLOADING: 'loading',
    RETURNING: 'transit',
    COMPLETED: 'completed',
    FAILED: 'failed',
  }
  return classes[stage] || 'default'
}

function formatTime(dateStr?: string | null): string {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return d.toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

function goToTracking(orderId: number) {
  router.push({ path: '/mobile/order', query: { orderId: String(orderId) } })
}

async function fetchOrders() {
  loading.value = true
  try {
    const response = await getParkOrders({})
    orders.value = response.data || []
  } finally {
    loading.value = false
  }
}

async function refresh() {
  await fetchOrders()
}

onMounted(() => {
  fetchOrders()
})
</script>

<style scoped lang="less">
.mobile-orders-page {
  min-height: 100vh;
  min-height: 100dvh;
  background: #f5f6fa;
  color: #333;
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Helvetica Neue', sans-serif;
  padding-bottom: calc(72px + env(safe-area-inset-bottom, 0px));
}

.orders-header {
  position: sticky;
  top: 0;
  z-index: 10;
  padding: calc(14px + env(safe-area-inset-top, 0px)) 20px 0;
  background: #ffffff;
  border-bottom: 1px solid #f0f0f0;
}

.header-top {
  display: flex;
  align-items: center;
  justify-content: space-between;

  h1 {
    margin: 0;
    font-size: 22px;
    font-weight: 600;
    letter-spacing: -0.02em;
    color: #1a1a1a;
  }
}

.refresh-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 50%;
  background: #f5f5f5;
  color: #666;
  cursor: pointer;
  transition: background 0.2s ease;

  &:active { transform: scale(0.9); }
  &.refreshing svg { animation: spin 0.8s linear infinite; }
}

@keyframes spin { to { transform: rotate(360deg); } }

.filter-tabs {
  display: flex;
  gap: 8px;
  margin-top: 14px;
  padding-bottom: 12px;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  &::-webkit-scrollbar { display: none; }
}

.filter-tab {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 6px 16px;
  border: 1px solid #e8e8e8;
  border-radius: 100px;
  background: #fff;
  color: #666;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.2s ease;

  &.filter-active {
    background: #1989fa;
    border-color: #1989fa;
    color: #fff;
    font-weight: 600;
  }
}

.filter-count {
  font-size: 10px;
  font-weight: 700;
  padding: 1px 5px;
  border-radius: 8px;
  background: #f0f0f0;
  .filter-active & { background: rgba(255, 255, 255, 0.3); }
}

.orders-main {
  width: min(100%, var(--fsd-mobile-max-width));
  margin: 0 auto;
  padding: 16px 16px;
}

.loading-state, .empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 24px;
  text-align: center;
}

.loading-spinner {
  width: 32px;
  height: 32px;
  border: 2.5px solid #e8e8e8;
  border-top-color: #1989fa;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin-bottom: 16px;
}

.empty-icon {
  color: #d9d9d9;
  margin-bottom: 16px;
}

.empty-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #666;
}

.empty-hint {
  margin: 6px 0 20px;
  font-size: 13px;
  color: #999;
}

.empty-cta {
  display: inline-block;
  padding: 10px 28px;
  border-radius: 100px;
  background: #1989fa;
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  text-decoration: none;
  transition: transform 0.15s ease;
  &:active { transform: scale(0.95); }
}

.orders-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.order-card {
  padding: 14px 16px;
  border-radius: 12px;
  background: #ffffff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
  cursor: pointer;
  transition: transform 0.15s ease, box-shadow 0.2s ease;

  &:active { transform: scale(0.98); }
  &:hover { box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08); }

  &.order-failed { border-left: 3px solid #ff4d4f; }
  &.order-completed { opacity: 0.85; }
}

.order-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.order-id {
  display: flex;
  align-items: center;
  gap: 8px;
}

.order-no {
  font-size: 14px;
  font-weight: 600;
  color: #1a1a1a;
}

.zone-tag {
  font-size: 9px;
  font-weight: 600;
  padding: 2px 6px;
  border-radius: 4px;
  letter-spacing: 0.03em;
}

.zone-geo { background: #e6f4ff; color: #1989fa; }
.zone-schematic { background: #f6ffed; color: #52c41a; }

.order-stage {
  font-size: 11px;
  font-weight: 600;
  padding: 3px 10px;
  border-radius: 100px;
  letter-spacing: 0.02em;

  &.stage-pending { background: #fffbe6; color: #faad14; }
  &.stage-assigned { background: #e6f4ff; color: #1989fa; }
  &.stage-transit { background: #e6fffb; color: #13c2c2; }
  &.stage-loading { background: #f9f0ff; color: #722ed1; }
  &.stage-completed { background: #f6ffed; color: #52c41a; }
  &.stage-failed { background: #fff2f0; color: #ff4d4f; }
}

.order-route {
  display: flex;
  flex-direction: column;
  gap: 0;
  margin-bottom: 12px;
}

.route-point {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 4px 0;
}

.route-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #1989fa;
  flex-shrink: 0;
  box-shadow: 0 0 0 3px rgba(25, 137, 250, 0.15);
}

.route-dot-end {
  background: #52c41a;
  box-shadow: 0 0 0 3px rgba(82, 196, 26, 0.15);
}

.route-info {
  display: flex;
  align-items: center;
  gap: 6px;
}

.route-label {
  font-size: 11px;
  font-weight: 700;
  color: #999;
}

.route-name {
  font-size: 13px;
  font-weight: 500;
  color: #333;
}

.route-connector {
  width: 2px;
  height: 16px;
  margin-left: 3px;
  background: #e8e8e8;
}

.order-card-foot {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 11px;
  color: #999;
}

.order-weight, .order-vehicle {
  padding: 2px 8px;
  border-radius: 4px;
  background: #f5f5f5;
}
</style>
