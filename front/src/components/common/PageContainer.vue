<template>
  <section class="page-container" :aria-labelledby="titleId">
    <header class="page-header">
      <div class="page-title-area">
        <h1 :id="titleId" class="page-title">{{ title }}</h1>
        <p v-if="subtitle" class="page-subtitle">{{ subtitle }}</p>
      </div>
      <div v-if="$slots.actions" class="page-actions">
        <slot name="actions" />
      </div>
    </header>
    <div class="page-body">
      <slot />
    </div>
  </section>
</template>

<script setup lang="ts">
import { useId } from 'vue'

withDefaults(
  defineProps<{
    title: string
    subtitle?: string
  }>(),
  {
    subtitle: '',
  },
)

const titleId = `page-title-${useId()}`
</script>

<style scoped lang="less">
@mobile-break: 767px;

.page-container {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: var(--fsd-space-5);
  animation: fsd-fade-in var(--fsd-duration-base) var(--fsd-ease) both;
}

.page-header {
  display: flex;
  min-width: 0;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--fsd-space-5);
}

.page-title-area {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: var(--fsd-space-1);
}

.page-title {
  margin: 0;
  color: var(--fsd-text-heading);
  font-size: var(--fsd-text-xl);
  font-weight: var(--fsd-font-semibold);
  line-height: var(--fsd-leading-tight);
  letter-spacing: var(--fsd-tracking-tight);
}

.page-subtitle {
  margin: 0;
  color: var(--fsd-text-tertiary);
  font-size: var(--fsd-text-xs);
  line-height: var(--fsd-leading-normal);
}

.page-actions {
  display: flex;
  min-width: 0;
  flex: 0 0 auto;
  align-items: center;
  justify-content: flex-end;
  gap: var(--fsd-space-2);
}

.page-body {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: var(--fsd-space-4);
}

@media (max-width: @mobile-break) {
  .page-header {
    flex-direction: column;
    gap: var(--fsd-space-3);
  }

  .page-actions {
    width: 100%;
    max-width: 100%;
    justify-content: flex-end;
    overflow-x: auto;
    overscroll-behavior-inline: contain;
    scrollbar-width: thin;
  }

  .page-actions :deep(> *) {
    flex: 0 0 auto;
  }
}
</style>
