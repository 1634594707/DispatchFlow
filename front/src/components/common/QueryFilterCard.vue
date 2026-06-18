<template>
  <div class="query-filter-card">
    <div class="query-filter-head">
      <span class="query-filter-title">{{ title }}</span>
      <span v-if="resultSummary" class="query-result-summary">{{ resultSummary }}</span>
    </div>
    <div class="query-filter-body">
      <slot />
    </div>
    <div v-if="activeChips.length > 0" class="query-filter-chips">
      <span class="chips-label">已筛选</span>
      <button
        v-for="chip in activeChips"
        :key="chip.key"
        type="button"
        class="filter-chip"
        @click="emit('remove', chip.key)"
      >
        {{ chip.label }}
        <span class="chip-close">&times;</span>
      </button>
      <a-button type="link" size="small" class="clear-all-btn" @click="emit('clear')">清除全部</a-button>
    </div>
    <div v-if="$slots.extra" class="query-filter-extra">
      <slot name="extra" />
    </div>
  </div>
</template>

<script setup lang="ts">
export interface FilterChip {
  key: string
  label: string
}

withDefaults(
  defineProps<{
    title?: string
    resultSummary?: string
    activeChips?: FilterChip[]
  }>(),
  {
    title: '筛选条件',
    activeChips: () => [],
  },
)

const emit = defineEmits<{
  remove: [key: string]
  clear: []
}>()
</script>

<style scoped lang="less">
.query-filter-card {
  padding: 14px 16px;
  border-radius: var(--fsd-radius-lg, 12px);
  border: 1px solid var(--fsd-border);
  background: var(--fsd-bg-base);
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.query-filter-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.query-filter-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--fsd-text-secondary);
}

.query-result-summary {
  font-size: 12px;
  color: var(--fsd-text-tertiary);
}

.query-filter-body {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.query-filter-chips {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  padding-top: 4px;
  border-top: 1px dashed var(--fsd-border);
}

.chips-label {
  font-size: 11px;
  color: var(--fsd-text-tertiary);
}

.filter-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 500;
  border: 1px solid rgba(34, 199, 230, 0.25);
  background: rgba(34, 199, 230, 0.08);
  color: var(--fsd-accent);
  cursor: pointer;
  transition: all 0.15s;

  &:hover {
    border-color: var(--fsd-error);
    color: var(--fsd-error);
    background: rgba(255, 92, 124, 0.08);
  }
}

.chip-close {
  font-size: 13px;
  line-height: 1;
}

.clear-all-btn {
  padding: 0 4px;
  height: auto;
}

.query-filter-extra {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
</style>
