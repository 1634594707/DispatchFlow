/**

 * 找家纺叠石桥短驳试点 — 示意画布参数（**前端只是兜底副本**）
 *
 * 真相顺序：`useParkMetadata` 优先读后端园区元数据，读不到才用下面这组常量。
 * 数值出处：`scripts/geo/osm_to_road_graph.py --params-out`（对 `data/map.corridor.osm`
 * 在走廊框内做 fit_canvas 反解），与 `application.yml` 的 `fsd.park.geo.*`、
 * `back/sql/seed/zjf_road_network.sql` 的 `coord_x/coord_y`、`t_park` 行**同源同值**。
 * 产物记录见 `docs/park_transform_params_2026-09-23.txt`。
 *
 * ⚠ 改画布必须五处一起改（装载器 / application.yml / 本文件 / t_park / 回归夹具），
 *   少改一处 = 像素与经纬度两套映射并存，站点吸附与 schematic 渲染会各自失真。
 *   历史教训：V27/V38 时代这里曾长期挂着旧锚点 121.080354/31.961977。
 */


export const ZJF_PILOT_GEO = {

  scenario: 'ZJF_DIESHIQIAO_PILOT',

  label: '找家纺网 · 叠石桥短驳试点',

  anchorLng: 121.0932364,

  anchorLat: 31.9373442,

  parkWidthPx: 1600,

  parkHeightPx: 1854,

  parkWidthMeters: 7957.7,

  parkHeightMeters: 9221.0,

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




/** L0 产业带图例：双中心 20km（不参与派单） */

export const ZJF_L0_COVERAGE = {

  chuanjiang: { center: [121.06228, 31.91245] as [number, number], radiusMeters: 20_000 },

  dieshiqiao: { center: [121.080354, 31.961977] as [number, number], radiusMeters: 20_000 },

} as const

