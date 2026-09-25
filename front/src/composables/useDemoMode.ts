import { computed, onUnmounted, ref } from 'vue'
import { DEMO_CONFIG } from '@/config/demo-config'
import { createParkOrder, getParkStations } from '@/api/park'
import { createIdempotencyKey } from '@/composables/useMobileOrderForm'
import type { ParkOrderCreateRequest, ParkStation } from '@/types/park'

/** 演示单的取送货点：GCJ-02 坐标，走 V64 的"任意点下单"入口，不是 stationId。 */
interface DemoRoute {
  pickupLng: number
  pickupLat: number
  dropoffLng: number
  dropoffLat: number
}

/**
 * 演示单的取送货点从哪来 —— **只用路网落点（`GEO_POINT`）的坐标**。
 *
 * 为什么不再按编码前缀找 `ZJF-PICK-*` / `ZJF-DROP-*`：设施 v2（`zjf_facility_v2.sql:26-31`）把
 * 所有 `PICKUP/DROPOFF/GENERAL` 站点置了 INACTIVE，接口只返回 ACTIVE ⇒ 这条找法在当前数据下
 * **恒为空**，于是"开始演示"点了就报错、立刻自停（就是本人截图里那句"没有可用的演示取送货站点"）。
 * 而业务事实本来就是"没有可下单作业点、用户拿任意点下单"，所以演示单也应该走同一条真实链路：
 * 给坐标，由后端吸附到路网节点。`GEO_POINT` 正是后端自己发布的落点，天然可吸附。
 *
 * ⚠ 这些坐标**不进下单下拉**：`filterMobileOrderStations()` 仍然把 `GEO-` 挡在外面（v14 钉零泄漏），
 * 这里只是拿它们当演示单的起终点。
 */
function resolveDemoRoute(stations: ParkStation[], index: number): DemoRoute | null {
  const points = stations.filter(
    (station) =>
      station.stationType === 'GEO_POINT' && station.coordLng != null && station.coordLat != null,
  )
  if (points.length < 2) return null

  const pickup = points[index % points.length]
  const dropoff = points[(index + 1) % points.length]
  if (pickup.stationId === dropoff.stationId) return null
  return {
    pickupLng: Number(pickup.coordLng),
    pickupLat: Number(pickup.coordLat),
    dropoffLng: Number(dropoff.coordLng),
    dropoffLat: Number(dropoff.coordLat),
  }
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
        lastError.value = '当前园区没有可用的演示取送货点（需要 ≥2 个路网落点 GEO_POINT）'
        return false
      }

      const request: ParkOrderCreateRequest = {
        idempotencyKey: createIdempotencyKey(),
        parkId,
        ...route,
        priority: 'P1',
        orderPriority: 'NORMAL',
        remark: '[演示] 自动生成',
      }
      await createParkOrder(request)
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
