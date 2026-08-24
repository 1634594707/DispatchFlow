<template>
  <Teleport to="body">
    <Transition name="btt-fade">
      <button
        v-show="visible"
        type="button"
        class="btt-btn"
        :class="{ 'btt-btn--mobile': isPhone }"
        :aria-label="ariaLabel"
        @click="scrollToTop"
      >
        <VerticalAlignTopOutlined />
      </button>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { VerticalAlignTopOutlined } from '@ant-design/icons-vue'
import { useResponsive } from '@/composables/useResponsive'

const props = withDefaults(
  defineProps<{
    /** Scroll offset (px) after which the button appears */
    threshold?: number
    /** Target container selector (null = window) */
    target?: string | null
    /** Accessible label */
    ariaLabel?: string
  }>(),
  {
    threshold: 300,
    target: null,
    ariaLabel: '回到顶部',
  },
)

const { isPhone, prefersReducedMotion } = useResponsive()

const visible = ref(false)
let scrollEl: HTMLElement | Window = window

function getScrollTop(): number {
  if (scrollEl instanceof Window) {
    return window.scrollY || document.documentElement.scrollTop
  }
  return scrollEl.scrollTop
}

function onScroll() {
  visible.value = getScrollTop() > props.threshold
}

function scrollToTop() {
  const behavior = prefersReducedMotion.value ? 'instant' : 'smooth'
  if (scrollEl instanceof Window) {
    window.scrollTo({ top: 0, behavior })
    return
  }
  scrollEl.scrollTo({ top: 0, behavior })
}

function findScrollContainer() {
  if (props.target) {
    const el = document.querySelector(props.target)
    if (el) scrollEl = el as HTMLElement
    return
  }
  // Try to find the layout content container
  const contentEl = document.querySelector('.fsd-content')
  if (contentEl) {
    scrollEl = contentEl as HTMLElement
    return
  }
  scrollEl = window
}

onMounted(() => {
  findScrollContainer()
  scrollEl.addEventListener('scroll', onScroll, { passive: true })
  // Initial check
  onScroll()
})

onUnmounted(() => {
  scrollEl.removeEventListener('scroll', onScroll)
})
</script>

<style scoped lang="less">
.btt-btn {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: var(--fsd-z-sticky);
  width: 44px;
  height: 44px;
  border: 1px solid var(--fsd-border);
  border-radius: var(--fsd-radius-full);
  background: var(--fsd-bg-elevated);
  color: var(--fsd-text-secondary);
  font-size: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: none;
  transition:
    color var(--fsd-transition-fast),
    background-color var(--fsd-transition-fast),
    border-color var(--fsd-transition-fast);

  &:hover {
    background: var(--fsd-bg-hover);
    border-color: var(--fsd-accent-border);
    color: var(--fsd-accent);
  }

  &:active {
    background: var(--fsd-bg-active);
  }

  /* Mobile: adjust position for bottom nav */
  &--mobile {
    right: 16px;
    bottom: calc(80px + env(safe-area-inset-bottom, 0px));
    width: 48px;
    height: 48px;
  }
}

/* ── Transition ─────────────────────────────────────────── */
.btt-fade-enter-active,
.btt-fade-leave-active {
  transition: opacity var(--fsd-transition-base);
}

.btt-fade-enter-from,
.btt-fade-leave-to {
  opacity: 0;
}
</style>
