/**

 * 找家纺叠石桥短驳试点（L1）— 与 ROADMAP-V3 §1.3 / V27 迁移一致
 *
 * 锚点校准（V38）：使用 3 个已知站点真实 GPS 反算最优锚点：
 *   ZJF-PICK-01 (121.074453, 31.960396)、ZJF-DROP-01 (121.079762, 31.963627)、
 *   ZJF-DROP-02 (121.087005, 31.961780)
 * 反算结果 anchorLng=121.080354, anchorLat=31.961977，与 V27 一致。
 * 全部 13 个已知站点转换误差 < 20 米（验收通过）。
 */

import { ZJF_DELIVERY_ZONES } from './zjfStationAnchors'

export const ZJF_PILOT_GEO = {

  scenario: 'ZJF_DIESHIQIAO_PILOT',

  label: '找家纺网 · 叠石桥短驳试点',

  anchorLng: 121.080354,

  anchorLat: 31.961977,

  parkWidthPx: 1200,

  parkHeightPx: 800,

  parkWidthMeters: 1570,

  parkHeightMeters: 470,

  /**
   * @deprecated Phase 3：单一大矩形已弃用，改用 pilotZonePolygons（5 个分区多边形）。
   * 保留此字段仅为向后兼容（GeofenceList 的"旧版矩形"按钮等）。
   */
  pilotPolygon: [

    [121.072051, 31.959885],

    [121.088673, 31.959902],

    [121.088674, 31.964101],

    [121.072051, 31.964084],

  ] as [number, number][],

} as const

/**
 * Phase 3：5 个配送分区多边形（替换单一大矩形）。
 * 与后端 t_park_geofence 中 ZJF-ZONE-* 记录一致（V37 迁移）。
 * 每个分区有不同的颜色，站点按所属分区着色。
 */
export const PILOT_ZONE_POLYGONS = ZJF_DELIVERY_ZONES.map((zone) => ({

  id: zone.code,

  name: zone.name,

  path: zone.polygon,

  strokeColor: zone.color,

  fillColor: zone.color + '20', // 12.5% opacity hex suffix

  zIndex: 10,

}))



/** L0 产业带图例：双中心 20km（不参与派单） */

export const ZJF_L0_COVERAGE = {

  chuanjiang: { center: [121.06228, 31.91245] as [number, number], radiusMeters: 20_000 },

  dieshiqiao: { center: [121.080354, 31.961977] as [number, number], radiusMeters: 20_000 },

} as const



export const ZJF_FLEET_STATS = {

  fleetSize: 264,

  servicePoints: 3485,

  routes: 3066,

  avgTripsPerDay: 7.9,

  hubDailyThroughput: 200_000,

} as const

