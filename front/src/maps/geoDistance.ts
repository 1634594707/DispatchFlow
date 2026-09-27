/** Haversine distance in meters between two GCJ-02 points. */
export function haversineMeters(a: [number, number], b: [number, number]): number {
  const [lng1, lat1] = a
  const [lng2, lat2] = b
  const r = 6371000
  const phi1 = (lat1 * Math.PI) / 180
  const phi2 = (lat2 * Math.PI) / 180
  const dPhi = ((lat2 - lat1) * Math.PI) / 180
  const dLambda = ((lng2 - lng1) * Math.PI) / 180
  const sinHalf =
    Math.sin(dPhi / 2) ** 2 + Math.cos(phi1) * Math.cos(phi2) * Math.sin(dLambda / 2) ** 2
  return 2 * r * Math.asin(Math.sqrt(sinHalf))
}

export function polylineLengthMeters(path: [number, number][]): number {
  if (path.length < 2) return 0
  let total = 0
  for (let i = 1; i < path.length; i += 1) {
    total += haversineMeters(path[i - 1], path[i])
  }
  return total
}

/**
 * 配送 ETA 的兜底速度：**3.66 m/s = 13.19 km/h，是实测值**。
 *
 * 来源 = 现役路网 124 条边（速度限值只有 10/15/20 三档，分布 16/79/29）按边长加权的调和平均
 * `Σlen / Σ(len/v)`，与后端 `RouteMetricsCalculator.FALLBACK_SPEED_KMH` 同一个数、同一个口径。
 * 之前这里写的是凭空的 2.5 m/s（= 9 km/h），而后端兜底是 15 km/h —— **同一个物理量在两端各写了一
 * 个常数，差 46%**，界面上的"约 X 分钟"和接口返回的 ETA 因此永远对不上。
 *
 * 注意：这里取的是**网络平均速度**，不含装卸与等待。给客户看的承诺如果要比平均值保守，
 * 应当再显式加一个"承诺余量"参数，而不是把速度常数调慢了当余量用（后者无人知道是余量）。
 */
export const MEASURED_NETWORK_SPEED_MPS = 3.66

export function formatDeliveryEta(meters: number, speedMps = MEASURED_NETWORK_SPEED_MPS): string {
  if (meters <= 0) return '--'
  const minutes = Math.max(1, Math.ceil(meters / speedMps / 60))
  return `约 ${minutes} 分钟`
}

export function formatDistance(meters: number): string {
  if (meters <= 0) return '--'
  if (meters < 1000) return `${Math.round(meters)} m`
  return `${(meters / 1000).toFixed(1)} km`
}

/**
 * 射线法：点是否落在环内（GCJ-02 经纬度平面上判，园区尺度 ~8 km 内地球曲率可忽略）。
 *
 * 边界上的点算"外"—— 这不是精度问题，是刻意的：贴着围栏线的点本来就该被后端拒，
 * 前端把它当范围外，用户得到的反馈与提交结果一致。
 */
export function pointInRing(point: [number, number], ring: [number, number][]): boolean {
  if (ring.length < 3) return false
  const [x, y] = point
  let inside = false
  for (let i = 0, j = ring.length - 1; i < ring.length; j = i++) {
    const [xi, yi] = ring[i]
    const [xj, yj] = ring[j]
    if (yi > y !== yj > y && x < ((xj - xi) * (y - yi)) / (yj - yi) + xi) inside = !inside
  }
  return inside
}
