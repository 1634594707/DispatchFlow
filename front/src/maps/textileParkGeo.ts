/**
 * 叠石桥家纺产业带地理锚点（仿找家纺无人快递短驳场景）
 * 与后端 fsd.park.geo 配置保持一致
 */
export const TEXTILE_PARK_GEO = {
  scenario: 'TEXTILE_DIESHIQIAO',
  label: '南通海门 · 叠石桥国际家纺城片区',
  anchorLng: 121.06228,
  anchorLat: 31.91245,
  parkWidthPx: 1200,
  parkHeightPx: 800,
  parkWidthMeters: 2400,
  parkHeightMeters: 1600,
} as const

const METERS_PER_DEGREE_LAT = 111_320

function metersPerDegreeLng(latitudeDegrees: number) {
  return METERS_PER_DEGREE_LAT * Math.cos((latitudeDegrees * Math.PI) / 180)
}

/** 园区 schematic x/y → GCJ-02 [lng, lat] */
export function parkXYToGcj02(x: number, y: number): [number, number] {
  const cfg = TEXTILE_PARK_GEO
  const metersPerPxX = cfg.parkWidthMeters / cfg.parkWidthPx
  const metersPerPxY = cfg.parkHeightMeters / cfg.parkHeightPx
  const deltaEastMeters = (x - cfg.parkWidthPx / 2) * metersPerPxX
  const deltaNorthMeters = (cfg.parkHeightPx / 2 - y) * metersPerPxY
  const lng = cfg.anchorLng + deltaEastMeters / metersPerDegreeLng(cfg.anchorLat)
  const lat = cfg.anchorLat + deltaNorthMeters / METERS_PER_DEGREE_LAT
  return [Number(lng.toFixed(6)), Number(lat.toFixed(6))]
}

export function defaultMapCenter(): [number, number] {
  return [TEXTILE_PARK_GEO.anchorLng, TEXTILE_PARK_GEO.anchorLat]
}
