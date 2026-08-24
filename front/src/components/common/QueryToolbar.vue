<template>
  <section class="query-toolbar" role="search" :aria-label="ariaLabel">
    <div class="query-toolbar-main">
      <span v-if="showLabel && title" class="query-toolbar-label">{{ title }}</span>
      <div class="query-toolbar-fields">
        <slot />
      </div>
      <span v-if="resultSummary" class="query-toolbar-summary">{{ resultSummary }}</span>
    </div>

    <div v-if="activeChips.length" class="query-toolbar-filters" aria-label="已启用的筛选条件">
      <span class="query-toolbar-filters-label">已筛选</span>
      <button
        v-for="chip in activeChips"
        :key="chip.key"
        type="button"
        class="query-toolbar-chip"
        :aria-label="`移除筛选条件：${chip.label}`"
        @click="emit('remove', chip.key)"
      >
        {{ chip.label }}
        <span aria-hidden="true" class="query-toolbar-chip-close">×</span>
      </button>
      <a-button type="link" size="small" class="query-toolbar-clear" @click="emit('clear')">
        清除全部
      </a-button>
    </div>

    <div v-if="$slots.extra" class="query-toolbar-extra">
      <slot name="extra" />
    </div>
  </section>
</template>

<script lang="ts">
export interface FilterChip {
  key: string
  label: string
}
</script>

<script setup lang="ts">
withDefaults(
  defineProps<{
    title?: string
    resultSummary?: string
    activeChips?: FilterChip[]
    ariaLabel?: string
    showLabel?: boolean
  }>(),
  {
    title: '筛选条件',
    activeChips: () => [],
    ariaLabel: '筛选条件',
    showLabel: true,
  },
)

const emit = defineEmits<{
  remove: [key: string]
  clear: []
}>()
</script>

<style scoped lang="less">
.query-toolbar {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: var(--fsd-space-3);
  padding: 0 0 var(--fsd-space-3);
  border-bottom: 1px solid var(--fsd-border);
}

.query-toolbar-main {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: var(--fsd-space-3);
}

.query-toolbar-label {
  flex: 0 0 auto;
  color: var(--fsd-text-secondary);
  font-size: var(--fsd-text-sm);
  font-weight: var(--fsd-font-semibold);
  line-height: var(--fsd-leading-normal);
}

.query-toolbar-fields {
  display: flex;
  min-width: 0;
  flex: 1 1 auto;
  align-items: center;
  gap: var(--fsd-space-2);
  flex-wrap: wrap;
}

.query-toolbar-summary {
  flex: 0 0 auto;
  color: var(--fsd-text-tertiary);
  font-size: var(--fsd-text-xs);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.query-toolbar-filters {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: var(--fsd-space-2);
  flex-wrap: wrap;
}

.query-toolbar-filters-label {
  color: var(--fsd-text-tertiary);
  font-size: var(--fsd-text-xs);
}

.query-toolbar-chip {
  display: inline-flex;
  min-height: 28px;
  align-items: center;
  gap: 4px;
  padding: 3px var(--fsd-space-2);
  border: 1px solid var(--fsd-accent-border);
  border-radius: var(--fsd-radius-sm);
  background: var(--fsd-accent-selected);
  color: var(--fsd-accent-strong);
  cursor: pointer;
  font: inherit;
  font-size: var(--fsd-text-xs);
  line-height: var(--fsd-leading-normal);
  transition:
    border-color var(--fsd-transition-fast),
    background-color var(--fsd-transition-fast),
    color var(--fsd-transition-fast);

  &:hover {
    border-color: var(--fsd-accent);
    background: var(--fsd-accent-subtle);
  }

  &:focus-visible {
    outline: 2px solid var(--fsd-accent-strong);
    outline-offset: 2px;
  }
}

.query-toolbar-chip-close {
  font-size: 14px;
  line-height: 1;
}

.query-toolbar-clear {
  min-height: 28px;
  padding-inline: 0;
}

.query-toolbar-extra {
  display: flex;
  align-items: center;
  gap: var(--fsd-space-2);
}

@media (max-width: 767px) {
  .query-toolbar-main {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .query-toolbar-fields {
    width: 100%;
    flex-basis: 100%;
  }

  .query-toolbar-fields :deep(> *) {
    flex: 1 1 100%;
    width: 100% !important;
  }

  .query-toolbar-summary {
    margin-left: auto;
  }
}
</style>
