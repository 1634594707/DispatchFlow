<template>
  <section class="merchant-home">
    <div class="merchant-home-head">
      <h2 class="merchant-home-title">商户下单</h2>
      <button type="button" class="history-btn" @click="emit('open-history')">
        订单历史
        <span v-if="historyCount > 0" class="history-count">{{ historyCount }}</span>
      </button>
    </div>

    <div v-if="currentOrder" class="current-order-card">
      <div class="current-order-head">
        <span class="current-order-label">当前订单</span>
        <span class="stage-pill" :class="stageClass(currentOrder.runtimeStage)">
          {{ stageLabel(currentOrder.runtimeStage) }}
        </span>
      </div>
      <p class="current-order-route">
        {{ currentOrder.pickupStation.stationName || currentOrder.pickupStation.stationCode }}
        →
        {{ currentOrder.dropoffStation.stationName || currentOrder.dropoffStation.stationCode }}
      </p>
      <div class="current-order-meta">
        <span v-if="feeEstimate">{{ feeEstimate }}</span>
        <span v-if="remainingLabel">{{ remainingLabel }}</span>
      </div>
      <div class="current-order-actions">
        <button type="button" class="action-btn primary" @click="emit('track', currentOrder.orderId)">
          查看轨迹
        </button>
        <button type="button" class="action-btn" @click="emit('order-again', currentOrder.orderId)">
          再来一单
        </button>
        <a v-if="shareLink" :href="shareLink" target="_blank" rel="noopener" class="action-btn link">
          分享轨迹
        </a>
      </div>
    </div>
    <div v-else class="empty-current">
      <p>暂无进行中的订单</p>
      <button type="button" class="action-btn primary" @click="emit('quick-order')">立即下单</button>
    </div>

    <div v-if="favoriteRoutes.length > 0" class="favorite-routes">
      <span class="section-label">常用线路</span>
      <div class="route-chips">
        <button
          v-for="route in favoriteRoutes"
          :key="route.key"
          type="button"
          class="route-chip"
          @click="emit('select-route', route)"
        >
          <span class="route-chip-name">{{ route.label }}</span>
          <span v-if="route.feeHint" class="route-chip-fee">{{ route.feeHint }}</span>
        </button>
      </div>
    </div>

    <div class="merchant-quick-links">
      <button type="button" class="quick-link" @click="emit('quick-order')">
        <span class="quick-link-icon">+</span>
        快速下单
      </button>
      <a href="tel:400-000-0000" class="quick-link contact">
        <span class="quick-link-icon">!</span>
        异常联系
      </a>
    </div>
  </section>
</template>

<script setup lang="ts">
import type { ParkOrderSnapshot } from '@/types/park'

export interface FavoriteRoute {
  key: string
  label: string
  pickupStationId: number
  dropoffStationId: number
  feeHint?: string
}

defineProps<{
  currentOrder: ParkOrderSnapshot | null
  favoriteRoutes: FavoriteRoute[]
  historyCount: number
  feeEstimate?: string | null
  remainingLabel?: string | null
  shareLink?: string | null
}>()

const emit = defineEmits<{
  track: [orderId: number]
  'order-again': [orderId: number]
  'select-route': [route: FavoriteRoute]
  'open-history': []
  'quick-order': []
}>()

function stageLabel(stage: string) {
  const map: Record<string, string> = {
    CREATED: '已创建',
    DISPATCHING: '派单中',
    ASSIGNED: '已派车',
    PICKUP: '取货中',
    DELIVERING: '配送中',
    COMPLETED: '已完成',
    FAILED: '失败',
  }
  return map[stage] || stage
}

function stageClass(stage: string) {
  if (['FAILED'].includes(stage)) return 'stage-danger'
  if (['COMPLETED'].includes(stage)) return 'stage-success'
  if (['DISPATCHING', 'ASSIGNED', 'PICKUP', 'DELIVERING'].includes(stage)) return 'stage-active'
  return 'stage-default'
}
</script>

<style scoped lang="less">
.merchant-home {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 16px;
  border-radius: 16px;
  border: 1px solid rgba(34, 199, 230, 0.2);
  background: linear-gradient(160deg, rgba(34, 199, 230, 0.08) 0%, rgba(11, 16, 24, 0.6) 100%);
}

.merchant-home-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.merchant-home-title {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: var(--fsd-text-primary);
}

.history-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  background: rgba(255, 255, 255, 0.04);
  color: var(--fsd-text-secondary);
  font-size: 12px;
  cursor: pointer;
}

.history-count {
  padding: 0 6px;
  border-radius: 999px;
  background: rgba(34, 199, 230, 0.2);
  color: var(--fsd-accent);
  font-size: 11px;
}

.current-order-card,
.empty-current {
  padding: 14px;
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  background: rgba(6, 9, 15, 0.5);
}

.current-order-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.current-order-label {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.06em;
  color: var(--fsd-text-secondary);
  text-transform: uppercase;
}

.stage-pill {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 999px;
}

.stage-active {
  color: var(--fsd-accent);
  background: rgba(34, 199, 230, 0.15);
}

.stage-success {
  color: var(--fsd-success);
  background: rgba(45, 224, 138, 0.12);
}

.stage-danger {
  color: var(--fsd-error);
  background: rgba(255, 92, 124, 0.12);
}

.stage-default {
  color: var(--fsd-text-secondary);
  background: rgba(139, 148, 158, 0.12);
}

.current-order-route {
  margin: 0 0 8px;
  font-size: 15px;
  font-weight: 600;
  color: var(--fsd-text-primary);
  line-height: 1.4;
}

.current-order-meta {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  font-size: 12px;
  color: var(--fsd-text-secondary);
  margin-bottom: 12px;
}

.current-order-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.action-btn {
  padding: 6px 14px;
  border-radius: 8px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  background: rgba(255, 255, 255, 0.04);
  color: var(--fsd-text-secondary);
  font-size: 13px;
  cursor: pointer;
  text-decoration: none;

  &.primary {
    border-color: rgba(34, 199, 230, 0.4);
    background: rgba(34, 199, 230, 0.15);
    color: var(--fsd-accent);
  }
}

.empty-current {
  text-align: center;

  p {
    margin: 0 0 12px;
    color: var(--fsd-text-secondary);
    font-size: 14px;
  }
}

.section-label {
  display: block;
  font-size: 11px;
  font-weight: 600;
  color: var(--fsd-text-tertiary);
  margin-bottom: 8px;
  letter-spacing: 0.04em;
}

.route-chips {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.route-chip {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  padding: 8px 12px;
  border-radius: 10px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  background: rgba(11, 16, 24, 0.6);
  cursor: pointer;
  text-align: left;
}

.route-chip-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--fsd-text-primary);
}

.route-chip-fee {
  font-size: 11px;
  color: var(--fsd-warning);
}

.merchant-quick-links {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.quick-link {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 10px;
  border-radius: 10px;
  border: 1px dashed rgba(255, 255, 255, 0.12);
  background: transparent;
  color: var(--fsd-text-secondary);
  font-size: 13px;
  cursor: pointer;
  text-decoration: none;

  &.contact {
    border-color: rgba(255, 176, 32, 0.25);
    color: var(--fsd-warning);
  }
}

.quick-link-icon {
  font-size: 16px;
  font-weight: 700;
}
</style>
