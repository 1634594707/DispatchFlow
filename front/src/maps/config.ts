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

function readConfigValue(key: keyof ImportMetaEnv): string {
  const runtimeValue = window.__DISPATCHFLOW_RUNTIME_CONFIG__?.[key as keyof DispatchFlowRuntimeConfig]
  if (typeof runtimeValue === 'string' && runtimeValue.trim()) {
    return runtimeValue.trim()
  }
  const envValue = import.meta.env[key]
  return typeof envValue === 'string' ? envValue.trim() : ''
}

export function getMapConfig() {
  const configuredProvider = readConfigValue('VITE_MAP_PROVIDER').toUpperCase()
  const amapKey = readConfigValue('VITE_AMAP_KEY')
  const amapSecurityCode = readConfigValue('VITE_AMAP_SECURITY_CODE')
  // AMap is opt-in when no provider or keys are supplied. This keeps the
  // local-graph/schematic fallback healthy instead of showing a false outage.
  const provider = (configuredProvider || (amapKey && amapSecurityCode ? 'AMAP' : 'SCHEMATIC')) as MapProviderId
  return {
    provider,
    amapKey,
    amapSecurityCode,
    defaultCenter: parseCenter(readConfigValue('VITE_AMAP_DEFAULT_CENTER') || undefined),
    defaultZoom: Number(readConfigValue('VITE_AMAP_DEFAULT_ZOOM') || 15),
  }
}

export function isAmapConfigured(): boolean {
  const config = getMapConfig()
  return config.provider === 'AMAP' && !!config.amapKey && !!config.amapSecurityCode
}
