import { computed, onUnmounted, ref } from 'vue'
import { DEMO_CONFIG } from '@/config/demo-config'
import { createParkOrder, getParkStations } from '@/api/park'
import { createIdempotencyKey } from '@/composables/useMobileOrderForm'
import { filterMobileOrderStations, mobileOrderStationGroup } from '@/maps/stationLayers'
import type { ParkStation } from '@/types/park'

interface DemoRoute {
  pickupStationId: number
  dropoffStationId: number
}

function resolveDemoRoute(stations: ParkStation[], index: number): DemoRoute | null {
  const orderable = filterMobileOrderStations(stations)
  const pickups = orderable.filter((station) => mobileOrderStationGroup(station) === 'pickup')
  const destinations = orderable.filter((station) => {
    const group = mobileOrderStationGroup(station)
    return group === 'dropoff' || group === 'express'
  })
  if (pickups.length === 0 || destinations.length === 0) return null

  const pickup = pickups[index % pickups.length]
  const dropoff = destinations[index % destinations.length]
  if (pickup.stationId === dropoff.stationId) return null
  return { pickupStationId: pickup.stationId, dropoffStationId: dropoff.stationId }
}

function resolveErrorMessage(error: unknown): string {
  const responseMessage = (error as { response?: { data?: { message?: string } } })?.response?.data?.message
  return responseMessage || (error instanceof Error ? error.message : '演示订单创建失败')
}

export function useDemoMode(resolveParkId: () => number | undefined) {
  const demoMode = ref(false)
  const remainingMs = ref(0)
  const orderIndex = ref(0)
  const creatingOrder = ref(false)
  const lastError = ref('')

  let timer: ReturnType<typeof setInterval> | null = null
  let countdownTimer: ReturnType<typeof setInterval> | null = null

  const remainingLabel = computed(() => {
    const totalSec = Math.ceil(remainingMs.value / 1000)
    const min = Math.floor(totalSec / 60)
    const sec = totalSec % 60
    return min + ':' + sec.toString().padStart(2, '0')
  })

  async function nextDemoOrder(): Promise<boolean> {
    if (creatingOrder.value) return true
    const parkId = resolveParkId()
    if (!parkId) {
      lastError.value = '未选择园区，无法创建演示订单'
      return false
    }

    creatingOrder.value = true
    try {
      const stationResponse = await getParkStations(parkId)
      const route = resolveDemoRoute(stationResponse.data || [], orderIndex.value)
      if (!route) {
        lastError.value = '当前园区没有可用的演示取送货站点'
        return false
      }

      await createParkOrder({
        idempotencyKey: createIdempotencyKey(),
        parkId,
        pickupStationId: route.pickupStationId,
        dropoffStationId: route.dropoffStationId,
        priority: 'P1',
        orderPriority: 'NORMAL',
        deliveryZone: 'GEO_DELIVERY',
        remark: '[演示] 自动生成',
      })
      orderIndex.value += 1
      lastError.value = ''
      return true
    } catch (error) {
      lastError.value = resolveErrorMessage(error)
      return false
    } finally {
      creatingOrder.value = false
    }
  }

  function resetCountdown() {
    remainingMs.value = DEMO_CONFIG.autoIntervalMs
  }

  async function startDemo() {
    if (demoMode.value) return
    lastError.value = ''
    demoMode.value = true
    resetCountdown()

    if (!(await nextDemoOrder())) {
      stopDemo()
      return
    }

    timer = setInterval(() => {
      void nextDemoOrder().then((created) => {
        if (!created) {
          stopDemo()
          return
        }
        resetCountdown()
      })
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
    lastError,
    startDemo,
    stopDemo,
    nextDemoOrder,
  }
}
