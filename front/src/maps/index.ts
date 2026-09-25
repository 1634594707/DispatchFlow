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
export { formatAmapDomainAuthError, getAmapWhitelistHosts } from './amapAuth'
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
export {
  formatDeliveryEta,
  formatDistance,
  haversineMeters,
  polylineLengthMeters,
} from './geoDistance'
export { defaultMapCenter, TEXTILE_PARK_GEO } from './textileParkGeo'
export { toAvGeoMarker, resolveAvMapStatus, avMapIconUrl } from './vehicleMapIcon'
export { ZJF_PILOT_GEO, ZJF_L0_COVERAGE } from './zjfPilotGeo'
export {
  ZJF_BASE_GEO_RADIUS_METERS,
  ZJF_DELIVERY_ZONES,
  ZJF_REAL_WORLD_REFERENCE,
  type ZjfDeliveryZone,
} from './zjfStationAnchors'
export {
  GEO_DELIVERY_AREA,
  buildGroupedMobileStationOptions,
  filterGeoDeliveryOrders,
  filterGeoDeliveryStations,
  filterMobileOrderStations,
  filterSchematicOrderStations,
  findMobileOrderStation,
  orderableStationsForMode,
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
  isAutoGeoEndpointStation,
  isEnergyFacilityStation,
  mobileEnergyFacilityStations,
  mobileOrderStationGroup,
  MOBILE_ORDER_STATION_GROUP_LABELS,
  workbenchStationColor,
  workbenchStationRole,
} from './stationLayers'
export {
  buildGeofencePolygons,
  buildGeoPolylines,
  L0_COVERAGE_CIRCLES,
  MOBILE_SERVICE_FENCE_PREFIX,
  buildStationGeoMarkers,
  buildOperationalStationMarkers,
  buildVehicleGeoMarkers,
  markerColor,
  orderColor,
  shortVehicleCode,
  collectRouteFitPoints,
  pilotMapCenter,
  stationGeoPosition,
  vehicleGeoPosition,
  isVehiclePositionUnknown,
  countVehiclesWithUnknownPosition,
  splitVehiclesByBasePresence,
  aggregateMarkersByPosition,
  shouldAggregateMarkers,
  isInsideBase,
  basePositionFromStations,
  ZJF_BASE_STATION_CODE,
  ZJF_BASE_CHARGE_STATION_CODE,
  MAP_SCALE_TIERS,
  MARKER_BUDGET,
} from './parkGeoMapLayers'
