<template>
  <a-badge :count="count" :offset="[6, 0]" :overflow-count="99">
    <slot />
  </a-badge>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { NavBadge } from '@/config/navigation'

const props = defineProps<{
  badge: NavBadge
  workbenchBadgeCount: number
  exceptionBadgeCount: number
}>()

const count = computed(() => {
  if (props.badge === 'workbench') return props.workbenchBadgeCount
  if (props.badge === 'exceptions') return props.exceptionBadgeCount
  return 0
})
</script>
