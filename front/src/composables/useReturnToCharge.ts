import { ref } from 'vue'
import { fetchReturnToChargeRoute } from '@/api/charging'
import type { ChargingStation, ReturnToChargeRoute } from '@/api/charging'

/** 回充路径服务 */
export function useReturnToCharge() {
  const routeInfo = ref<ReturnToChargeRoute | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)

  /** 获取回充路径 */
  async function calculateRoute(vehicleId: number, stationId: number) {
    loading.value = true
    error.value = null
    routeInfo.value = null
    try {
      const res = await fetchReturnToChargeRoute(vehicleId, stationId)
      routeInfo.value = res.data
      return res.data
    } catch (e: any) {
      error.value = e?.message || '获取回充路径失败'
      throw e
    } finally {
      loading.value = false
    }
  }

  /** 判断是否需要回充 */
  function isReturnToChargeNeeded(soc: number, threshold = 30): boolean {
    return soc < threshold
  }

  /** 查找最近充电站 */
  function findNearestChargingStation(
    vehiclePosition: { x: number; y: number },
    stations: ChargingStation[]
  ): ChargingStation | null {
    if (stations.length === 0) return null

    let nearest: ChargingStation | null = null
    let minDist = Infinity

    for (const station of stations) {
      if (station.coordX == null || station.coordY == null) continue
      const dx = vehiclePosition.x - station.coordX
      const dy = vehiclePosition.y - station.coordY
      const dist = Math.hypot(dx, dy)
      if (dist < minDist) {
        minDist = dist
        nearest = station
      }
    }

    return nearest
  }

  return {
    routeInfo,
    loading,
    error,
    calculateRoute,
    isReturnToChargeNeeded,
    findNearestChargingStation,
  }
}