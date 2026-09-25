import type { ParkOrderSnapshot, ParkStation, ParkVehicleSnapshot } from '@/types/park'

/** 找家纺 L1 短驳站点（仅地理 Tab / 移动下单） */
export const GEO_DELIVERY_AREA = 'ZJF'

export function isGeoDeliveryStation(station: Pick<ParkStation, 'area' | 'stationCode'>): boolean {
  if (station.area === GEO_DELIVERY_AREA) return true
  return (station.stationCode ?? '').startsWith('ZJF-')
}

/** 园区调度站点（仅 schematic Tab · park-map.svg） */
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

/** 仅调度/回充 · 不可移动下单 · 默认不在工作台态势图层 */
export function isZjfDispatchOnlyStation(station: Pick<ParkStation, 'stationCode'>): boolean {
  const code = station.stationCode ?? ''
  return code === 'ZJF-IDLE-01' || code.startsWith('ZJF-CHG-')
}

/** 任意点下单自动落点（V64 `GEO-<节点>`）：不是人工作业点，不该出现在下单站点下拉里。 */
export function isAutoGeoEndpointStation(
  station: Pick<ParkStation, 'stationCode' | 'stationType'>,
): boolean {
  return station.stationType === 'GEO_POINT' || (station.stationCode ?? '').startsWith('GEO-')
}

/** 补能设施（充电桩 / 换电柜）：是车去的地方，不是货去的地方。
 *  `t_station` 里它们和作业点同表，只按 status/前缀过滤会漏 —— 35 个 `FSD-SWAP-*`
 *  一旦进下单下拉，用户就能把"取货点"选成一个电池柜。 */
export function isEnergyFacilityStation(
  station: Pick<ParkStation, 'stationCode' | 'stationType'>,
): boolean {
  return station.stationType === 'SWAP_CABINET'
    || station.stationType === 'CHARGING_STATION'
    || (station.stationCode ?? '').startsWith('ZJF-CHG-')
    || (station.stationCode ?? '').startsWith('FSD-SWAP-')
}

/** 移动下单 / 典型线路：地图上能作为**货的起终点**的 ZJF 站（排除补能/待命、自动落点）。
 *  ⚠ 数量取决于库里当前启停了哪些站 ⇒ 要个数就现算 `.length`，别在别处写常数。 */
export function filterMobileOrderStations(stations: ParkStation[]): ParkStation[] {
  return filterGeoDeliveryStations(stations).filter(
    (station) =>
      !isZjfDispatchOnlyStation(station)
      && !isAutoGeoEndpointStation(station)
      && !isEnergyFacilityStation(station),
  )
}

/** 园区示意地图下单：A/B 区厂内站 */
export function filterSchematicOrderStations(stations: ParkStation[]): ParkStation[] {
  return filterSchematicStations(stations).filter(station => /^[AB][1-4]$/.test(station.stationCode ?? ''))
}

export function orderableStationsForMode(stations: ParkStation[], mode: 'geo' | 'schematic'): ParkStation[] {
  return mode === 'schematic' ? filterSchematicOrderStations(stations) : filterMobileOrderStations(stations)
}

export interface WorkbenchSituationFilterOptions {
  showIdle?: boolean
  showCharging?: boolean
  /** 换电柜默认就画：它们是"车去哪儿补能"的主答案，藏起来地图上就只剩发货点。 */
  showSwap?: boolean
}

/** 工作台园区态势：可下单作业站 + 按需叠加的设施（待命点 / 充电桩 / 换电柜）。
 *  ⚠ 设施按 **stationType** 选，不再写死 `ZJF-CHG-01` —— 写死一个码意味着"库里新增多少
 *     充电桩和换电柜都不会出现在图上"，而 `FSD-SWAP-*` 那 35 个正是这样被吞掉的。 */
export function filterWorkbenchSituationStations(
  stations: ParkStation[],
  options: WorkbenchSituationFilterOptions = {},
): ParkStation[] {
  const orderable = filterMobileOrderStations(stations)
  const geo = filterGeoDeliveryStations(stations)
  const extras: ParkStation[] = []
  if (options.showIdle) {
    extras.push(...geo.filter((station) => station.stationCode === 'ZJF-IDLE-01'))
  }
  if (options.showCharging) {
    extras.push(...geo.filter((station) => station.stationType === 'CHARGING_STATION'))
  }
  if (options.showSwap !== false) {
    extras.push(...geo.filter((station) => station.stationType === 'SWAP_CABINET'))
  }
  return [...orderable, ...extras]
}

/** 移动端追踪图的补能图层（§4 T2-c）：只挑"车去补能的地方"，绝不参与下单端点选择。
 *  ⚠ 只用于**画图**：下单下拉走 `filterMobileOrderStations()`，两者不许共用出口。 */
export function mobileEnergyFacilityStations(stations: ParkStation[]): ParkStation[] {
  const facilities = filterWorkbenchSituationStations(stations, { showCharging: true }).filter(
    isEnergyFacilityStation,
  )
  // 柜排在桩前：`aggregateMarkersByPosition` 取组内第一个成员当徽标代表，而本机实测
  // （2026-09-25 活库 46 站）6 根 `FSD-CHG-*` 与 `FSD-SWAP-01..06` 是**同一个坐标**——
  // 谁在前决定那 6 个点画成"柜"还是"桩"。移动端的口径是 35 个柜（T2-c 闸门：柜 marker 数
  // = 接口返回的 SWAP_CABINET 数），所以让柜当代表，桩仍在徽标的 `aggregatedLabels` 里。
  return [
    ...facilities.filter((station) => station.stationType === 'SWAP_CABINET'),
    ...facilities.filter((station) => station.stationType !== 'SWAP_CABINET'),
  ]
}

export type WorkbenchStationRole =
  | 'pickup' | 'dropoff' | 'express' | 'idle' | 'charging' | 'swap' | 'warehouse'

/** 角色优先按 **stationType** 判，编码前缀只留给没有类型的历史站/示意站兜底。
 *  ⚠ 原来纯按前缀判 + "不是 A 开头就当 dropoff" 的兜底，会把 `FSD-SWAP-01` 画成**送货点**。 */
export function workbenchStationRole(
  station: Pick<ParkStation, 'stationCode' | 'stationType'>,
): WorkbenchStationRole {
  switch (station.stationType) {
    case 'SWAP_CABINET': return 'swap'
    case 'CHARGING_STATION': return 'charging'
    case 'MOTHERSHIP':
    case 'HUB': return 'warehouse'
    case 'GEO_POINT': return 'dropoff'
    default: break
  }
  const code = station.stationCode ?? ''
  if (code.startsWith('ZJF-PICK-')) return 'pickup'
  if (code.startsWith('ZJF-DROP-')) return 'dropoff'
  if (code.startsWith('ZJF-EXPRESS-')) return 'express'
  if (code.startsWith('ZJF-CHG-')) return 'charging'
  if (code === 'ZJF-IDLE-01') return 'idle'
  return code.startsWith('A') ? 'pickup' : 'dropoff'
}

const WORKBENCH_STATION_COLORS: Record<WorkbenchStationRole, string> = {
  pickup: '#22C7E6',
  dropoff: '#FFC04D',
  express: '#2DE08A',
  idle: '#9BA8B8',
  charging: '#9d4edd',
  swap: '#ff6b35',
  warehouse: '#4cc9f0',
}

export function workbenchStationColor(
  station: Pick<ParkStation, 'stationCode' | 'stationType' | 'area'>,
): string {
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
