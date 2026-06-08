<template>
  <a-badge class="nav-menu-badge-icon" :count="count" :offset="[4, -2]" :overflow-count="99">
    <span class="nav-menu-badge-icon__slot">
      <slot />
    </span>
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

<style scoped lang="less">
.nav-menu-badge-icon {
  display: inline-grid;
  width: 20px;
  height: 20px;
  place-items: center;
  line-height: 0;
  vertical-align: middle;

  :deep(.ant-badge-count) {
    min-width: 14px;
    height: 14px;
    padding: 0 4px;
    border: 0;
    border-radius: 999px;
    box-shadow: 0 0 0 1px rgba(13, 17, 23, 0.95);
    font-size: 10px;
    line-height: 14px;
  }
}

.nav-menu-badge-icon__slot {
  display: inline-grid;
  width: 20px;
  height: 20px;
  place-items: center;
  line-height: 0;
}
</style>

