<template>
  <div class="demo-mode-panel" :class="{ active: demoMode }">
    <div class="demo-header">
      <span class="demo-badge" :class="{ running: demoMode }">
        <span class="demo-pulse"></span>
        {{ demoMode ? '演示中' : '演示模式' }}
      </span>
    </div>

    <div v-if="demoMode" class="demo-body">
      <div class="demo-info">
        <span class="demo-info-label">下次自动下单</span>
        <span class="demo-info-value">{{ remainingLabel }}</span>
      </div>
    </div>

    <div class="demo-actions">
      <a-button size="small" :type="demoMode ? 'default' : 'primary'" @click="demoMode ? stop() : start()">
        {{ demoMode ? '停止演示' : '开始演示' }}
      </a-button>
      <a-button size="small" :disabled="!demoMode" @click="manualNext">
        创建演示订单
      </a-button>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  demoMode: boolean
  remainingLabel: string
}>()

const emit = defineEmits<{
  start: []
  stop: []
  next: []
}>()

function start() {
  emit('start')
}

function stop() {
  emit('stop')
}

function manualNext() {
  emit('next')
}
</script>

<style scoped lang="less">
.demo-mode-panel {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 14px;
  border-radius: 10px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  background: rgba(11, 16, 24, 0.6);
  transition: border-color 0.2s, background 0.2s;

  &.active {
    border-color: rgba(45, 224, 138, 0.25);
    background: rgba(45, 224, 138, 0.06);
  }
}

.demo-header {
  display: flex;
  align-items: center;
}

.demo-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 10px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  background: rgba(255, 255, 255, 0.04);
  color: var(--fsd-text-secondary);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
  white-space: nowrap;

  &.running {
    border-color: rgba(45, 224, 138, 0.3);
    background: rgba(45, 224, 138, 0.08);
    color: var(--fsd-success);
  }
}

.demo-pulse {
  width: 6px;
  height: 6px;
  border-radius: 999px;
  background: currentColor;
}

.demo-badge.running .demo-pulse {
  animation: demo-pulse 1.6s ease-in-out infinite;
}

.demo-body {
  display: flex;
  align-items: center;
}

.demo-info {
  display: flex;
  align-items: center;
  gap: 6px;
}

.demo-info-label {
  font-size: 11px;
  color: var(--fsd-text-secondary);
}

.demo-info-value {
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
  font-weight: 600;
  color: var(--fsd-text-primary);
  font-variant-numeric: tabular-nums;
}

.demo-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-left: auto;
}

@keyframes demo-pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.45; transform: scale(0.85); }
}
</style>