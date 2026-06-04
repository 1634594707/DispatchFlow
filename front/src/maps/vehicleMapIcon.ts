import type { GeoMapMarker } from './types'

export type AvMapStatus = 'idle' | 'busy' | 'charging' | 'offline' | 'lowBattery'

const ICON_BY_STATUS: Record<AvMapStatus, string> = {
  idle: '/icons/av-delivery-idle.svg',
  busy: '/icons/av-delivery-busy.svg',
  charging: '/icons/av-delivery-charging.svg',
  offline: '/icons/av-delivery-offline.svg',
  lowBattery: '/icons/av-delivery-low-battery.svg',
}

export function resolveAvMapStatus(input: {
  onlineStatus?: string
  dispatchStatus?: string
  charging?: boolean
  lowBattery?: boolean
  batteryStatus?: string
}): AvMapStatus {
  if (input.onlineStatus === 'OFFLINE') return 'offline'
  if (input.batteryStatus === 'CRITICAL' || input.lowBattery) return 'lowBattery'
  if (input.charging) return 'charging'
  if (input.dispatchStatus === 'BUSY') return 'busy'
  return 'idle'
}

export function avMapIconUrl(status: AvMapStatus): string {
  return ICON_BY_STATUS[status]
}

export function toAvGeoMarker(
  id: string,
  position: [number, number],
  input: Parameters<typeof resolveAvMapStatus>[0] & { label?: string; heading?: number | null },
): GeoMapMarker {
  const status = resolveAvMapStatus(input)
  return {
    id,
    position,
    label: input.label,
    iconUrl: avMapIconUrl(status),
    heading: input.heading ?? undefined,
    status,
  }
}
