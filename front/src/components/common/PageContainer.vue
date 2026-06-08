<template>
  <div class="page-container animate-fade-in-up">
    <div class="page-header">
      <div class="page-title-area">
        <h2 class="page-title">{{ title }}</h2>
        <span v-if="subtitle" class="page-subtitle">{{ subtitle }}</span>
      </div>
      <div class="page-actions">
        <slot name="actions" />
      </div>
    </div>
    <div class="page-body">
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
withDefaults(defineProps<{
  title: string
  subtitle?: string
}>(), {
  subtitle: '',
})
</script>

<style scoped lang="less">
@mobile-break: 768px;

.page-container {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;

  @media (max-width: @mobile-break) {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }
}

.page-title-area {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.page-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--fsd-text-primary);
  letter-spacing: -0.02em;
  margin: 0;

  @media (max-width: @mobile-break) {
    font-size: 18px;
  }
}

.page-subtitle {
  font-size: 13px;
  color: var(--fsd-text-tertiary);
}

.page-actions {
  display: flex;
  align-items: center;
  gap: 8px;

  @media (max-width: @mobile-break) {
    flex-wrap: wrap;
    width: 100%;
  }
}

.page-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
</style>
