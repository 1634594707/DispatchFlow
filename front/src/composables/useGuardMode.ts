import { onMounted, ref, watch } from 'vue'

const STORAGE_KEY = 'fsd_guard_mode'

// P3-5: theme-color 跟随 Guard Mode 切换，影响 Android 浏览器地址栏颜色
const THEME_COLOR_DEFAULT = '#08090C'
const THEME_COLOR_GUARD = '#0F1218'

function loadStored(): boolean {
  try {
    return localStorage.getItem(STORAGE_KEY) === '1'
  } catch {
    return false
  }
}

function persist(value: boolean) {
  try {
    localStorage.setItem(STORAGE_KEY, value ? '1' : '0')
  } catch {
    // ignore storage errors
  }
}

function applyThemeColor(value: boolean) {
  const meta = document.querySelector('meta[name="theme-color"]')
  if (meta) {
    meta.setAttribute('content', value ? THEME_COLOR_GUARD : THEME_COLOR_DEFAULT)
  }
}

export function useGuardMode() {
  const enabled = ref(loadStored())

  function applyClass(value: boolean) {
    document.documentElement.classList.toggle('fsd-guard-mode', value)
    applyThemeColor(value)
  }

  function toggle() {
    enabled.value = !enabled.value
  }

  onMounted(() => applyClass(enabled.value))

  watch(enabled, (value) => {
    persist(value)
    applyClass(value)
  })

  return { enabled, toggle }
}
