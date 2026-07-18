import type { GeoMapMarker } from './types'

/**
 * 车辆 8 状态（视觉规范 §5）。
 *
 * <p>状态优先级（高 → 低）：
 * <ol>
 *   <li>{@link #OFFLINE} 离线：onlineStatus=OFFLINE</li>
 *   <li>{@link #MANUAL_OVERRIDE} 人工接管：manualOverride=true</li>
 *   <li>{@link #OFF_ROUTE} 偏航：routeInvalid=true</li>
 *   <li>{@link #LOADING_UNLOADING} 到站装卸：runtimeStage=LOADING/UNLOADING</li>
 *   <li>{@link #WAITING} 等待：runtimeStage=WAITING/PAUSED</li>
 *   <li>{@link #CHARGING} 充电中：charging=true</li>
 *   <li>{@link #HEADING_CHARGE} 去充电：lowBattery && !charging</li>
 *   <li>{@link #DELIVERING} 执行配送：dispatchStatus=BUSY && currentTaskId</li>
 *   <li>{@link #ASSIGNABLE_IDLE} 可派空闲：默认</li>
 * </ol>
 */
export type AvMapStatus =
  | 'assignable_idle'
  | 'delivering'
  | 'heading_charge'
  | 'loading_unloading'
  | 'waiting'
  | 'charging'
  | 'off_route'
  | 'offline'
  | 'manual_override'

/** 旧版状态别名（向后兼容） */
export type LegacyAvMapStatus = 'idle' | 'busy' | 'charging' | 'offline' | 'lowBattery'

const ICON_BY_STATUS: Record<AvMapStatus, string> = {
  assignable_idle: '/icons/av-delivery-idle.svg',
  delivering: '/icons/av-delivery-busy.svg',
  heading_charge: '/icons/av-delivery-low-battery.svg',
  loading_unloading: '/icons/av-delivery-loading.svg',
  waiting: '/icons/av-delivery-waiting.svg',
  charging: '/icons/av-delivery-charging.svg',
  off_route: '/icons/av-delivery-off-route.svg',
  offline: '/icons/av-delivery-offline.svg',
  manual_override: '/icons/av-delivery-manual.svg',
}

/** 状态对应主色（视觉规范 §5.2 状态色） */
const COLOR_BY_STATUS: Record<AvMapStatus, string> = {
  assignable_idle: '#22D3EE',
  delivering: '#3B82F6',
  heading_charge: '#FB923C',
  loading_unloading: '#FBBF24',
  waiting: '#94A3B8',
  charging: '#FACC15',
  off_route: '#C4B5FD',
  offline: '#FF5C7C',
  manual_override: '#F97316',
}

const LEGACY_TO_NEW: Record<LegacyAvMapStatus, AvMapStatus> = {
  idle: 'assignable_idle',
  busy: 'delivering',
  charging: 'charging',
  offline: 'offline',
  lowBattery: 'heading_charge',
}

export interface AvMapStatusInput {
  onlineStatus?: string
  dispatchStatus?: string
  charging?: boolean
  lowBattery?: boolean
  batteryStatus?: string
  /** 当前任务 ID（非空表示执行中） */
  currentTaskId?: number | null
  /** 运行时阶段：LOADING/UNLOADING/WAITING/PAUSED/EXECUTING 等 */
  runtimeStage?: string
  /** 路线失效标记（偏航） */
  routeInvalid?: boolean | null
  /** 人工接管标记 */
  manualOverride?: boolean | null
  /** Last telemetry exceeded the backend freshness window. */
  telemetryStale?: boolean | null
}

export function resolveAvMapStatus(input: AvMapStatusInput): AvMapStatus {
  if (input.onlineStatus === 'OFFLINE' || input.telemetryStale) return 'offline'
  if (input.manualOverride) return 'manual_override'
  if (input.routeInvalid) return 'off_route'
  const stage = String(input.runtimeStage ?? '').toUpperCase()
  if (stage === 'LOADING' || stage === 'UNLOADING') return 'loading_unloading'
  if (stage === 'WAITING' || stage === 'PAUSED') return 'waiting'
  if (input.charging) return 'charging'
  if (input.batteryStatus === 'CRITICAL' || input.lowBattery) return 'heading_charge'
  if (input.dispatchStatus === 'BUSY' || input.currentTaskId != null) return 'delivering'
  return 'assignable_idle'
}

export function avMapIconUrl(status: AvMapStatus): string {
  return ICON_BY_STATUS[status]
}

export function avMapStatusColor(status: AvMapStatus): string {
  return COLOR_BY_STATUS[status]
}

/** 旧版兼容：将旧状态名映射到新状态 */
export function legacyStatus(status: LegacyAvMapStatus): AvMapStatus {
  return LEGACY_TO_NEW[status]
}

export function toAvGeoMarker(
  id: string,
  position: [number, number],
  input: AvMapStatusInput & { label?: string; heading?: number | null },
): GeoMapMarker {
  const status = resolveAvMapStatus(input)
  return {
    id,
    position,
    label: input.label,
    iconUrl: avMapIconUrl(status),
    heading: input.heading ?? undefined,
    status,
    markerType: 'vehicle',
  }
}
