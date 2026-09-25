import type { ParkOrderEndpoint, ParkStation } from '@/types/park'

/**
 * 任意点下单的三条受理判据在后端各自带一个原因码（`OrderEndpointResolver`）。
 * 这里只给"人话标题"，细节仍以后端返回的 message 为准 —— 半径、坐标这些数字只有后端知道。
 */
export const ORDER_ENDPOINT_REJECT_LABELS: Record<string, string> = {
  ORDER_ENDPOINT_OUT_OF_SERVICE_AREA: '这个点不在服务范围内',
  ORDER_ENDPOINT_SNAP_FAILED: '这个点附近没有可派车的道路',
  ORDER_ENDPOINT_UNREACHABLE: '取货点与送货点之间没有可通行道路',
  ORDER_ROAD_GRAPH_EMPTY: '园区路网暂不可用，暂时无法按坐标下单',
}

export interface OrderRejection {
  code: string
  headline: string
  detail: string
  /** 该原因码是否附带"下一步该怎么选点"的引导（§4 T2-f）。 */
  guidance?: string
}

/**
 * 拒单引导文案（§4 T2-f）：只给"点不在服务区"和"吸附不到路"这两类 —— 这两类的下一步动作是同一件
 * 事（在画出来的服务范围里重选一个点）。 unreachable / 路网空 是系统侧问题，引导用户改点只会误导。
 */
export const ORDER_REJECT_SERVICE_AREA_GUIDANCE = '请在高亮的服务范围内选点'

const ORDER_REJECT_GUIDANCE_CODES: readonly string[] = [
  'ORDER_ENDPOINT_OUT_OF_SERVICE_AREA',
  'ORDER_ENDPOINT_SNAP_FAILED',
]

export function needsServiceAreaGuidance(code?: string | null): boolean {
  return !!code && ORDER_REJECT_GUIDANCE_CODES.includes(code)
}

export function describeOrderRejection(err: unknown): OrderRejection {
  const shaped = err as { code?: string; message?: string; rawMessage?: string } | undefined
  const code = shaped?.code || 'ORDER_CREATE_FAILED'
  const detail = shaped?.rawMessage || shaped?.message || '下单失败，请稍后重试'
  return {
    code,
    headline: ORDER_ENDPOINT_REJECT_LABELS[code] || '下单被拒绝',
    detail,
    guidance: needsServiceAreaGuidance(code) ? ORDER_REJECT_SERVICE_AREA_GUIDANCE : undefined,
  }
}

/** 坐标端点是否填全了 —— 半个坐标不许提交，避免后端把缺经纬度的请求当成"没给坐标"。 */
export function isCompleteEndpoint(endpoint: ParkOrderEndpoint | null | undefined): boolean {
  if (!endpoint) return false
  if (endpoint.kind === 'station') return Number.isFinite(endpoint.stationId)
  return Number.isFinite(endpoint.lng) && Number.isFinite(endpoint.lat)
}

export function endpointSummary(
  endpoint: ParkOrderEndpoint | null | undefined,
  stations: ParkStation[],
): string {
  if (!isCompleteEndpoint(endpoint)) return '--'
  if (endpoint!.kind === 'station') {
    const station = stations.find((item) => item.stationId === (endpoint as { stationId: number }).stationId)
    return station?.stationCode ?? '--'
  }
  const { lng, lat } = endpoint as { lng: number; lat: number }
  return `${lng.toFixed(5)},${lat.toFixed(5)}`
}

/** 端点 → 请求体字段。站点走 stationId，坐标走 Lng/Lat，两者互斥。 */
export function endpointPayload(
  side: 'pickup' | 'dropoff',
  endpoint: ParkOrderEndpoint | null | undefined,
): Partial<Pick<import('@/types/park').ParkOrderCreateRequest, 'pickupStationId' | 'dropoffStationId' | 'pickupLng' | 'pickupLat' | 'dropoffLng' | 'dropoffLat'>> {
  if (!isCompleteEndpoint(endpoint)) return {}
  if (endpoint!.kind === 'station') {
    return side === 'pickup'
      ? { pickupStationId: (endpoint as { stationId: number }).stationId }
      : { dropoffStationId: (endpoint as { stationId: number }).stationId }
  }
  const { lng, lat } = endpoint as { lng: number; lat: number }
  return side === 'pickup' ? { pickupLng: lng, pickupLat: lat } : { dropoffLng: lng, dropoffLat: lat }
}
