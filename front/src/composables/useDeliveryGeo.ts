/**
 * Shared delivery geo logic composable
 * V5-D3: Unify geo conversion between MapPoc and Tracking delivery mode
 *
 * Encapsulates pilot zone boundaries, vehicle-to-marker conversion,
 * and delivery polygon/polyline construction.
 */
import { computed } from 'vue'
import { parkXYToGcj02, toAvGeoMarker, TEXTILE_PARK_GEO, ZJF_L0_COVERAGE, buildGeoPolylines, collectRouteFitPoints } from '@/maps'
import type { GeoMapMarker, GeoMapPolygon, GeoMapPolyline, GeoMapCircle } from '@/maps'
import type { ParkVehicleSnapshot, ParkOrderSnapshot, ParkStation } from '@/types/park'

/** Pilot zone boundary polygon (叠石桥 L1 试点) */
export const PILOT_BOUNDARY_POLYGON: GeoMapPolygon[] = [
  {
    id: 'zjf-pilot',
    path: TEXTILE_PARK_GEO.pilotPolygon.map((p) => [p[0], p[1]] as [number, number]),
    strokeColor: '#00d4aa',
    fillColor: 'rgba(0, 212, 170, 0.12)',
  },
]

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