<template>
  <div class="empty-state">
    <a-empty :description="description">
      <template v-if="$slots.default" #default>
        <slot />
      </template>
    </a-empty>
  </div>
</template>

<script setup lang="ts">
withDefaults(defineProps<{
  description?: string
}>(), {
  description: '暂无数据',
})
</script>

<style scoped lang="less">
.empty-state {
  padding: 48px 0;
  display: flex;
  justify-content: center;
}
</style>
