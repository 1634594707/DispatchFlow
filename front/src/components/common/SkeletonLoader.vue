<template>
  <div v-if="preset === 'workbench'" class="skeleton-workbench">
    <div class="skeleton-panel skeleton-panel-tasks">
      <div class="skeleton-head"></div>
      <div class="skeleton-line"></div>
      <div class="skeleton-line short"></div>
      <div class="skeleton-card"></div>
      <div class="skeleton-card"></div>
      <div class="skeleton-card short"></div>
    </div>
    <div class="skeleton-panel skeleton-panel-map">
      <div class="skeleton-head"></div>
      <div class="skeleton-block map-block"></div>
    </div>
    <div class="skeleton-panel skeleton-panel-exc">
      <div class="skeleton-head"></div>
      <div class="skeleton-card"></div>
      <div class="skeleton-card short"></div>
    </div>
  </div>

  <div v-else-if="preset === 'tracking'" class="skeleton-tracking">
    <div class="skeleton-tracking-panel">
      <div class="skeleton-head"></div>
      <div class="skeleton-line"></div>
      <div class="skeleton-line short"></div>
      <div class="skeleton-card"></div>
      <div class="skeleton-card"></div>
      <div class="skeleton-card short"></div>
      <div class="skeleton-card"></div>
    </div>
    <div class="skeleton-tracking-map">
      <div class="skeleton-block map-block"></div>
    </div>
  </div>

  <div v-else class="skeleton-default">
    <div class="skeleton-head"></div>
    <div class="skeleton-line"></div>
    <div class="skeleton-line short"></div>
  </div>
</template>

<script setup lang="ts">
withDefaults(defineProps<{
  preset?: 'workbench' | 'tracking' | 'default'
}>(), {
  preset: 'default',
})
</script>

<style scoped lang="less">
@keyframes skeleton-shimmer {
  0% { background-position: -400px 0; }
  100% { background-position: calc(400px + 100%) 0; }
}

.skeleton-shimmer {
  background: linear-gradient(
    90deg,
    rgba(255, 255, 255, 0.04) 25%,
    rgba(255, 255, 255, 0.08) 50%,
    rgba(255, 255, 255, 0.04) 75%
  );
  background-size: 400px 100%;
  animation: skeleton-shimmer 1.6s ease-in-out infinite;
  border-radius: 6px;
}

.skeleton-head {
  .skeleton-shimmer();
  height: 20px;
  width: 120px;
  margin-bottom: 16px;
}

.skeleton-line {
  .skeleton-shimmer();
  height: 12px;
  width: 100%;
  margin-bottom: 10px;

  &.short {
    width: 60%;
  }
}

.skeleton-card {
  .skeleton-shimmer();
  height: 56px;
  width: 100%;
  margin-bottom: 8px;

  &.short {
    width: 75%;
  }
}

.skeleton-block {
  .skeleton-shimmer();
  height: 100%;
  width: 100%;

  &.map-block {
    min-height: 200px;
  }
}

/* workbench layout */
.skeleton-workbench {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 16px;
  padding: 24px;

  .skeleton-panel {
    background: rgba(22, 27, 34, 0.5);
    border: 1px solid rgba(255, 255, 255, 0.06);
    border-radius: 12px;
    padding: 20px;
  }

  .skeleton-panel-map {
    .map-block {
      min-height: 360px;
    }
  }
}

/* tracking layout */
.skeleton-tracking {
  display: flex;
  height: 100vh;
  position: relative;

  .skeleton-tracking-panel {
    width: 320px;
    flex-shrink: 0;
    background: rgba(13, 17, 23, 0.95);
    padding: 20px;
    overflow: hidden;
  }

  .skeleton-tracking-map {
    flex: 1;
    padding: 48px 24px 24px;
    background: rgba(13, 17, 23, 0.5);

    .map-block {
      min-height: calc(100vh - 72px);
      border-radius: 12px;
    }
  }
}

.skeleton-default {
  padding: 24px;
  background: rgba(22, 27, 34, 0.5);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 12px;
}
</style>