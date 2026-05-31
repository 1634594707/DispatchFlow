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
export type { GeoMapHandle, GeoMapInitOptions, GeoMapMarker, GeoMapPolygon, MapProvider, MapProviderId } from './types'
export { defaultMapCenter, parkXYToGcj02, TEXTILE_PARK_GEO } from './textileParkGeo'
