<template>
  <div class="skl-root" :class="`skl--${variant}`" role="status" aria-label="加载中">
    <!-- Card skeleton -->
    <template v-if="variant === 'card'">
      <div class="skl-card">
        <div class="skl-card-header">
          <div class="skl-line skl-line--short" />
          <div class="skl-line skl-line--xs" />
        </div>
        <div class="skl-card-body">
          <div class="skl-block skl-block--lg" />
          <div class="skl-line skl-line--md" />
          <div class="skl-line skl-line--full" />
        </div>
      </div>
    </template>

    <!-- Table skeleton -->
    <template v-if="variant === 'table'">
      <div class="skl-table">
        <div class="skl-table-header">
          <div v-for="col in columns" :key="col" class="skl-line skl-line--xs" :style="{ flex: col }" />
        </div>
        <div v-for="row in rows" :key="row" class="skl-table-row">
          <div v-for="col in columns" :key="col" class="skl-line" :style="{ flex: col }" />
        </div>
      </div>
    </template>

    <!-- Chart skeleton -->
    <template v-if="variant === 'chart'">
      <div class="skl-chart">
        <div class="skl-chart-header">
          <div class="skl-line skl-line--md" />
        </div>
        <div class="skl-chart-area">
          <div class="skl-chart-bar" v-for="i in 7" :key="i" :style="{ height: `${30 + Math.random() * 60}%` }" />
        </div>
      </div>
    </template>

    <!-- Stat cards row -->
    <template v-if="variant === 'stats'">
      <div class="skl-stats" :style="{ gridTemplateColumns: `repeat(${count}, 1fr)` }">
        <div v-for="i in count" :key="i" class="skl-stat-card">
          <div class="skl-line skl-line--xs" />
          <div class="skl-block skl-block--sm" />
        </div>
      </div>
    </template>

    <!-- Paragraph text -->
    <template v-if="variant === 'text'">
      <div class="skl-text">
        <div v-for="i in lines" :key="i" class="skl-line" :class="i === lines ? 'skl-line--short' : 'skl-line--full'" />
      </div>
    </template>

    <!-- List -->
    <template v-if="variant === 'list'">
      <div class="skl-list">
        <div v-for="i in count" :key="i" class="skl-list-item">
          <div class="skl-avatar" />
          <div class="skl-list-content">
            <div class="skl-line skl-line--md" />
            <div class="skl-line skl-line--xs" />
          </div>
        </div>
      </div>
    </template>

    <!-- Inline (single line, e.g. for labels) -->
    <template v-if="variant === 'inline'">
      <span class="skl-inline" :style="{ width: `${width}px` }" />
    </template>

    <span class="skl-sr-only">加载中...</span>
  </div>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    /** Skeleton variant */
    variant?: 'card' | 'table' | 'chart' | 'stats' | 'text' | 'list' | 'inline'
    /** Number of rows (table) or lines (text) or items (stats/list) */
    count?: number
    /** Width for inline variant, in px */
    width?: number
    /** Table columns: array of flex values */
    columns?: number[]
    /** Table row count */
    rows?: number
    /** Text line count */
    lines?: number
  }>(),
  {
    variant: 'text',
    count: 4,
    width: 80,
    columns: () => [1, 2, 1.5, 1, 0.8],
    rows: 5,
    lines: 3,
  }
)
</script>

<style scoped lang="less">
/* ── Root ──────────────────────────────────────────────── */
.skl-root {
  display: contents;
}

.skl-sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

/* ── Shimmer animation ──────────────────────────────────── */
@keyframes skl-shimmer {
  0% {
    background-position: -200% 0;
  }
  100% {
    background-position: 200% 0;
  }
}

/* ── Base skeleton elements ─────────────────────────────── */
.skl-line {
  height: 14px;
  border-radius: 6px;
  background: linear-gradient(
    90deg,
    rgba(255, 255, 255, 0.05) 25%,
    rgba(255, 255, 255, 0.10) 37%,
    rgba(255, 255, 255, 0.05) 63%
  );
  background-size: 200% 100%;
  animation: skl-shimmer 1.6s ease-in-out infinite;
  flex: 1;

  &--xs   { width: 60px;  flex: none; }
  &--short{ width: 120px; flex: none; }
  &--md   { width: 180px; flex: none; }
  &--full { width: 100%;  }
}

.skl-block {
  border-radius: 8px;
  background: linear-gradient(
    90deg,
    rgba(255, 255, 255, 0.04) 25%,
    rgba(255, 255, 255, 0.09) 37%,
    rgba(255, 255, 255, 0.04) 63%
  );
  background-size: 200% 100%;
  animation: skl-shimmer 1.6s ease-in-out infinite;

  &--sm { width: 48px;  height: 48px; }
  &--md { width: 80px;  height: 80px; }
  &--lg { width: 100%;  height: 120px; }
}

.skl-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  flex-shrink: 0;
  background: linear-gradient(
    90deg,
    rgba(255, 255, 255, 0.04) 25%,
    rgba(255, 255, 255, 0.09) 37%,
    rgba(255, 255, 255, 0.04) 63%
  );
  background-size: 200% 100%;
  animation: skl-shimmer 1.6s ease-in-out infinite;
}

.skl-inline {
  display: inline-block;
  height: 1em;
  border-radius: 4px;
  vertical-align: middle;
  background: linear-gradient(
    90deg,
    rgba(255, 255, 255, 0.05) 25%,
    rgba(255, 255, 255, 0.10) 37%,
    rgba(255, 255, 255, 0.05) 63%
  );
  background-size: 200% 100%;
  animation: skl-shimmer 1.6s ease-in-out infinite;
}

/* ── Card skeleton ──────────────────────────────────────── */
.skl-card {
  background: var(--fsd-bg-base);
  border: 1px solid var(--fsd-border);
  border-radius: var(--fsd-radius-lg);
  padding: var(--fsd-space-5);
  display: flex;
  flex-direction: column;
  gap: var(--fsd-space-4);
}

.skl-card-header {
  display: flex;
  flex-direction: column;
  gap: var(--fsd-space-2);
}

.skl-card-body {
  display: flex;
  flex-direction: column;
  gap: var(--fsd-space-3);
}

/* ── Table skeleton ─────────────────────────────────────── */
.skl-table {
  background: var(--fsd-bg-base);
  border: 1px solid var(--fsd-border);
  border-radius: var(--fsd-radius-lg);
  overflow: hidden;
}

.skl-table-header {
  display: flex;
  gap: var(--fsd-space-4);
  padding: var(--fsd-space-4) var(--fsd-space-5);
  border-bottom: 1px solid var(--fsd-border);
  background: var(--fsd-bg-elevated);
}

.skl-table-row {
  display: flex;
  gap: var(--fsd-space-4);
  padding: var(--fsd-space-3) var(--fsd-space-5);
  border-bottom: 1px solid var(--fsd-border);

  &:last-child {
    border-bottom: none;
  }
}

/* ── Chart skeleton ─────────────────────────────────────── */
.skl-chart {
  background: var(--fsd-bg-base);
  border: 1px solid var(--fsd-border);
  border-radius: var(--fsd-radius-lg);
  padding: var(--fsd-space-5);
}

.skl-chart-header {
  margin-bottom: var(--fsd-space-5);
}

.skl-chart-area {
  display: flex;
  align-items: flex-end;
  gap: var(--fsd-space-3);
  height: 180px;
  padding: 0 var(--fsd-space-2);
}

.skl-chart-bar {
  flex: 1;
  border-radius: 6px 6px 0 0;
  background: linear-gradient(
    180deg,
    rgba(34, 199, 230, 0.18) 0%,
    rgba(34, 199, 230, 0.04) 100%
  );
  animation: skl-shimmer 1.6s ease-in-out infinite;
  background-size: 200% 100%;
}

/* ── Stats skeleton ─────────────────────────────────────── */
.skl-stats {
  display: grid;
  gap: var(--fsd-space-4);

  @media (max-width: 767px) {
    grid-template-columns: 1fr !important;
  }
}

.skl-stat-card {
  background: var(--fsd-bg-base);
  border: 1px solid var(--fsd-border);
  border-radius: var(--fsd-radius-lg);
  padding: var(--fsd-space-5);
  display: flex;
  flex-direction: column;
  gap: var(--fsd-space-3);
}

/* ── Text skeleton ──────────────────────────────────────── */
.skl-text {
  display: flex;
  flex-direction: column;
  gap: var(--fsd-space-3);
}

/* ── List skeleton ──────────────────────────────────────── */
.skl-list {
  display: flex;
  flex-direction: column;
  gap: var(--fsd-space-2);
}

.skl-list-item {
  display: flex;
  align-items: center;
  gap: var(--fsd-space-3);
  padding: var(--fsd-space-3) var(--fsd-space-4);
  background: var(--fsd-bg-base);
  border: 1px solid var(--fsd-border);
  border-radius: var(--fsd-radius);
}

.skl-list-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: var(--fsd-space-2);
  min-width: 0;
}
</style>
