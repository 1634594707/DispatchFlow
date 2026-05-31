import type { MapProviderId } from './types'
import { defaultMapCenter } from './textileParkGeo'

function parseCenter(raw?: string): [number, number] {
  if (!raw) return defaultMapCenter()
  const parts = raw.split(',').map((item) => Number(item.trim()))
  if (parts.length !== 2 || parts.some((value) => !Number.isFinite(value))) {
    return defaultMapCenter()
  }
  return [parts[0], parts[1]]
}

export function getMapConfig() {
  const provider = (import.meta.env.VITE_MAP_PROVIDER || 'AMAP').toUpperCase() as MapProviderId
  return {
    provider,
    amapKey: import.meta.env.VITE_AMAP_KEY?.trim() || '',
    amapSecurityCode: import.meta.env.VITE_AMAP_SECURITY_CODE?.trim() || '',
    defaultCenter: parseCenter(import.meta.env.VITE_AMAP_DEFAULT_CENTER),
    defaultZoom: Number(import.meta.env.VITE_AMAP_DEFAULT_ZOOM || 15),
  }
}

export function isAmapConfigured(): boolean {
  const config = getMapConfig()
  return config.provider === 'AMAP' && !!config.amapKey && !!config.amapSecurityCode
}
