<template>
  <template v-for="item in items" :key="item.key">
    <a-sub-menu v-if="item.children?.length" :key="item.key">
      <template #icon>
        <NavMenuIcon :icon="item.icon" />
      </template>
      <template #title>{{ item.label }}</template>
      <NavMenuItems
        :items="item.children"
        :workbench-badge-count="workbenchBadgeCount"
        :exception-badge-count="exceptionBadgeCount"
      />
    </a-sub-menu>
    <a-menu-item v-else :key="item.key">
      <template #icon>
        <NavMenuBadgeIcon
          v-if="item.badge"
          :badge="item.badge"
          :workbench-badge-count="workbenchBadgeCount"
          :exception-badge-count="exceptionBadgeCount"
        >
          <NavMenuIcon :icon="item.icon" />
        </NavMenuBadgeIcon>
        <NavMenuIcon v-else :icon="item.icon" />
      </template>
      <span>{{ item.label }}</span>
    </a-menu-item>
  </template>
</template>

<script setup lang="ts">
import type { NavItem } from '@/config/navigation'
import NavMenuItems from './NavMenuItems.vue'
import NavMenuIcon from './NavMenuIcon.vue'
import NavMenuBadgeIcon from './NavMenuBadgeIcon.vue'

defineProps<{
  items: NavItem[]
  workbenchBadgeCount: number
  exceptionBadgeCount: number
}>()
</script>
