/**
 * Shared delivery geo logic composable
 * V5-D3: Unify geo conversion between MapPoc and Tracking delivery mode
 *
 * Encapsulates pilot zone boundaries, vehicle-to-marker conversion,
 * and delivery polygon/polyline construction.
 */
import { computed } from 'vue'
import { parkXYToGcj02, toAvGeoMarker, TEXTILE_PARK_GEO, ZJF_L0_COVERAGE, buildGeoPolylines, collectRouteFitPoints } from '@/maps'
import { PILOT_ZONE_POLYGONS } from '@/maps/zjfPilotGeo'
import type { GeoMapMarker, GeoMapPolygon, GeoMapPolyline, GeoMapCircle } from '@/maps'
import type { ParkVehicleSnapshot, ParkOrderSnapshot, ParkStation } from '@/types/park'

/**
 * Pilot zone boundary polygons (叠石桥 L1 试点)
 * Phase 3：替换单一大矩形为 5 个配送分区多边形，每分区不同颜色。
 */
export const PILOT_BOUNDARY_POLYGON: GeoMapPolygon[] = PILOT_ZONE_POLYGONS.map((zone) => ({
  id: zone.id,
  path: zone.path.map((p) => [p[0], p[1]] as [number, number]),
  strokeColor: zone.strokeColor,
  fillColor: zone.fillColor,
}))

/** L0 coverage circles (产业带图例) */
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

/** Convert park XY to GCJ-02 for a vehicle snapshot */
export function vehicleToGeoPosition(vehicle: ParkVehicleSnapshot): [number, number] {
  if (vehicle.longitude != null && vehicle.latitude != null) {
    return [Number(vehicle.longitude), Number(vehicle.latitude)]
  }
  return parkXYToGcj02(vehicle.x, vehicle.y)
}

/** Convert parking XY to GCJ-02 for a station */
export function stationToGeoPosition(station: ParkStation): [number, number] | null {
  if (station.coordLng != null && station.coordLat != null) {
    return [Number(station.coordLng), Number(station.coordLat)]
  }
  return parkXYToGcj02(station.x, station.y)
}

/** Build geo markers from vehicles (for MapPoc-style usage) */
export function buildVehicleGeoMarkers(vehicles: ParkVehicleSnapshot[]): GeoMapMarker[] {
  return vehicles.map((vehicle) =>
    toAvGeoMarker(String(vehicle.vehicleId), vehicleToGeoPosition(vehicle), {
      onlineStatus: vehicle.onlineStatus,
      dispatchStatus: vehicle.dispatchStatus,
      charging: vehicle.charging,
      lowBattery: vehicle.lowBattery,
      batteryStatus: vehicle.batteryStatus,
      currentTaskId: vehicle.currentTaskId,
      runtimeStage: vehicle.runtimeStage,
      routeInvalid: vehicle.routeInvalid,
      heading: vehicle.heading ?? null,
      label: `${vehicle.vehicleCode} · ${vehicle.batteryLevel}%`,
    }),
  )
}

/** Build delivery polylines from vehicles and orders */
export function buildDeliveryPolylines(
  vehicles: ParkVehicleSnapshot[],
  orders: ParkOrderSnapshot[],
  options?: { focusVehicleId?: number | null; includeOrderLines?: boolean },
): GeoMapPolyline[] {
  return buildGeoPolylines(vehicles, orders, options)
}

/** Collect fit-view points from delivery vehicles */
export function collectDeliveryFitPoints(
  vehicles: ParkVehicleSnapshot[],
  options?: { focusVehicleId?: number | null },
): [number, number][] {
  return collectRouteFitPoints(vehicles, options)
}

/** Reactive delivery geo markers from vehicles (for Tracking.vue delivery mode) */
export function useDeliveryVehicleMarkers(vehicles: import('vue').Ref<ParkVehicleSnapshot[]>) {
  const markers = computed<GeoMapMarker[]>(() =>
    buildVehicleGeoMarkers(vehicles.value),
  )
  return markers
}

/** Station markers for delivery (charging stations) */
export function buildChargeStationMarkers(
  stations: ParkStation[],
  vehicles: ParkVehicleSnapshot[],
): GeoMapMarker[] {
  return stations
    .filter((station) => (station.stationCode ?? '').startsWith('ZJF-CHG-'))
    .flatMap((station) => {
      const position = stationToGeoPosition(station)
      if (!position) return []
      const occupied = vehicles.filter(
        (v) => v.charging && v.targetCode === station.stationCode,
      ).length
      return [
        {
          id: `chg-${station.stationId}`,
          position,
          label: `⚡ ${station.stationCode}${occupied ? ' · 占用' : ''}`,
        },
      ]
    })
}

/**
 * 视觉规范 §6：订单目标点三层结构。
 *
 * <p>返回 3 类覆盖物：
 * <ol>
 *   <li>目标环（circle，半径 25m，stroke 状态色 + 透明 fill）</li>
 *   <li>服务位芯点（marker，半径 6m 等效视觉，深色填充）</li>
 *   <li>道路接入虚线（polyline，从芯点连接到接入道路节点；当前数据无 accessNode 时省略）</li>
 * </ol>
 *
 * @param orders 订单快照列表
 * @param options 可选过滤与状态色映射
 */
export function buildOrderTargetOverlays(
  orders: ParkOrderSnapshot[],
  options?: {
    focusOrderId?: number | null
    /** 状态色映射（默认按 runtimeStage 推断） */
    stageColor?: (stage: string) => string
  },
): { circles: GeoMapCircle[]; markers: GeoMapMarker[]; polylines: GeoMapPolyline[] } {
  const focusOrderId = options?.focusOrderId
  const stageColor =
    options?.stageColor ?? defaultOrderStageColor

  const circles: GeoMapCircle[] = []
  const markers: GeoMapMarker[] = []
  const polylines: GeoMapPolyline[] = []

  orders.forEach((order) => {
    if (focusOrderId != null && focusOrderId !== order.orderId) return
    const dropoff = order.dropoffStation
    const targetPosition = stationToGeoPosition(dropoff)
    if (!targetPosition) return
    const color = stageColor(order.runtimeStage)

    // 目标环（外环，25 米半径）
    circles.push({
      id: `target-ring-${order.orderId}`,
      center: targetPosition,
      radiusMeters: 25,
      strokeColor: color,
      fillColor: color,
      strokeWeight: 2,
      fillOpacity: 0.08,
      zIndex: 35,
    })

    // 服务位芯点（中心点 marker，6 米等效视觉）
    markers.push({
      id: `target-core-${order.orderId}`,
      position: targetPosition,
      label: `${order.orderNo}`,
      iconUrl: undefined,
      status: 'core',
    })

    // 道路接入虚线：当前 ParkStation 无 accessNode 字段，暂不输出
    // 待 V44 引入 service_position.access_node_lng/lat 后补全
  })

  return { circles, markers, polylines }
}

function defaultOrderStageColor(stage: string): string {
  const upper = String(stage ?? '').toUpperCase()
  if (upper === 'COMPLETED') return '#2DE08A'
  if (upper === 'FAILED' || upper === 'MANUAL_PENDING') return '#FF5C7C'
  if (upper === 'LOADING' || upper === 'UNLOADING' || upper === 'CHARGING') return '#FFC04D'
  return '#22C7E6'
}