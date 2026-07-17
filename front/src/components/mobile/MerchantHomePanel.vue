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
  padding: 18px;
  border-radius: var(--fsd-radius-xl);
  border: 1px solid var(--fsd-border);
  background:
    radial-gradient(circle at 0% 0%, var(--fsd-accent-subtle), transparent 50%),
    var(--fsd-bg-base);
  position: relative;
  overflow: hidden;

  &::before {
    content: '';
    position: absolute;
    top: -40px;
    right: -40px;
    width: 160px;
    height: 160px;
    border-radius: 50%;
    background: radial-gradient(circle, var(--fsd-accent-glow), transparent 60%);
    filter: blur(40px);
    pointer-events: none;
    opacity: 0.5;
  }
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
    border-radius: var(--fsd-radius-full);
    background: var(--fsd-accent);
    box-shadow: 0 0 8px var(--fsd-accent-muted);
  }
}

.history-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 10px;
  border-radius: var(--fsd-radius-full);
  border: 1px solid var(--fsd-border);
  background: var(--fsd-bg-elevated);
  color: var(--fsd-text-secondary);
  font-size: 11px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--fsd-transition-fast);

  &:hover {
    border-color: var(--fsd-border-active);
    color: var(--fsd-text-primary);
  }
}

.history-count {
  padding: 1px 6px;
  border-radius: var(--fsd-radius-full);
  background: var(--fsd-accent-bg);
  color: var(--fsd-accent);
  font-size: 10px;
  font-weight: 600;
  font-family: var(--fsd-font-mono);
  border: 1px solid var(--fsd-accent-border);
}

.current-order-card,
.empty-current {
  padding: 16px;
  border-radius: var(--fsd-radius-lg);
  border: 1px solid var(--fsd-border);
  background: var(--fsd-bg-elevated);
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
  border-radius: var(--fsd-radius-full);
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.stage-active {
  color: var(--fsd-accent);
  background: var(--fsd-accent-bg);
  border: 1px solid var(--fsd-accent-border);
}

.stage-success {
  color: var(--fsd-success);
  background: rgba(52, 211, 153, 0.10);
  border: 1px solid rgba(52, 211, 153, 0.22);
}

.stage-danger {
  color: var(--fsd-error);
  background: rgba(248, 113, 113, 0.10);
  border: 1px solid rgba(248, 113, 113, 0.22);
}

.stage-default {
  color: var(--fsd-text-secondary);
  background: rgba(148, 163, 184, 0.10);
  border: 1px solid var(--fsd-border-active);
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
  border-radius: var(--fsd-radius-sm);
  border: 1px solid var(--fsd-border);
  background: var(--fsd-bg-base);
  color: var(--fsd-text-secondary);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  text-decoration: none;
  transition: all var(--fsd-transition-fast);

  &:hover {
    border-color: var(--fsd-border-active);
    color: var(--fsd-text-primary);
    background: var(--fsd-bg-hover);
  }

  &.primary {
    border-color: var(--fsd-accent-border);
    background: var(--fsd-accent-bg);
    color: var(--fsd-accent);

    &:hover {
      background: rgba(34, 211, 238, 0.14);
      border-color: var(--fsd-accent);
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
  border-radius: var(--fsd-radius-md);
  border: 1px solid var(--fsd-border);
  background: var(--fsd-bg-base);
  cursor: pointer;
  text-align: left;
  transition: all var(--fsd-transition-fast);

  &:hover {
    border-color: var(--fsd-accent-border);
    background: var(--fsd-bg-elevated);
    transform: translateY(-1px);
  }

  &:active {
    transform: translateY(0);
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
  border-radius: var(--fsd-radius-md);
  border: 1px dashed var(--fsd-border-active);
  background: transparent;
  color: var(--fsd-text-secondary);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  text-decoration: none;
  transition: all var(--fsd-transition-fast);

  &:hover {
    background: var(--fsd-bg-elevated);
    color: var(--fsd-text-primary);
    border-style: solid;
    border-color: var(--fsd-border-active);
  }

  &.contact {
    border-color: rgba(251, 191, 36, 0.28);
    color: var(--fsd-warning);

    &:hover {
      background: rgba(251, 191, 36, 0.06);
      border-color: rgba(251, 191, 36, 0.40);
    }
  }
}

.quick-link-icon {
  font-size: 14px;
  font-weight: 700;
  font-family: var(--fsd-font-mono);
}
</style>
