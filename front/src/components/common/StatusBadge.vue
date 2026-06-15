<template>
  <span class="fsd-status" :class="[`fsd-status--${semantic}`, sizeClass]">
    <span v-if="showDot" class="fsd-status-dot"></span>
    <span class="fsd-status-label">{{ label }}</span>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import {
  orderStatusMap,
  taskStatusMap,
  onlineStatusMap,
  dispatchStatusMap,
  exceptionStatusMap,
  slotStatusMap,
  infraActiveMap,
  ticketStatusMap,
  alertStatusMap,
  healthStatusMap,
  configCheckMap,
  userStatusMap,
  executionStatusMap,
  vehicleHealthMap,
  statusColorToSemantic,
} from '@/constants/statusMap'

const props = withDefaults(defineProps<{
  status: string
  type?: 'order' | 'task' | 'online' | 'dispatch' | 'exception'
    | 'slot' | 'infra' | 'ticket' | 'alert' | 'health' | 'config'
    | 'user' | 'execution' | 'vehicleHealth'
  size?: 'sm' | 'md'
  showDot?: boolean
}>(), {
  type: 'order',
  size: 'md',
  showDot: true,
})

const statusMaps: Record<string, Record<string, { label: string; color: string }>> = {
  order:         orderStatusMap,
  task:          taskStatusMap,
  online:        onlineStatusMap,
  dispatch:      dispatchStatusMap,
  exception:     exceptionStatusMap,
  slot:          slotStatusMap,
  infra:         infraActiveMap,
  ticket:        ticketStatusMap,
  alert:         alertStatusMap,
  health:        healthStatusMap,
  config:        configCheckMap,
  user:          userStatusMap,
  execution:     executionStatusMap,
  vehicleHealth: vehicleHealthMap,
}

const config = computed(() => {
  const map = statusMaps[props.type]
  return map?.[props.status] || { label: props.status, color: 'default' }
})

const label = computed(() => config.value.label)

const semantic = computed(() => statusColorToSemantic(config.value.color))

const sizeClass = computed(() => `fsd-status--${props.size}`)
</script>

<style scoped lang="less">
.fsd-status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 500;
  white-space: nowrap;
}

.fsd-status--sm {
  font-size: 11px;
}

.fsd-status--md {
  font-size: 12px;
}

.fsd-status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;
}

.fsd-status--critical .fsd-status-dot {
  background: var(--fsd-risk-critical);
  box-shadow: 0 0 6px rgba(255, 61, 113, 0.45);
}

.fsd-status--critical .fsd-status-label {
  color: var(--fsd-risk-critical);
}

.fsd-status--warning .fsd-status-dot {
  background: var(--fsd-risk-warning);
}

.fsd-status--warning .fsd-status-label {
  color: var(--fsd-risk-warning);
}

.fsd-status--active .fsd-status-dot {
  background: var(--fsd-risk-active);
}

.fsd-status--active .fsd-status-label {
  color: var(--fsd-risk-active);
}

.fsd-status--normal .fsd-status-dot {
  background: var(--fsd-risk-normal);
}

.fsd-status--normal .fsd-status-label {
  color: var(--fsd-risk-normal);
}

.fsd-status--muted .fsd-status-dot {
  background: var(--fsd-risk-muted);
}

.fsd-status--muted .fsd-status-label {
  color: var(--fsd-text-tertiary);
}
</style>
