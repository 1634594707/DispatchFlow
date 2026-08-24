<template>
  <QueryToolbar
    :title="title"
    :result-summary="resultSummary"
    :active-chips="activeChips"
    @remove="emit('remove', $event)"
    @clear="emit('clear')"
  >
    <slot />
    <template #extra>
      <slot name="extra" />
    </template>
  </QueryToolbar>
</template>

<script lang="ts">
export type { FilterChip } from './QueryToolbar.vue'
</script>

<script setup lang="ts">
import QueryToolbar from './QueryToolbar.vue'
import type { FilterChip } from './QueryToolbar.vue'

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
