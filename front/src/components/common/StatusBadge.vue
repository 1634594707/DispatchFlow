<template>
  <a-badge :status="badgeStatus" :text="label" />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { orderStatusMap, taskStatusMap, onlineStatusMap, dispatchStatusMap, exceptionStatusMap } from '@/constants/statusMap'

const props = defineProps<{
  status: string
  type?: 'order' | 'task' | 'online' | 'dispatch' | 'exception'
}>()

const statusMaps: Record<string, Record<string, { label: string; color: string }>> = {
  order: orderStatusMap,
  task: taskStatusMap,
  online: onlineStatusMap,
  dispatch: dispatchStatusMap,
  exception: exceptionStatusMap,
}

const config = computed(() => {
  const map = statusMaps[props.type || 'order']
  return map?.[props.status] || { label: props.status, color: 'default' }
})

const label = computed(() => config.value.label)

const badgeStatus = computed(() => {
  const colorMap: Record<string, string> = {
    success: 'success',
    error: 'error',
    warning: 'warning',
    processing: 'processing',
    default: 'default',
    cyan: 'processing',
  }
  return (colorMap[config.value.color] || 'default') as any
})
</script>
