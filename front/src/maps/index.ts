import { AmapProvider } from './amapProvider'
import { getMapConfig } from './config'
import type { MapProvider } from './types'

export function resolveGeoMapProvider(): MapProvider | null {
  const { provider } = getMapConfig()
  if (provider !== 'AMAP') {
    return null
  }
  const amap = new AmapProvider()
  return amap.isAvailable() ? amap : null
}

export { AmapProvider } from './amapProvider'
export { getMapConfig, isAmapConfigured } from './config'
export type {
  GeoMapCircle,
  GeoMapHandle,
  GeoMapInitOptions,
  GeoMapMarker,
  GeoMapPolygon,
  GeoMapPolyline,
  MapProvider,
  MapProviderId,
} from './types'
export { formatDeliveryEta, formatDistance, haversineMeters, polylineLengthMeters } from './geoDistance'
export { defaultMapCenter, parkXYToGcj02, TEXTILE_PARK_GEO } from './textileParkGeo'
export { toAvGeoMarker, resolveAvMapStatus, avMapIconUrl } from './vehicleMapIcon'
export { ZJF_PILOT_GEO, ZJF_L0_COVERAGE, ZJF_FLEET_STATS } from './zjfPilotGeo'
export {
  GEO_DELIVERY_AREA,
  buildGroupedMobileStationOptions,
  filterGeoDeliveryOrders,
  filterGeoDeliveryStations,
  filterMobileOrderStations,
  filterSchematicOrderStations,
  findMobileOrderStation,
  orderableStationsForMode,
  syncDefaultMobileOrderStations,
  syncDefaultOrderStations,
  filterSchematicParkVehicles,
  filterGeoDeliverySimVehicles,
  filterSchematicOrders,
  filterSchematicStations,
  isGeoDeliverySimVehicle,
  isSchematicParkVehicle,
  filterWorkbenchSituationStations,
  isGeoDeliveryOrder,
  isGeoDeliveryStation,
  isSchematicParkOrder,
  isSchematicParkStation,
  isZjfDispatchOnlyStation,
  mobileOrderStationGroup,
  MOBILE_ORDER_STATION_GROUP_LABELS,
  workbenchStationColor,
  workbenchStationRole,
  ZJF_ORDERABLE_STATION_COUNT,
  SCHEMATIC_ORDERABLE_STATION_COUNT,
} from './stationLayers'
export {
  buildGeofencePolygons,
  buildGeoPolylines,
  buildL0CoverageCircles,
  buildStationGeoMarkers,
  buildVehicleGeoMarkers,
  markerColor,
  orderColor,
  collectRouteFitPoints,
  pilotMapCenter,
  stationGeoPosition,
  vehicleGeoPosition,
} from './parkGeoMapLayers'
