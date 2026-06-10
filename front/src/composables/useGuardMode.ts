import { onMounted, ref, watch } from 'vue'

const STORAGE_KEY = 'fsd_guard_mode'

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

export function useGuardMode() {
  const enabled = ref(loadStored())

  function applyClass(value: boolean) {
    document.documentElement.classList.toggle('fsd-guard-mode', value)
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
