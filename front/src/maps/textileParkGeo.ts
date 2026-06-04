/**
 * 叠石桥家纺产业带地理锚点（找家纺无人快递短驳场景）
 * 与后端 fsd.park.geo 配置保持一致
 */
export { ZJF_PILOT_GEO as TEXTILE_PARK_GEO } from './zjfPilotGeo'
import { ZJF_PILOT_GEO } from './zjfPilotGeo'

const METERS_PER_DEGREE_LAT = 111_320

function metersPerDegreeLng(latitudeDegrees: number) {
  return METERS_PER_DEGREE_LAT * Math.cos((latitudeDegrees * Math.PI) / 180)
}

/** 园区 schematic x/y → GCJ-02 [lng, lat] */
export function parkXYToGcj02(x: number, y: number): [number, number] {
  const cfg = ZJF_PILOT_GEO
  const metersPerPxX = cfg.parkWidthMeters / cfg.parkWidthPx
  const metersPerPxY = cfg.parkHeightMeters / cfg.parkHeightPx
  const deltaEastMeters = (x - cfg.parkWidthPx / 2) * metersPerPxX
  const deltaNorthMeters = (cfg.parkHeightPx / 2 - y) * metersPerPxY
  const lng = cfg.anchorLng + deltaEastMeters / metersPerDegreeLng(cfg.anchorLat)
  const lat = cfg.anchorLat + deltaNorthMeters / METERS_PER_DEGREE_LAT
  return [Number(lng.toFixed(6)), Number(lat.toFixed(6))]
}

export function defaultMapCenter(): [number, number] {
  return [ZJF_PILOT_GEO.anchorLng, ZJF_PILOT_GEO.anchorLat]
}
