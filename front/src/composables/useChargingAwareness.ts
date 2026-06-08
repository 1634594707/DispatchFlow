import { computed } from 'vue'

interface SocVehicleOption {
  vehicleId: number
  vehicleCode: string
  batteryLevel: number
}

const LOW_SOC_THRESHOLD = 30
const FLEET_LOW_SOC_WARNING_COUNT = 3

export interface SocCheckResult {
  shouldWarn: boolean
  vehicleSoc: number
  message: string
  recommendReturnToCharge: boolean
}

/**
 * 派单充电感知 composable
 * 用于在调度工作台中检测低电量车辆并给出提示
 */
export function useChargingAwareness(vehicles: { value: SocVehicleOption[] }) {
  /** SOC < 30% 的低电车辆列表 */
  const lowSocVehicles = computed(() =>
    vehicles.value.filter((v) => (v.batteryLevel ?? 0) < LOW_SOC_THRESHOLD)
  )

  /** 车队整体低电告警（当低电车辆数 ≥ 3 时） */
  const fleetLowSocWarning = computed(() =>
    lowSocVehicles.value.length >= FLEET_LOW_SOC_WARNING_COUNT
  )

  /** 低电车辆数 */
  const lowSocCount = computed(() => lowSocVehicles.value.length)

  /** 低电车辆编码列表 */
  const lowSocVehicleCodes = computed(() =>
    lowSocVehicles.value.map((v) => v.vehicleCode)
  )

  /** 检查某车辆在派单前是否需要警告 */
  function checkVehicleSocBeforeAssign(vehicleId: number): SocCheckResult | null {
    const vehicle = vehicles.value.find((v) => v.vehicleId === vehicleId)
    if (!vehicle) return null

    const soc = vehicle.batteryLevel ?? 100
    const shouldWarn = soc < LOW_SOC_THRESHOLD

    return {
      shouldWarn,
      vehicleSoc: soc,
      message: shouldWarn
        ? `车辆 ${vehicle.vehicleCode} 电量仅 ${soc}%，建议优先回充后派单`
        : '',
      recommendReturnToCharge: shouldWarn,
    }
  }

  return {
    lowSocVehicles,
    fleetLowSocWarning,
    lowSocCount,
    lowSocVehicleCodes,
    checkVehicleSocBeforeAssign,
    LOW_SOC_THRESHOLD,
  }
}