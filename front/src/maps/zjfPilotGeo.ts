/**

 * 找家纺叠石桥短驳试点（L1）— 与 ROADMAP-V3 §1.3 / V27 迁移一致

 */

export const ZJF_PILOT_GEO = {

  scenario: 'ZJF_DIESHIQIAO_PILOT',

  label: '找家纺网 · 叠石桥短驳试点',

  anchorLng: 121.080354,

  anchorLat: 31.961977,

  parkWidthPx: 1200,

  parkHeightPx: 800,

  parkWidthMeters: 1570,

  parkHeightMeters: 470,

  pilotPolygon: [

    [121.072051, 31.959885],

    [121.088673, 31.959902],

    [121.088674, 31.964101],

    [121.072051, 31.964084],

  ] as [number, number][],

} as const



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

