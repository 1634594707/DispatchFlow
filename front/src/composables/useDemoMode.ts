import { ref, onUnmounted, computed } from 'vue'
import { DEMO_CONFIG } from '@/config/demo-config'
import { createParkOrder } from '@/api/park'

export function useDemoMode() {
  const demoMode = ref(false)
  const remainingMs = ref(0)
  const orderIndex = ref(0)

  let timer: ReturnType<typeof setInterval> | null = null
  let countdownTimer: ReturnType<typeof setInterval> | null = null

  const remainingLabel = computed(() => {
    const totalSec = Math.ceil(remainingMs.value / 1000)
    const min = Math.floor(totalSec / 60)
    const sec = totalSec % 60
    return `${min}:${sec.toString().padStart(2, '0')}`
  })

  async function nextDemoOrder() {
    const templates = DEMO_CONFIG.orderTemplates
    if (templates.length === 0) return

    const template = templates[orderIndex.value % templates.length]
    orderIndex.value++

    try {
      await createParkOrder({
        pickupStationId: template.pickupStationId,
        dropoffStationId: template.dropoffStationId,
        priority: template.priority,
        remark: template.remark ?? '[演示] 自动生成',
      })
    } catch {
      // Silently ignore demo order creation failures
    }
  }

  function resetCountdown() {
    remainingMs.value = DEMO_CONFIG.autoIntervalMs
  }

  function startDemo() {
    if (demoMode.value) return
    demoMode.value = true
    resetCountdown()

    void nextDemoOrder()

    timer = setInterval(() => {
      void nextDemoOrder()
      resetCountdown()
    }, DEMO_CONFIG.autoIntervalMs)

    countdownTimer = setInterval(() => {
      if (remainingMs.value > 0) {
        remainingMs.value = Math.max(0, remainingMs.value - 1000)
      }
    }, 1000)
  }

  function stopDemo() {
    demoMode.value = false
    remainingMs.value = 0
    if (timer) {
      clearInterval(timer)
      timer = null
    }
    if (countdownTimer) {
      clearInterval(countdownTimer)
      countdownTimer = null
    }
  }

  onUnmounted(() => {
    stopDemo()
  })

  return {
    demoMode,
    remainingMs,
    remainingLabel,
    startDemo,
    stopDemo,
    nextDemoOrder,
  }
}