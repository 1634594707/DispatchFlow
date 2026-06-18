import type { ParkGeofence, ParkOrderSnapshot, ParkStation, ParkVehicleSnapshot } from '@/types/park'
import { parkXYToGcj02, toAvGeoMarker } from './index'
import type { GeoMapCircle, GeoMapMarker, GeoMapPolygon, GeoMapPolyline } from './types'
import { ZJF_L0_COVERAGE, ZJF_PILOT_GEO } from './zjfPilotGeo'
import { shouldDrawPlannedRoute } from './routeValidation'

export function vehicleGeoPosition(vehicle: ParkVehicleSnapshot): [number, number] {
  if (vehicle.longitude != null && vehicle.latitude != null) {
    return [Number(vehicle.longitude), Number(vehicle.latitude)]
  }
  return parkXYToGcj02(vehicle.x, vehicle.y)
}

export function stationGeoPosition(station: ParkStation): [number, number] | null {
  if (station.coordLng != null && station.coordLat != null) {
    return [Number(station.coordLng), Number(station.coordLat)]
  }
  return parkXYToGcj02(station.x, station.y)
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

function shortVehicleCode(code: string): string {
  const parts = code.split('-')
  if (parts.length >= 2) return `${parts[0]}-${parts[1]}`
  return code.length > 10 ? `${code.slice(0, 10)}…` : code
}

export function buildVehicleGeoMarkers(
  vehicles: ParkVehicleSnapshot[],
  options?: { selectedId?: number | null; focusVehicleId?: number | null },
): GeoMapMarker[] {
  const focusId = options?.focusVehicleId ?? options?.selectedId
  return vehicles.map((vehicle) =>
    toAvGeoMarker(String(vehicle.vehicleId), vehicleGeoPosition(vehicle), {
      onlineStatus: vehicle.onlineStatus,
      dispatchStatus: vehicle.dispatchStatus,
      charging: vehicle.charging,
      lowBattery: vehicle.lowBattery,
      batteryStatus: vehicle.batteryStatus,
      heading: vehicle.heading ?? null,
      label: `${shortVehicleCode(vehicle.vehicleCode)} · ${vehicle.batteryLevel}%${
        vehicle.batteryStatus === 'CRITICAL' ? ' ⚠' : vehicle.lowBattery ? ' ↓' : ''
      }`,
    }),
  )
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
      },
    ]
  })
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
    if (vehicle.geoTrajectory && vehicle.geoTrajectory.length >= 2 && !vehicle.routeInvalid) {
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

export function buildGeofencePolygons(geofences: ParkGeofence[]): GeoMapPolygon[] {
  return geofences
    .filter((fence) => fence.status === 'ACTIVE' && fence.polygon?.length >= 3)
    .map((fence) => ({
      id: String(fence.id),
      path: fence.polygon.map((point) => [Number(point[0]), Number(point[1])] as [number, number]),
      strokeColor: fence.fenceType === 'RESTRICTED' ? '#FF5C7C' : '#2DE08A',
      fillColor: fence.fenceType === 'RESTRICTED' ? 'rgba(255, 92, 124, 0.15)' : 'rgba(45, 224, 138, 0.12)',
      zIndex: 10,
    }))
}

export function buildL0CoverageCircles(): GeoMapCircle[] {
  return [
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
}

export function pilotMapCenter(): [number, number] {
  return [ZJF_PILOT_GEO.anchorLng, ZJF_PILOT_GEO.anchorLat]
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
    points.push(vehicleGeoPosition(vehicle))
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
