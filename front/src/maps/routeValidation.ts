import type { ParkVehicleSnapshot } from '@/types/park'

/** M8-R8：是否有车辆路线被判定为穿越建筑/水域。 */
export function hasInvalidRoute(vehicles: ParkVehicleSnapshot[]): boolean {
  return vehicles.some((vehicle) => vehicle.routeInvalid === true)
}

/** 地理图顶栏路线异常文案。 */
export function routeAnomalyWarning(vehicles: ParkVehicleSnapshot[]): string {
  const busy = vehicles.filter((vehicle) => vehicle.dispatchStatus === 'BUSY')
  const staleCount = vehicles.filter((vehicle) => vehicle.telemetryStale).length
  if (hasInvalidRoute(busy)) {
    return '路线异常：检测到穿越建筑或水域，已隐藏异常计划线。'
  }
  if (staleCount > 0) {
    return `${staleCount} 辆车位置超过 30 秒未更新，已隐藏其旧路线。`
  }
  return ''
}

/** 是否应绘制计划路线 polyline。 */
export function shouldDrawPlannedRoute(vehicle: ParkVehicleSnapshot): boolean {
  if (vehicle.routeInvalid || vehicle.telemetryStale) return false
  return Boolean(vehicle.plannedRouteGeo && vehicle.plannedRouteGeo.length >= 4)
}
