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
        <button
          type="button"
          class="action-btn primary"
          @click="emit('track', currentOrder.orderId)"
        >
          查看轨迹
        </button>
        <button type="button" class="action-btn" @click="emit('order-again', currentOrder.orderId)">
          再来一单
        </button>
        <a
          v-if="shareLink"
          :href="shareLink"
          target="_blank"
          rel="noopener"
          class="action-btn link"
        >
          分享轨迹
        </a>
      </div>
    </div>
    <div v-else class="empty-current">
      <p>暂无进行中的订单</p>
      <button type="button" class="action-btn primary" @click="emit('quick-order')">
        立即下单
      </button>
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
  gap: 16px;
  padding: 16px;
  border: 1px solid var(--fsd-border);
  border-radius: var(--fsd-radius-md);
  background: var(--fsd-surface-raised);
}

.merchant-home-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  position: relative;
}

.merchant-home-title {
  margin: 0;
  font-family: var(--fsd-font-display);
  font-size: 16px;
  font-weight: 600;
  color: var(--fsd-text-heading);
  letter-spacing: -0.015em;
  display: flex;
  align-items: center;
  gap: 8px;

  &::before {
    content: '';
    width: 6px;
    height: 6px;
    border-radius: var(--fsd-radius-sm);
    background: var(--fsd-accent);
  }
}

.history-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 10px;
  border: 1px solid var(--fsd-border);
  border-radius: var(--fsd-radius-sm);
  background: var(--fsd-surface-page);
  color: var(--fsd-text-secondary);
  font-size: 11px;
  font-weight: 500;
  cursor: pointer;
  transition:
    border-color var(--fsd-transition-fast),
    color var(--fsd-transition-fast),
    background-color var(--fsd-transition-fast);

  &:hover {
    border-color: var(--fsd-border-active);
    color: var(--fsd-text-primary);
  }
}

.history-count {
  padding: 1px 6px;
  border: 1px solid var(--fsd-accent);
  border-radius: var(--fsd-radius-sm);
  background: var(--fsd-accent-selected);
  color: var(--fsd-accent-strong);
  font-family: var(--fsd-font-mono);
  font-size: 10px;
  font-weight: var(--fsd-font-semibold);
}

.current-order-card,
.empty-current {
  padding: 16px;
  border: 1px solid var(--fsd-border);
  border-radius: var(--fsd-radius-md);
  background: var(--fsd-surface-page);
  position: relative;
}

.current-order-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.current-order-label {
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.08em;
  color: var(--fsd-text-tertiary);
  text-transform: uppercase;
}

.stage-pill {
  font-size: 10px;
  font-weight: 600;
  padding: 3px 8px;
  border-radius: var(--fsd-radius-sm);
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.stage-active {
  color: var(--fsd-accent);
  background: var(--fsd-accent-bg);
  border: 1px solid var(--fsd-accent-border);
}

.stage-success {
  border: 1px solid var(--fsd-success);
  background: var(--fsd-success-bg);
  color: var(--fsd-success);
}

.stage-danger {
  border: 1px solid var(--fsd-error);
  background: var(--fsd-error-bg);
  color: var(--fsd-error);
}

.stage-default {
  border: 1px solid var(--fsd-border);
  background: var(--fsd-surface-page);
  color: var(--fsd-text-secondary);
}

.current-order-route {
  margin: 0 0 10px;
  font-family: var(--fsd-font-display);
  font-size: 16px;
  font-weight: 600;
  color: var(--fsd-text-heading);
  line-height: 1.4;
  letter-spacing: -0.015em;
}

.current-order-meta {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  font-size: 12px;
  color: var(--fsd-text-secondary);
  margin-bottom: 14px;
  font-family: var(--fsd-font-mono);
  letter-spacing: -0.01em;
}

.current-order-actions {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.action-btn {
  padding: 7px 14px;
  border: 1px solid var(--fsd-border);
  border-radius: var(--fsd-radius-sm);
  background: var(--fsd-surface-page);
  color: var(--fsd-text-secondary);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  text-decoration: none;
  transition:
    border-color var(--fsd-transition-fast),
    color var(--fsd-transition-fast),
    background-color var(--fsd-transition-fast);

  &:hover {
    border-color: var(--fsd-border-active);
    color: var(--fsd-text-primary);
    background: var(--fsd-bg-hover);
  }

  &.primary {
    border-color: var(--fsd-action-primary);
    background: var(--fsd-action-primary);
    color: var(--fsd-text-on-action);

    &:hover {
      border-color: var(--fsd-action-primary-hover);
      background: var(--fsd-action-primary-hover);
    }
  }

  &.link {
    background: transparent;
    border-color: transparent;
    color: var(--fsd-text-secondary);
    text-decoration: underline;
    text-decoration-color: var(--fsd-border-active);
    text-underline-offset: 3px;

    &:hover {
      color: var(--fsd-accent);
      text-decoration-color: var(--fsd-accent);
      background: transparent;
    }
  }
}

.empty-current {
  text-align: center;
  padding: 24px 16px;

  p {
    margin: 0 0 14px;
    color: var(--fsd-text-secondary);
    font-size: 13px;
  }
}

.section-label {
  display: block;
  font-size: 10px;
  font-weight: 600;
  color: var(--fsd-text-tertiary);
  margin-bottom: 8px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
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
  gap: 3px;
  padding: 10px 12px;
  border: 1px solid var(--fsd-border);
  border-radius: var(--fsd-radius-sm);
  background: var(--fsd-surface-page);
  cursor: pointer;
  text-align: left;
  transition:
    border-color var(--fsd-transition-fast),
    background-color var(--fsd-transition-fast);

  &:hover {
    border-color: var(--fsd-accent);
    background: var(--fsd-accent-selected);
  }
}

.route-chip-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--fsd-text-primary);
  letter-spacing: -0.01em;
}

.route-chip-fee {
  font-size: 10px;
  color: var(--fsd-warning);
  font-family: var(--fsd-font-mono);
  letter-spacing: -0.01em;
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
  padding: 12px;
  border: 1px solid var(--fsd-border);
  border-radius: var(--fsd-radius-sm);
  background: var(--fsd-surface-page);
  color: var(--fsd-text-secondary);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  text-decoration: none;
  transition:
    border-color var(--fsd-transition-fast),
    color var(--fsd-transition-fast),
    background-color var(--fsd-transition-fast);

  &:hover {
    background: var(--fsd-bg-elevated);
    color: var(--fsd-text-primary);
    border-style: solid;
    border-color: var(--fsd-border-active);
  }

  &.contact {
    border-color: var(--fsd-warning);
    background: var(--fsd-warning-bg);
    color: var(--fsd-warning);

    &:hover {
      border-color: var(--fsd-warning);
      background: var(--fsd-warning-bg);
    }
  }
}

.quick-link-icon {
  font-size: 14px;
  font-weight: 700;
  font-family: var(--fsd-font-mono);
}
</style>
