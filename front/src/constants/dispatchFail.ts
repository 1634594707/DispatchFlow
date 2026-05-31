export interface DispatchFailExplain {
  reasonCode: string
  reasonMessage: string
  suggestions: string[]
}

export const DISPATCH_FAIL_REASON: Record<string, string> = {
  NO_VEHICLE: '无可用车辆',
  NO_IDLE_VEHICLE: '无在线空闲车辆',
  LOW_SOC: '电量不足',
  LOW_BATTERY: '电量不足',
  UNREACHABLE: '取货点不可达',
  ROUTE_BLOCKED: '路网不可达或路段管制',
  HUB_CAPACITY_FULL: '枢纽容量已满',
  ROUTE_OCCUPANCY_FULL: '线路并发已满',
  CONFLICT: '占车/占桩冲突',
}

export const DISPATCH_FAIL_LINKS: Record<string, { label: string; path: string }[]> = {
  NO_IDLE_VEHICLE: [
    { label: '车辆列表', path: '/vehicles' },
    { label: '异常队列', path: '/exceptions?status=OPEN' },
  ],
  LOW_BATTERY: [
    { label: '车辆列表', path: '/vehicles?onlineStatus=ONLINE' },
    { label: '充电报表', path: '/analytics/charging' },
  ],
  ROUTE_BLOCKED: [
    { label: '路网管理', path: '/infrastructure/road-network' },
    { label: '交通态势', path: '/infrastructure/traffic' },
  ],
  HUB_CAPACITY_FULL: [{ label: '母港分流', path: '/vertical/hub' }],
  ROUTE_OCCUPANCY_FULL: [{ label: '线路管理', path: '/vertical/routes' }],
  CONFLICT: [{ label: '异常队列', path: '/exceptions?status=OPEN' }],
}

export function normalizeFailCode(code?: string | null): string {
  if (!code) return 'NO_IDLE_VEHICLE'
  switch (code.toUpperCase()) {
    case 'NO_VEHICLE':
      return 'NO_IDLE_VEHICLE'
    case 'LOW_SOC':
      return 'LOW_BATTERY'
    case 'UNREACHABLE':
    case 'ZONE_PAUSED':
      return 'ROUTE_BLOCKED'
    default:
      return code.toUpperCase()
  }
}

export function failReasonLabel(code?: string | null, fallbackMsg?: string | null): string {
  const normalized = normalizeFailCode(code)
  return DISPATCH_FAIL_REASON[normalized] || fallbackMsg || '派车失败'
}

export function failActionLinks(code?: string | null) {
  const normalized = normalizeFailCode(code)
  return DISPATCH_FAIL_LINKS[normalized] || [{ label: '异常队列', path: '/exceptions?status=OPEN' }]
}

export function explainFromAssignResponse(res: {
  reasonCode?: string | null
  reasonMessage?: string | null
  failReasonCode?: string | null
  message?: string | null
  suggestions?: string[] | null
}): DispatchFailExplain {
  const code = res.reasonCode || normalizeFailCode(res.failReasonCode)
  return {
    reasonCode: code,
    reasonMessage: res.reasonMessage || res.message || failReasonLabel(code),
    suggestions: res.suggestions?.length ? res.suggestions : [],
  }
}
