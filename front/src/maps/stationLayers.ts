import type { ParkOrderSnapshot, ParkStation, ParkVehicleSnapshot } from '@/types/park'

/** 找家纺 L1 短驳站点（仅地理 Tab / 移动下单） */
export const GEO_DELIVERY_AREA = 'ZJF'

export function isGeoDeliveryStation(station: Pick<ParkStation, 'area' | 'stationCode'>): boolean {
  if (station.area === GEO_DELIVERY_AREA) return true
  return (station.stationCode ?? '').startsWith('ZJF-')
}

/** 园区内部调度站点（仅 schematic Tab · park-map.svg） */
export function isSchematicParkStation(station: Pick<ParkStation, 'area' | 'stationCode'>): boolean {
  return !isGeoDeliveryStation(station)
}

export function isGeoDeliveryOrder(order: Pick<ParkOrderSnapshot, 'pickupStation' | 'dropoffStation'>): boolean {
  return isGeoDeliveryStation(order.pickupStation) || isGeoDeliveryStation(order.dropoffStation)
}

export function isSchematicParkOrder(order: Pick<ParkOrderSnapshot, 'pickupStation' | 'dropoffStation'>): boolean {
  return isSchematicParkStation(order.pickupStation) && isSchematicParkStation(order.dropoffStation)
}

/** 园区示意地图仅展示仿真车；REAL/VDA5050 走外部遥测，不在 schematic 图层绘制。 */
export function isSchematicParkVehicle(vehicle: Pick<ParkVehicleSnapshot, 'linkMode' | 'vehicleCode'>): boolean {
  return (vehicle.linkMode || 'SIM') === 'SIM' && (vehicle.vehicleCode ?? '').startsWith('PARK-')
}

/** 叠石桥真实地图仿真车（ZJF-AV-*，与 PARK-* 分池）。 */
export function isGeoDeliverySimVehicle(vehicle: Pick<ParkVehicleSnapshot, 'linkMode' | 'vehicleCode'>): boolean {
  return (vehicle.linkMode || 'SIM') === 'SIM' && (vehicle.vehicleCode ?? '').startsWith('ZJF-AV-')
}

export function filterSchematicParkVehicles(vehicles: ParkVehicleSnapshot[]): ParkVehicleSnapshot[] {
  return vehicles.filter(isSchematicParkVehicle)
}

export function filterGeoDeliverySimVehicles(vehicles: ParkVehicleSnapshot[]): ParkVehicleSnapshot[] {
  return vehicles.filter(isGeoDeliverySimVehicle)
}

export function filterSchematicStations(stations: ParkStation[]): ParkStation[] {
  return stations.filter(isSchematicParkStation)
}

export function filterGeoDeliveryStations(stations: ParkStation[]): ParkStation[] {
  return stations.filter(isGeoDeliveryStation)
}

export function filterSchematicOrders(orders: ParkOrderSnapshot[]): ParkOrderSnapshot[] {
  return orders.filter(isSchematicParkOrder)
}

export function filterGeoDeliveryOrders(orders: ParkOrderSnapshot[]): ParkOrderSnapshot[] {
  return orders.filter(isGeoDeliveryOrder)
}

/**
 * 按配送区域过滤车辆
 * - deliveryZone 为 'BOTH' 或缺失时，所有模式均可见
 * - zone='geo' 仅保留 'GEO_DELIVERY' 车辆（'BOTH' 通用）
 * - zone='schematic' 仅保留 'SCHEMATIC' 车辆（'BOTH' 通用）
 */
export function filterVehiclesByDeliveryZone(
  vehicles: ParkVehicleSnapshot[],
  zone: 'geo' | 'schematic',
): ParkVehicleSnapshot[] {
  return vehicles.filter(vehicle => {
    const vehicleZone = vehicle.deliveryZone || 'BOTH'
    if (vehicleZone === 'BOTH') return true
    return zone === 'geo'
      ? vehicleZone === 'GEO_DELIVERY'
      : vehicleZone === 'SCHEMATIC'
  })
}

/**
 * 按配送区域过滤站点
 * - deliveryZone 为 'GENERAL' 或缺失时，所有模式均可见
 * - zone='geo' 仅保留 'GEO_DELIVERY' 站点（'GENERAL' 通用）
 * - zone='schematic' 仅保留 'SCHEMATIC' 站点（'GENERAL' 通用）
 */
export function filterStationsByDeliveryZone(
  stations: ParkStation[],
  zone: 'geo' | 'schematic',
): ParkStation[] {
  return stations.filter(station => {
    const stationZone = station.deliveryZone || 'GENERAL'
    if (stationZone === 'GENERAL') return true
    return zone === 'geo'
      ? stationZone === 'GEO_DELIVERY'
      : stationZone === 'SCHEMATIC'
  })
}

/** 仅调度/回充 · 不可移动下单 · 默认不在工作台态势图层 */
export function isZjfDispatchOnlyStation(station: Pick<ParkStation, 'stationCode'>): boolean {
  const code = station.stationCode ?? ''
  return code === 'ZJF-IDLE-01' || code.startsWith('ZJF-CHG-')
}

/** 移动下单 / 典型线路：8 个可下单 ZJF 站（排除 CHG/IDLE） */
export function filterMobileOrderStations(stations: ParkStation[]): ParkStation[] {
  return filterGeoDeliveryStations(stations).filter(station => !isZjfDispatchOnlyStation(station))
}

/** 园区内部示意下单：A/B 区厂内站 */
export function filterSchematicOrderStations(stations: ParkStation[]): ParkStation[] {
  return filterSchematicStations(stations).filter(station => /^[AB][1-4]$/.test(station.stationCode ?? ''))
}

export const SCHEMATIC_ORDERABLE_STATION_COUNT = 8

export function orderableStationsForMode(stations: ParkStation[], mode: 'geo' | 'schematic'): ParkStation[] {
  return mode === 'schematic' ? filterSchematicOrderStations(stations) : filterMobileOrderStations(stations)
}

export const ZJF_ORDERABLE_STATION_COUNT = 8

export interface WorkbenchSituationFilterOptions {
  showIdle?: boolean
  showCharging?: boolean
}

/** 工作台园区态势：默认 8 运营站；可选待命点 / 充电站 */
export function filterWorkbenchSituationStations(
  stations: ParkStation[],
  options: WorkbenchSituationFilterOptions = {},
): ParkStation[] {
  const orderable = filterMobileOrderStations(stations)
  const extras: ParkStation[] = []
  if (options.showIdle) {
    extras.push(
      ...filterGeoDeliveryStations(stations).filter(station => station.stationCode === 'ZJF-IDLE-01'),
    )
  }
  if (options.showCharging) {
    extras.push(
      ...filterGeoDeliveryStations(stations).filter(station =>
        (station.stationCode ?? '').startsWith('ZJF-CHG-'),
      ),
    )
  }
  return [...orderable, ...extras]
}

export type WorkbenchStationRole = 'pickup' | 'dropoff' | 'express' | 'idle' | 'charging'

export function workbenchStationRole(station: Pick<ParkStation, 'stationCode'>): WorkbenchStationRole {
  const code = station.stationCode ?? ''
  if (code.startsWith('ZJF-PICK-')) return 'pickup'
  if (code.startsWith('ZJF-DROP-')) return 'dropoff'
  if (code.startsWith('ZJF-EXPRESS-')) return 'express'
  if (code.startsWith('ZJF-CHG-')) return 'charging'
  if (code === 'ZJF-IDLE-01') return 'idle'
  return station.stationCode?.startsWith('A') ? 'pickup' : 'dropoff'
}

const WORKBENCH_STATION_COLORS: Record<WorkbenchStationRole, string> = {
  pickup: '#22C7E6',
  dropoff: '#FFC04D',
  express: '#2DE08A',
  idle: '#9BA8B8',
  charging: '#9d4edd',
}

export function workbenchStationColor(station: Pick<ParkStation, 'stationCode' | 'area'>): string {
  return WORKBENCH_STATION_COLORS[workbenchStationRole(station)]
}

/** 移动下单站点分组（门市 / 代发仓 / 接驳） */
export type MobileOrderStationGroup = 'pickup' | 'dropoff' | 'express'

export const MOBILE_ORDER_STATION_GROUP_LABELS: Record<MobileOrderStationGroup, string> = {
  pickup: '门市',
  dropoff: '代发仓',
  express: '接驳',
}

const MOBILE_ORDER_GROUP_ORDER: MobileOrderStationGroup[] = ['pickup', 'dropoff', 'express']

export function mobileOrderStationGroup(station: Pick<ParkStation, 'stationCode'>): MobileOrderStationGroup {
  const code = station.stationCode ?? ''
  if (code.startsWith('ZJF-PICK-')) return 'pickup'
  if (code.startsWith('ZJF-DROP-')) return 'dropoff'
  return 'express'
}

export interface MobileStationSelectOption {
  value: number
  label: string
}

export interface MobileStationSelectGroup {
  label: string
  options: MobileStationSelectOption[]
}

export function buildGroupedMobileStationOptions(
  stations: ParkStation[],
  options?: { excludeStationId?: number | null; mode?: 'geo' | 'schematic' },
): MobileStationSelectGroup[] {
  const grouped = new Map<MobileOrderStationGroup, MobileStationSelectOption[]>()
  for (const key of MOBILE_ORDER_GROUP_ORDER) grouped.set(key, [])

  for (const station of stations) {
    if (options?.excludeStationId != null && station.stationId === options.excludeStationId) continue
    if (options?.mode === 'schematic') {
      const code = station.stationCode ?? ''
      const group: MobileOrderStationGroup = code.startsWith('A') ? 'pickup' : 'dropoff'
      grouped.get(group)!.push({
        value: station.stationId,
        label: `${station.stationCode} · ${station.stationName}`,
      })
      continue
    }
    grouped.get(mobileOrderStationGroup(station))!.push({
      value: station.stationId,
      label: `${station.stationCode} · ${station.stationName}`,
    })
  }

  const groupOrder =
    options?.mode === 'schematic'
      ? (['pickup', 'dropoff'] as MobileOrderStationGroup[])
      : MOBILE_ORDER_GROUP_ORDER

  return groupOrder.filter(key => grouped.get(key)!.length > 0).map(key => ({
    label:
      options?.mode === 'schematic'
        ? key === 'pickup'
          ? '取货区 A'
          : '送货区 B'
        : MOBILE_ORDER_STATION_GROUP_LABELS[key],
    options: grouped.get(key)!,
  }))
}

export function findMobileOrderStation(
  stations: ParkStation[],
  options: { stationId?: number | null; stationCode?: string | null },
  orderable?: ParkStation[],
): ParkStation | undefined {
  const pool = orderable ?? filterMobileOrderStations(stations)
  if (options.stationId != null) {
    return pool.find(station => station.stationId === options.stationId)
  }
  if (options.stationCode) {
    return pool.find(station => station.stationCode === options.stationCode)
  }
  return undefined
}

/** 丢弃失效站 ID，按模式回填默认可下单站点 */
export function syncDefaultOrderStations(
  stations: ParkStation[],
  mode: 'geo' | 'schematic',
  current: { pickupStationId?: number | null; dropoffStationId?: number | null },
): { pickupStationId?: number; dropoffStationId?: number; repaired: boolean } {
  const orderable = orderableStationsForMode(stations, mode)
  let pickup = findMobileOrderStation(stations, { stationId: current.pickupStationId }, orderable)
  let dropoff = findMobileOrderStation(stations, { stationId: current.dropoffStationId }, orderable)
  let repaired = false

  if (!pickup) {
    pickup =
      mode === 'schematic'
        ? orderable.find(station => station.stationCode === 'A1') ?? orderable[0]
        : orderable.find(station => station.stationCode?.startsWith('ZJF-PICK-')) ?? orderable[0]
    repaired = true
  }
  if (!dropoff || dropoff.stationId === pickup?.stationId) {
    const previousDropoffValid = dropoff != null && dropoff.stationId !== pickup?.stationId
    dropoff =
      mode === 'schematic'
        ? orderable.find(station => station.stationCode === 'B1' && station.stationId !== pickup?.stationId) ??
          orderable.find(station => station.stationId !== pickup?.stationId)
        : orderable.find(station => station.stationCode === 'ZJF-DROP-01' && station.stationId !== pickup?.stationId) ??
          orderable.find(station => station.stationId !== pickup?.stationId)
    repaired = repaired || !previousDropoffValid
  }

  return {
    pickupStationId: pickup?.stationId,
    dropoffStationId: dropoff?.stationId,
    repaired,
  }
}

/** @deprecated use syncDefaultOrderStations */
export function syncDefaultMobileOrderStations(
  stations: ParkStation[],
  current: { pickupStationId?: number | null; dropoffStationId?: number | null },
): { pickupStationId?: number; dropoffStationId?: number; repaired: boolean } {
  return syncDefaultOrderStations(stations, 'geo', current)
}
