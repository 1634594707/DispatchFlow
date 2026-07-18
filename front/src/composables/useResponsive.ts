/**
 * useResponsive — CSS Media Query based breakpoint detection
 *
 * Replaces User-Agent sniffing with proper CSS media query matching.
 * Provides reactive breakpoint states, touch detection, and accessibility preferences.
 *
 * Breakpoints (Ant Design Vue 4 grid alignment):
 *   xs  < 576px   (phone)
 *   sm  ≥ 576px   (large phone / small tablet)
 *   md  ≥ 768px   (tablet portrait)
 *   lg  ≥ 992px   (tablet landscape / small laptop)
 *   xl  ≥ 1200px  (desktop)
 *   xxl ≥ 1600px  (large desktop)
 */

import { ref, computed, onMounted, readonly } from 'vue'

export type BreakpointKey = 'xs' | 'sm' | 'md' | 'lg' | 'xl' | 'xxl'

interface BreakpointQuery {
  key: BreakpointKey
  query: string
}

const BREAKPOINTS: BreakpointQuery[] = [
  { key: 'xs', query: '(max-width: 575px)' },
  { key: 'sm', query: '(min-width: 576px) and (max-width: 767px)' },
  { key: 'md', query: '(min-width: 768px) and (max-width: 991px)' },
  { key: 'lg', query: '(min-width: 992px) and (max-width: 1199px)' },
  { key: 'xl', query: '(min-width: 1200px) and (max-width: 1599px)' },
  { key: 'xxl', query: '(min-width: 1600px)' },
]

// Singleton state shared across all useResponsive() calls
const breakpointState = ref<Record<BreakpointKey, boolean>>({
  xs: false,
  sm: false,
  md: false,
  lg: false,
  xl: false,
  xxl: false,
})

const isTouchDevice = ref(false)
const prefersReducedMotion = ref(false)
const prefersDarkScheme = ref(true)
const isInitialized = ref(false)

const listeners: Array<{
  mql: MediaQueryList
  handler: (e: MediaQueryListEvent) => void
}> = []

function setupBreakpoints() {
  if (isInitialized.value) return
  isInitialized.value = true

  for (const bp of BREAKPOINTS) {
    const mql = window.matchMedia(bp.query)
    const handler = (e: MediaQueryListEvent) => {
      breakpointState.value = { ...breakpointState.value, [bp.key]: e.matches }
    }
    mql.addEventListener('change', handler)
    listeners.push({ mql, handler })
    // Initial value
    breakpointState.value[bp.key] = mql.matches
  }

  // Touch detection
  isTouchDevice.value =
    'ontouchstart' in window ||
    navigator.maxTouchPoints > 0 ||
    window.matchMedia('(pointer: coarse)').matches

  // Accessibility preferences
  const motionMQL = window.matchMedia('(prefers-reduced-motion: reduce)')
  prefersReducedMotion.value = motionMQL.matches
  motionMQL.addEventListener('change', (e) => {
    prefersReducedMotion.value = e.matches
  })

  const darkMQL = window.matchMedia('(prefers-color-scheme: dark)')
  prefersDarkScheme.value = darkMQL.matches
  darkMQL.addEventListener('change', (e) => {
    prefersDarkScheme.value = e.matches
  })
}

function teardownBreakpoints() {
  for (const { mql, handler } of listeners) {
    mql.removeEventListener('change', handler)
  }
  listeners.length = 0
  isInitialized.value = false
}

export function useResponsive() {
  onMounted(() => setupBreakpoints())

  // ── Breakpoint booleans ────────────────────────────────
  const isXs = computed(() => breakpointState.value.xs)
  const isSm = computed(() => breakpointState.value.sm)
  const isMd = computed(() => breakpointState.value.md)
  const isLg = computed(() => breakpointState.value.lg)
  const isXl = computed(() => breakpointState.value.xl)
  const isXxl = computed(() => breakpointState.value.xxl)

  // ── Convenience groupings ──────────────────────────────
  /** Phone: xs only */
  const isPhone = computed(() => breakpointState.value.xs)
  /** Tablet: sm or md */
  const isTablet = computed(
    () => breakpointState.value.sm || breakpointState.value.md
  )
  /** Desktop+: lg and above */
  const isDesktop = computed(
    () =>
      breakpointState.value.lg ||
      breakpointState.value.xl ||
      breakpointState.value.xxl
  )
  /** Mobile (phone + tablet): below lg */
  const isMobile = computed(
    () =>
      breakpointState.value.xs ||
      breakpointState.value.sm ||
      breakpointState.value.md
  )

  // ── Current breakpoint name ────────────────────────────
  const currentBreakpoint = computed<BreakpointKey>(() => {
    for (const bp of BREAKPOINTS) {
      if (breakpointState.value[bp.key]) return bp.key
    }
    return 'xs'
  })

  // ── Layout helpers ─────────────────────────────────────
  /** How many columns in a stat grid */
  const statGridCols = computed<1 | 2 | 3 | 4>(() => {
    if (isXs.value) return 1
    if (isSm.value) return 2
    if (isMd.value) return 3
    return 4
  })

  /** Should the sidebar be a floating drawer? */
  const useDrawerSidebar = computed(() => isMobile.value)
  /** Should the sidebar auto-collapse? */
  const autoCollapseSidebar = computed(() => isTablet.value)

  return {
    // Raw breakpoint state
    breakpoints: readonly(breakpointState),
    currentBreakpoint,

    // Individual breakpoints
    isXs,
    isSm,
    isMd,
    isLg,
    isXl,
    isXxl,

    // Groupings
    isPhone,
    isTablet,
    isDesktop,
    isMobile,

    // Device capabilities
    isTouchDevice: readonly(isTouchDevice),
    prefersReducedMotion: readonly(prefersReducedMotion),
    prefersDarkScheme: readonly(prefersDarkScheme),

    // Layout helpers
    statGridCols,
    useDrawerSidebar,
    autoCollapseSidebar,
  }
}

/**
 * Setup responsive watcher once at app-level (no component needed).
 * Call in main.ts to eagerly initialize breakpoint detection.
 */
export function initResponsive() {
  if (typeof window !== 'undefined') {
    setupBreakpoints()
  }
}

/**
 * Cleanup (useful in tests / HMR).
 */
export function destroyResponsive() {
  teardownBreakpoints()
}
