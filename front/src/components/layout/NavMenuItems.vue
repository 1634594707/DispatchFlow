<template>
  <template v-for="item in items" :key="item.key">
    <a-sub-menu v-if="item.children?.length" :key="item.key" popup-class-name="nav-menu-popup">
      <template #title>
        <div class="nav-menu-entry">
          <div class="nav-menu-entry__icon">
            <NavMenuIcon :icon="item.icon" />
          </div>
          <div class="nav-menu-entry__label">{{ item.label }}</div>
        </div>
      </template>
      <NavMenuItems
        v-if="item.children.length > 0"
        :items="item.children"
        :workbench-badge-count="workbenchBadgeCount"
        :exception-badge-count="exceptionBadgeCount"
      />
    </a-sub-menu>
    <a-menu-item v-else :key="item.key">
      <div class="nav-menu-entry">
        <div class="nav-menu-entry__icon">
          <NavMenuBadgeIcon
            v-if="item.badge"
            :badge="item.badge"
            :workbench-badge-count="workbenchBadgeCount"
            :exception-badge-count="exceptionBadgeCount"
          >
            <NavMenuIcon :icon="item.icon" />
          </NavMenuBadgeIcon>
          <NavMenuIcon v-else :icon="item.icon" />
        </div>
        <div class="nav-menu-entry__label">{{ item.label }}</div>
      </div>
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

<style scoped lang="less">
.nav-menu-entry {
  display: grid;
  grid-template-columns: 20px minmax(0, 1fr);
  column-gap: 20px;
  width: 100%;
  height: 100%;
  align-items: center;
  color: inherit;
  line-height: 1;
  opacity: 1;
}

.nav-menu-entry__icon {
  display: grid;
  width: 20px;
  height: 20px;
  place-items: center;
  color: inherit;
  line-height: 0;
  opacity: 1;
}

.nav-menu-entry__label {
  min-width: 0;
  overflow: hidden;
  color: inherit;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>

