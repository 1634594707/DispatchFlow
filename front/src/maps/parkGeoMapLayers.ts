import type {
  ParkGeofence,
  ParkOrderSnapshot,
  ParkStation,
  ParkVehicleSnapshot,
} from '@/types/park'
import { toAvGeoMarker } from './vehicleMapIcon'
import type { GeoMapCircle, GeoMapMarker, GeoMapPolygon, GeoMapPolyline } from './types'
import { ZJF_L0_COVERAGE, ZJF_PILOT_GEO } from './zjfPilotGeo'
import { ZJF_DELIVERY_ZONES, ZJF_BASE_GEO_RADIUS_METERS } from './zjfStationAnchors'
import { haversineMeters } from './geoDistance'
import { shouldDrawPlannedRoute } from './routeValidation'
import { workbenchStationRole } from './stationLayers'

/**
 * Phase 3：5 分区颜色映射表。
 * ZJF-ZONE-* 围栏使用各自分配的颜色；其他围栏按类型着色（BOUNDARY 绿色 / RESTRICTED 红色）。
 */
const ZONE_COLOR_MAP: Record<string, { stroke: string; fill: string }> = ZJF_DELIVERY_ZONES.reduce(
  (acc, zone) => {
    acc[zone.code] = { stroke: zone.color, fill: zone.color + '20' }
    return acc
  },
  {} as Record<string, { stroke: string; fill: string }>,
)

/**
 * 车辆的 GCJ-02 位置；**没有真经纬度就返回 null**（§7.6 / §7.2②）。
 *
 * <p>此前这里会退回 `parkXYToGcj02(x, y)`，把园区 schematic 像素当成经纬度换算出一个坐标。
 * 那正是本仓反复警告的"半做静默改错"：车不会报错，只会画到一个看起来像真实街道的位置上，
 * 而它与该车的真实位置没有任何关系。示意模式（`Tracking.vue` 的 Leaflet 画布）本来就按像素画，
 * 不需要一个假经纬度；地理图层要么有真坐标，要么明说"位置未知"。
 */
export function vehicleGeoPosition(vehicle: ParkVehicleSnapshot): [number, number] | null {
  if (vehicle.longitude != null && vehicle.latitude != null) {
    return [Number(vehicle.longitude), Number(vehicle.latitude)]
  }
  return null
}

/** 位置未知的车（既不在基地圈也不在路上，必须单独计入可见状态，不能悄悄消失）。 */
export function isVehiclePositionUnknown(vehicle: ParkVehicleSnapshot): boolean {
  return vehicleGeoPosition(vehicle) === null
}

export function countVehiclesWithUnknownPosition(vehicles: ParkVehicleSnapshot[]): number {
  return vehicles.filter(isVehiclePositionUnknown).length
}

/**
 * "在不在基地"。基地锚点是**数据**，所以由调用方传进来（`useParkMetadata().anchor()`，
 * 后端 `t_park.anchor_lng/lat`）——§6.4 之前它写死在 `zjfStationAnchors.ts` 里。
 * 传 null 表示"拿不到基地点"，此时一律算在路上：宁可少报"在场"，也不要凭一个假想原点把车归堆。
 */
export function isInsideBase(
  position: [number, number],
  basePosition: [number, number] | null | undefined,
  radiusMeters = ZJF_BASE_GEO_RADIUS_METERS,
): boolean {
  if (!basePosition) return false
  return haversineMeters(position, basePosition) <= radiusMeters
}

/**
 * 基地锚点 = **`ZJF-IDLE-01` 那个点的坐标**，只能从接口的站点里取。
 *
 * <p>为什么不用 `t_park.anchor_lng/lat`：实测那是 **schematic 画布的锚点**
 * （`DEFAULT` 园区是 `121.093236,31.937344`，§13.30 fit_canvas 的产物，2026-09-23 随走廊换图重定标），
 * 与基地点 `121.080681,31.960337` 相距约 **2.8 km** —— 拿它当基地，75 m 半径内一台车都不会算"在场"，
 * 是个安静变错的 bug。用它当**地图中心**才是对的。
 */
export const ZJF_BASE_STATION_CODE = 'ZJF-IDLE-01'

/** 基地那一侧承担"充电 + 在场徽标"的点位（示意上用它代表基地）。 */
export const ZJF_BASE_CHARGE_STATION_CODE = 'ZJF-CHG-01'

export function basePositionFromStations(
  stations: ParkStation[],
): [number, number] | null {
  const hit = stations.find((station) => station.stationCode === ZJF_BASE_STATION_CODE)
  return hit ? stationGeoPosition(hit) : null
}

/**
 * 按"在基地 / 在路上 / 位置未知"三分。
 *
 * <p>三桶而不是两桶：坐标缺失时把车默认算成"在路上"（旧代码的像素兜底事实上就是这么骗人的）
 * 会让"基地停留 N 台"这个数字看起来正常，实际是数据没上来。
 *
 * @param basePosition 基地锚点（GCJ-02）；null ⇒ 无法判定在场，有坐标的都记在路上
 */
export function splitVehiclesByBasePresence(
  vehicles: ParkVehicleSnapshot[],
  basePosition: [number, number] | null | undefined,
): {
  atBase: ParkVehicleSnapshot[]
  onRoad: ParkVehicleSnapshot[]
  unknown: ParkVehicleSnapshot[]
} {
  const atBase: ParkVehicleSnapshot[] = []
  const onRoad: ParkVehicleSnapshot[] = []
  const unknown: ParkVehicleSnapshot[] = []
  for (const vehicle of vehicles) {
    const position = vehicleGeoPosition(vehicle)
    if (!position) {
      unknown.push(vehicle)
    } else if (isInsideBase(position, basePosition)) {
      atBase.push(vehicle)
    } else {
      onRoad.push(vehicle)
    }
  }
  return { atBase, onRoad, unknown }
}

export function stationGeoPosition(station: ParkStation): [number, number] | null {
  if (station.coordLng != null && station.coordLat != null) {
    return [Number(station.coordLng), Number(station.coordLat)]
  }
  return null
}

export function markerColor(vehicle: ParkVehicleSnapshot): string {
  if (vehicle.onlineStatus === 'OFFLINE') return '#FF5C7C'
  if (vehicle.charging) return '#FFC04D'
  if (vehicle.lowBattery) return '#FF5C7C'
  if (vehicle.dispatchStatus === 'BUSY') return '#22C7E6'
  return '#2DE08A'
}

export function orderColor(stage: string): string {
  if (stage === 'COMPLETED') return '#2DE08A'
  if (stage === 'FAILED' || stage === 'MANUAL_PENDING') return '#FF5C7C'
  if (stage === 'LOADING' || stage === 'UNLOADING' || stage === 'CHARGING') return '#FFC04D'
  return '#22C7E6'
}

export function shortVehicleCode(code: string): string {
  const parts = code.split('-')
  if (parts.length >= 2) return `${parts[0]}-${parts[1]}`
  return code.length > 10 ? `${code.slice(0, 10)}…` : code
}

export function buildVehicleGeoMarkers(
  vehicles: ParkVehicleSnapshot[],
  options?: { selectedId?: number | null; focusVehicleId?: number | null },
): GeoMapMarker[] {
  const focusId = options?.focusVehicleId ?? options?.selectedId
  return vehicles.flatMap((vehicle) => {
    const position = vehicleGeoPosition(vehicle)
    if (!position) return []
    const marker = toAvGeoMarker(String(vehicle.vehicleId), position, {
      onlineStatus: vehicle.onlineStatus,
      dispatchStatus: vehicle.dispatchStatus,
      charging: vehicle.charging,
      lowBattery: vehicle.lowBattery,
      batteryStatus: vehicle.batteryStatus,
      currentTaskId: vehicle.currentTaskId,
      runtimeStage: vehicle.runtimeStage,
      routeInvalid: vehicle.routeInvalid,
      manualOverride: vehicle.manualOverride,
      telemetryStale: vehicle.telemetryStale,
      heading: vehicle.heading ?? null,
      label: `${shortVehicleCode(vehicle.vehicleCode)} · ${vehicle.batteryLevel}%${
        vehicle.batteryStatus === 'CRITICAL' ? ' ⚠' : vehicle.lowBattery ? ' ↓' : ''
      }${vehicle.telemetryStale ? ' · 数据陈旧' : ''}`,
    })
    const selected = focusId === vehicle.vehicleId
    return [
      {
        ...marker,
        selected,
        showLabel: options?.selectedId !== undefined ? selected : marker.showLabel,
      },
    ]
  })
}

export function buildStationGeoMarkers(
  stations: Array<{ id: string; station: ParkStation; label?: string }>,
): GeoMapMarker[] {
  return stations.flatMap(({ id, station, label }) => {
    const position = stationGeoPosition(station)
    if (!position) return []
    return [
      {
        id,
        position,
        label: label ?? station.stationCode,
        iconUrl: stationIconUrl(station),
        markerType: workbenchStationRole(station),
      },
    ]
  })
}

export function buildOperationalStationMarkers(
  stations: ParkStation[],
  options?: { selectedId?: string | null },
): GeoMapMarker[] {
  return aggregateMarkersByPosition(
    stations.flatMap((station) => {
      const position = stationGeoPosition(station)
      if (!position) return []
      const id = `station-${station.stationId}`
      const role = workbenchStationRole(station)
      const selected = options?.selectedId === id
      return [
        {
          id,
          position,
          label: `${station.stationName} · ${station.stationCode}`,
          iconUrl: stationIconUrl(station),
          markerType: role,
          selected,
          showLabel: selected,
        },
      ]
    }),
  )
}

function stationIconUrl(station: Pick<ParkStation, 'stationCode' | 'stationType'>): string {
  return `/icons/map-station-${workbenchStationRole(station)}.svg`
}

export function buildGeoPolylines(
  vehicles: ParkVehicleSnapshot[],
  orders: ParkOrderSnapshot[] = [],
  options?: {
    includeOrderLines?: boolean
    focusVehicleId?: number | null
    focusOrderId?: number | null
  },
): GeoMapPolyline[] {
  const lines: GeoMapPolyline[] = []
  const focusVehicleId = options?.focusVehicleId
  const focusOrderId = options?.focusOrderId
  const includeOrderLines = options?.includeOrderLines ?? true

  vehicles.forEach((vehicle) => {
    const isFocused = focusVehicleId == null || focusVehicleId === vehicle.vehicleId
    if (!isFocused) return

    if (shouldDrawPlannedRoute(vehicle) && vehicle.plannedRouteGeo) {
      lines.push({
        id: `plan-${vehicle.vehicleId}`,
        path: vehicle.plannedRouteGeo.map(
          (p) => [Number(p.longitude ?? p.x), Number(p.latitude ?? p.y)] as [number, number],
        ),
        strokeColor: '#22C7E6',
        strokeWeight: 5,
        strokeOpacity: 0.55,
        zIndex: 40,
      })
    }
    if (
      vehicle.geoTrajectory &&
      vehicle.geoTrajectory.length >= 2 &&
      !vehicle.routeInvalid &&
      !vehicle.telemetryStale
    ) {
      lines.push({
        id: `trail-${vehicle.vehicleId}`,
        path: vehicle.geoTrajectory.map(
          (p) => [Number(p.longitude ?? p.x), Number(p.latitude ?? p.y)] as [number, number],
        ),
        strokeColor: markerColor(vehicle),
        strokeWeight: 3,
        strokeOpacity: 0.75,
        lineDash: [6, 8],
        zIndex: 45,
      })
    }
  })

  if (includeOrderLines) {
    orders.forEach((order) => {
      if (focusOrderId != null && focusOrderId !== order.orderId) return
      const pickup = order.pickupStation
      const dropoff = order.dropoffStation
      if (pickup?.coordLng != null && dropoff?.coordLng != null) {
        lines.push({
          id: `order-${order.orderId}`,
          path: [
            [Number(pickup.coordLng), Number(pickup.coordLat)],
            [Number(dropoff.coordLng), Number(dropoff.coordLat)],
          ],
          strokeColor: orderColor(order.runtimeStage),
          strokeWeight: 2,
          strokeOpacity: 0.35,
          lineDash: [4, 10],
          zIndex: 30,
        })
      }
    })
  }

  return lines
}

/**
 * 移动端只画**受理围栏**的编码前缀（§4 T2-b，Q4=移动端隐藏包络）。
 *
 * <p>`DEFAULT-BOUNDARY`（`scopeCode=L1_CANDIDATE_ENVELOPE`）是 4.74 km² 的**展示包络**，
 * 不进受理判据；把它和 `ZJF-ZONE-SVC-01` 画在同一屏，用户读到的是"有两条服务范围"。
 * PC 工作台/大屏跟车仍在消费它（`OperationsCockpit.vue`、`Tracking.vue`），所以过滤只能由
 * 移动端调用方提出来，不改本函数的默认行为。
 */
export const MOBILE_SERVICE_FENCE_PREFIX = 'ZJF-ZONE-'

/** 拒单引导的围栏描边高亮宽度（§4 T2-f）：只改描边/填充与压叠次序，几何一个点都不动。 */
const FENCE_FLASH_STROKE_WEIGHT = 6

export interface GeofencePolygonOptions {
  /** 只画 `fenceCode` 以此前缀开头的围栏；缺省 = 不过滤（PC 端行为）。 */
  fenceCodePrefix?: string
  /** 描边闪一次：移动端"围栏外/吸附失败"拒单时把服务范围描边加粗提亮（仅样式，不改几何）。 */
  flashOutline?: boolean
}

export function buildGeofencePolygons(
  geofences: ParkGeofence[],
  options?: GeofencePolygonOptions,
): GeoMapPolygon[] {
  const prefix = options?.fenceCodePrefix
  return geofences
    .filter((fence) => fence.status === 'ACTIVE' && fence.polygon?.length >= 3)
    .filter((fence) => !prefix || (fence.fenceCode ?? '').startsWith(prefix))
    .map((fence) => {
      const isServiceEnvelope = fence.scopeCode === 'L1_CANDIDATE_ENVELOPE'
      // Phase 3：ZJF-ZONE-* 分区使用各自分配的颜色
      const zoneColor = ZONE_COLOR_MAP[fence.fenceCode]
      const strokeColor =
        zoneColor?.stroke ??
        (fence.fenceType === 'RESTRICTED'
          ? '#FF5C7C'
          : isServiceEnvelope
            ? '#13C2C2'
            : '#2DE08A')
      const fillColor =
        zoneColor?.fill ??
        (fence.fenceType === 'RESTRICTED'
          ? 'rgba(255, 92, 124, 0.15)'
          : isServiceEnvelope
            ? 'rgba(19, 194, 194, 0.06)'
            : 'rgba(45, 224, 138, 0.12)')
      const flash = options?.flashOutline === true && !isServiceEnvelope
      return {
        id: String(fence.id),
        path: fence.polygon.map(
          (point) => [Number(point[0]), Number(point[1])] as [number, number],
        ),
        strokeColor,
        fillColor,
        strokeWeight: flash ? FENCE_FLASH_STROKE_WEIGHT : isServiceEnvelope ? 2 : 2.5,
        fillOpacity: isServiceEnvelope ? 0.12 : flash ? 0.26 : 0.32,
        lineDash: isServiceEnvelope ? [10, 8] : undefined,
        zIndex: isServiceEnvelope ? 4 : flash ? 20 : 10,
      }
    })
}

/**
 * L0 产业带覆盖圈。**图层数据只此一份**（§6.3）：以前 `composables/useDeliveryGeo.ts`
 * 里还有一份逐字节相同的副本，两个页面各读各的，改一处就会让 GIS 图和工作台长期不一致。
 */
export const L0_COVERAGE_CIRCLES: GeoMapCircle[] = [
  {
    id: 'l0-chuanjiang',
    center: ZJF_L0_COVERAGE.chuanjiang.center,
    radiusMeters: ZJF_L0_COVERAGE.chuanjiang.radiusMeters,
    strokeColor: 'rgba(100, 149, 237, 0.35)',
    fillColor: 'rgba(100, 149, 237, 0.04)',
    zIndex: 1,
  },
  {
    id: 'l0-dieshiqiao',
    center: ZJF_L0_COVERAGE.dieshiqiao.center,
    radiusMeters: ZJF_L0_COVERAGE.dieshiqiao.radiusMeters,
    strokeColor: 'rgba(147, 112, 219, 0.3)',
    fillColor: 'rgba(147, 112, 219, 0.04)',
    zIndex: 1,
  },
]

export function pilotMapCenter(): [number, number] {
  return [ZJF_PILOT_GEO.anchorLng, ZJF_PILOT_GEO.anchorLat]
}

/**
 * 规模档与聚合阈值（§6.3 / §6.5）。
 *
 * <p>档位口径与 §1.4 车队规模表一致：M 档 20 台可全量画，L 档 40 台起必须聚合 ——
 * 这里的数字**只服务于"画得动不画得动"**，不是容量证明（容量结论在 §13.20 的 L 档退化曲线里）。
 */
export const MAP_SCALE_TIERS = { M: 20, L: 40 } as const

/** 单页 marker 预算：超过就该聚合而不是硬画（e2e 里有断言，改数值得一起改判据）。 */
export const MARKER_BUDGET = 60

export function shouldAggregateMarkers(vehicleCount: number): boolean {
  return vehicleCount >= MAP_SCALE_TIERS.L
}

/**
 * 同一坐标叠了多个点位时合成一个"计数徽标"（§7.5 的 14 对象叠一点）。
 *
 * <p>为什么按位置而不是按类型：基地那 6 根桩 + 待命位 + IDLE 点在数据层**就是同一个坐标**
 * （`t_charging_pile` 无 coord 列，按 `entry_node_code` 上图，全挂 `OSM0009`）。数据层保留真实
 * 坐标是 §7.5 定的处置，所以只能由图层侧收 —— 否则 14 个图标叠成一个看不清的墨点，
 * 而点开哪一个都靠运气。
 *
 * <p>取组内第一个成员当代表：它的 `id`/`markerType`/`iconUrl` 决定点开后选中谁。
 */
export function aggregateMarkersByPosition(
  markers: GeoMapMarker[],
  options?: { minCount?: number },
): GeoMapMarker[] {
  const minCount = options?.minCount ?? 2
  const groups = new Map<string, GeoMapMarker[]>()
  for (const marker of markers) {
    const key = `${marker.position[0].toFixed(6)},${marker.position[1].toFixed(6)}`
    const group = groups.get(key)
    if (group) group.push(marker)
    else groups.set(key, [marker])
  }
  return [...groups.values()].flatMap((group) => {
    const [head] = group
    if (group.length < minCount) return [head]
    const names = group.map((marker) => marker.label ?? marker.id)
    return [
      {
        ...head,
        label: `${names[0]} 等 ${group.length} 个点位`,
        showLabel: true,
        // 组里任何一个被选中，徽标就得显示为选中态 —— 否则点掉叠在下面的那个点位会"选了个没反应"。
        selected: group.some((marker) => marker.selected === true),
        aggregatedCount: group.length,
        aggregatedLabels: names,
      },
    ]
  })
}

/** Collect GCJ-02 points for fitView — prefer focused vehicle route over station bbox. */
export function collectRouteFitPoints(
  vehicles: ParkVehicleSnapshot[],
  options?: { focusVehicleId?: number | null },
): [number, number][] {
  const points: [number, number][] = []
  const targets =
    options?.focusVehicleId != null
      ? vehicles.filter((vehicle) => vehicle.vehicleId === options.focusVehicleId)
      : vehicles.filter((vehicle) => vehicle.dispatchStatus === 'BUSY')

  targets.forEach((vehicle) => {
    const position = vehicleGeoPosition(vehicle)
    if (position) points.push(position)
    if (vehicle.plannedRouteGeo && vehicle.plannedRouteGeo.length >= 2) {
      vehicle.plannedRouteGeo.forEach((p) => {
        points.push([Number(p.longitude ?? p.x), Number(p.latitude ?? p.y)])
      })
    }
    if (vehicle.geoTrajectory && vehicle.geoTrajectory.length >= 2) {
      vehicle.geoTrajectory.forEach((p) => {
        points.push([Number(p.longitude ?? p.x), Number(p.latitude ?? p.y)])
      })
    }
  })
  return points
}
