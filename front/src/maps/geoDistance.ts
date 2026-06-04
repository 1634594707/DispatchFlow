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

/** Short-haul AV demo: ~2.5 m/s ≈ 9 km/h along campus roads. */
export function formatDeliveryEta(meters: number, speedMps = 2.5): string {
  if (meters <= 0) return '--'
  const minutes = Math.max(1, Math.ceil(meters / speedMps / 60))
  return `约 ${minutes} 分钟`
}

export function formatDistance(meters: number): string {
  if (meters <= 0) return '--'
  if (meters < 1000) return `${Math.round(meters)} m`
  return `${(meters / 1000).toFixed(1)} km`
}
