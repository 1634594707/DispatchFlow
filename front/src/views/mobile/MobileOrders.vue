<template>
  <div class="mobile-orders-page">
    <header class="orders-header">
      <div class="header-top">
        <h1>我的订单</h1>
        <button
          type="button"
          class="refresh-btn"
          :class="{ refreshing: loading }"
          aria-label="刷新订单"
          :aria-busy="loading"
          @click="refresh"
        >
          <svg
            viewBox="0 0 24 24"
            width="18"
            height="18"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
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
          type="button"
          :aria-pressed="activeFilter === f.value"
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
          <svg
            viewBox="0 0 24 24"
            width="48"
            height="48"
            fill="none"
            stroke="currentColor"
            stroke-width="1.5"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <path
              d="M9 4h6a1 1 0 0 1 1 1v1h3a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h3V5a1 1 0 0 1 1-1z"
            />
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
              <span v-if="order.deliveryZone === 'GEO_DELIVERY'" class="zone-tag zone-geo"
                >地理配送</span
              >
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
                <span class="route-name">{{
                  order.pickupStation.stationName || order.pickupStation.stationCode
                }}</span>
              </div>
            </div>
            <div class="route-connector" />
            <div class="route-point route-dropoff">
              <span class="route-dot route-dot-end" />
              <div class="route-info">
                <span class="route-label">送</span>
                <span class="route-name">{{
                  order.dropoffStation.stationName || order.dropoffStation.stationCode
                }}</span>
              </div>
            </div>
          </div>

          <div class="order-card-foot">
            <span class="order-time">{{
              formatTime(order.startTime || order.assignTime || order.updatedAt)
            }}</span>
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
import { filterGeoDeliveryOrders, filterSchematicOrders } from '@/maps'
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
  visibleOrders.value.filter((o) => !['COMPLETED', 'FAILED'].includes(o.runtimeStage)),
)

const completedOrders = computed(() =>
  visibleOrders.value.filter((o) => o.runtimeStage === 'COMPLETED'),
)

const failedOrders = computed(() => visibleOrders.value.filter((o) => o.runtimeStage === 'FAILED'))

const activeCount = computed(() => activeOrders.value.length)

const filters = computed(() => [
  { label: '全部', value: 'all' as const, count: visibleOrders.value.length },
  { label: '进行中', value: 'active' as const, count: activeOrders.value.length },
  { label: '已完成', value: 'completed' as const, count: completedOrders.value.length },
  { label: '异常', value: 'failed' as const, count: failedOrders.value.length },
])

const filteredOrders = computed(() => {
  switch (activeFilter.value) {
    case 'active':
      return activeOrders.value
    case 'completed':
      return completedOrders.value
    case 'failed':
      return failedOrders.value
    default:
      return visibleOrders.value
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
  return d.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
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

.orders-header {
  position: sticky;
  top: 0;
  z-index: var(--fsd-z-sticky);
  padding: calc(var(--fsd-space-3) + env(safe-area-inset-top, 0px)) var(--fsd-space-4) 0;
  background: var(--mobile-surface);
  border-bottom: 1px solid var(--mobile-border);
}

.header-top {
  display: flex;
  align-items: center;
  justify-content: space-between;

  h1 {
    margin: 0;
    color: var(--mobile-text);
    font-size: var(--fsd-text-xl);
    font-weight: var(--fsd-font-semibold);
    letter-spacing: var(--fsd-tracking-tight);
  }
}

.refresh-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: var(--fsd-touch-target-min);
  height: var(--fsd-touch-target-min);
  border: 1px solid var(--mobile-border);
  border-radius: var(--fsd-radius-sm);
  background: var(--mobile-surface);
  color: var(--mobile-secondary);
  cursor: pointer;
  transition:
    background-color var(--fsd-transition-base),
    border-color var(--fsd-transition-base),
    color var(--fsd-transition-base);

  &:hover,
  &:focus-visible {
    border-color: var(--fsd-border-active);
    background: var(--fsd-bg-hover);
    color: var(--fsd-accent-strong);
  }

  &:focus-visible {
    outline: 2px solid var(--fsd-accent-strong);
    outline-offset: 2px;
  }

  &.refreshing {
    opacity: 0.56;
  }
}

.filter-tabs {
  display: flex;
  gap: var(--fsd-space-2);
  margin-top: var(--fsd-space-2);
  padding-bottom: var(--fsd-space-3);
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  &::-webkit-scrollbar {
    display: none;
  }
}

.filter-tab {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--fsd-space-1);
  min-height: var(--fsd-touch-target-min);
  padding: 6px 12px;
  border: 1px solid var(--mobile-border);
  border-radius: var(--fsd-radius-sm);
  background: var(--mobile-surface);
  color: var(--mobile-secondary);
  font-size: var(--fsd-text-sm);
  font-weight: var(--fsd-font-medium);
  cursor: pointer;
  white-space: nowrap;
  transition:
    background-color var(--fsd-transition-base),
    border-color var(--fsd-transition-base),
    color var(--fsd-transition-base);

  &.filter-active {
    border-color: var(--fsd-accent-border);
    background: var(--fsd-accent-selected);
    color: var(--fsd-accent-strong);
    font-weight: var(--fsd-font-semibold);
  }
}

.filter-count {
  padding: 1px 5px;
  border-radius: var(--fsd-radius-sm);
  background: #edf0f2;
  font-size: 10px;
  font-weight: var(--fsd-font-semibold);

  .filter-active & {
    background: var(--fsd-action-primary);
    color: var(--fsd-text-on-action);
  }
}

.orders-main {
  width: min(100%, var(--fsd-mobile-max-width));
  margin: 0 auto;
  padding: var(--fsd-space-4);
}

.loading-state,
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px var(--fsd-space-4);
  text-align: center;
}

.loading-spinner {
  width: 32px;
  height: 32px;
  margin-bottom: var(--fsd-space-3);
  border: 2px solid var(--mobile-border);
  border-top-color: var(--fsd-accent);
  border-radius: var(--fsd-radius-full);
}

.empty-icon {
  margin-bottom: var(--fsd-space-3);
  color: var(--mobile-tertiary);
}

.empty-title {
  margin: 0;
  color: var(--mobile-text);
  font-size: var(--fsd-text-md);
  font-weight: var(--fsd-font-semibold);
}

.empty-hint {
  margin: var(--fsd-space-1) 0 var(--fsd-space-4);
  color: var(--mobile-secondary);
  font-size: var(--fsd-text-sm);
}

.empty-cta {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: var(--fsd-touch-target-min);
  padding: 9px var(--fsd-space-4);
  border-radius: var(--fsd-radius-sm);
  background: var(--fsd-action-primary);
  color: var(--fsd-text-on-action);
  font-size: var(--fsd-text-sm);
  font-weight: var(--fsd-font-semibold);
  text-decoration: none;
}

.orders-list {
  display: flex;
  flex-direction: column;
  border-top: 1px solid var(--mobile-border);
}

.order-card {
  min-height: var(--fsd-touch-target-min);
  padding: var(--fsd-space-3) 0;
  border: 0;
  border-bottom: 1px solid var(--mobile-border);
  background: transparent;
  cursor: pointer;
  transition: background-color var(--fsd-transition-base);

  &:hover,
  &:active {
    background: var(--fsd-bg-hover);
  }

  &.order-failed {
    padding-left: var(--fsd-space-2);
    border-left: 3px solid var(--fsd-error);
  }
}

.order-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--fsd-space-2);
  margin-bottom: var(--fsd-space-2);
}

.order-id {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: var(--fsd-space-2);
}

.order-no {
  overflow: hidden;
  color: var(--mobile-text);
  font-family: var(--fsd-font-mono);
  font-size: var(--fsd-text-sm);
  font-weight: var(--fsd-font-semibold);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.zone-tag {
  flex: 0 0 auto;
  padding: 2px 6px;
  border-radius: var(--fsd-radius-sm);
  font-size: 10px;
  font-weight: var(--fsd-font-medium);
  letter-spacing: 0.03em;
}

.zone-geo {
  background: var(--fsd-neutral-bg);
  color: var(--mobile-secondary);
}
.zone-schematic {
  background: var(--fsd-neutral-bg);
  color: var(--mobile-secondary);
}

.order-stage {
  flex: 0 0 auto;
  padding: 3px 8px;
  border-radius: var(--fsd-radius-sm);
  font-size: 11px;
  font-weight: var(--fsd-font-semibold);
  letter-spacing: 0.02em;

  &.stage-pending {
    background: var(--fsd-warning-bg);
    color: var(--fsd-warning);
  }
  &.stage-assigned,
  &.stage-transit {
    background: var(--fsd-accent-selected);
    color: var(--fsd-accent-strong);
  }
  &.stage-loading {
    background: var(--fsd-neutral-bg);
    color: var(--mobile-secondary);
  }
  &.stage-completed {
    background: var(--fsd-success-bg);
    color: var(--fsd-success);
  }
  &.stage-failed {
    background: var(--fsd-error-bg);
    color: var(--fsd-error);
  }
}

.order-route {
  display: flex;
  flex-direction: column;
  gap: 0;
  margin-bottom: var(--fsd-space-2);
}

.route-point {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: var(--fsd-space-2);
  padding: 4px 0;
}

.route-dot {
  width: 8px;
  height: 8px;
  flex-shrink: 0;
  border-radius: var(--fsd-radius-full);
  background: var(--mobile-tertiary);
}

.order-transit .route-pickup .route-dot,
.order-loading .route-pickup .route-dot {
  background: var(--fsd-accent);
}

.order-failed .route-dot-end {
  background: var(--fsd-error);
}

.route-info {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: var(--fsd-space-1);
}

.route-label {
  flex: 0 0 auto;
  color: var(--mobile-tertiary);
  font-size: 11px;
  font-weight: var(--fsd-font-semibold);
}

.route-name {
  overflow: hidden;
  color: var(--mobile-text);
  font-size: var(--fsd-text-sm);
  font-weight: var(--fsd-font-medium);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.route-connector {
  width: 1px;
  height: 16px;
  margin-left: 3px;
  background: var(--mobile-border);
}

.order-card-foot {
  display: flex;
  align-items: center;
  gap: var(--fsd-space-3);
  color: var(--mobile-tertiary);
  font-family: var(--fsd-font-mono);
  font-size: 11px;
}

.order-weight,
.order-vehicle {
  padding: 2px 6px;
  border-radius: var(--fsd-radius-sm);
  background: #edf0f2;
}
</style>
