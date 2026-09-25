/**
 * 叠石桥 L1 试点的**非几何**呈现配置（分区配色）。
 *
 * §6.4：这里原来还有两份"地图内容的副本"，都已删除：
 *   - `ZJF_STATION_ANCHORS`（9 个站点经纬度）—— 库里 `t_station` 已有 17 个未删除站点（其中 13 个 ACTIVE），
 *     副本少 `ZJF-PICK-03 / DROP-05 / DROP-06 / EXPRESS-02 / CHG-02…05`，且共有的 7 个点
 *     与库逐位相同 ⇒ 它既没提供新信息，又在把运营台路线**静默截断**（`anchorPosition()` 查不到
 *     就跳过该点）。站点几何的唯一来源改成 `getParkStations()`（`/admin/park/stations`）。
 *   - `ZJF_BASE_ANCHOR` + `isInsideZjfBase()` —— "在不在基地"改由
 *     `parkGeoMapLayers.splitVehiclesByBasePresence(vehicles, basePosition)` 承担，
 *     而基地点必须用 `basePositionFromStations()` 取 `ZJF-IDLE-01` 的坐标。
 *     **不要**改用 `t_park.anchor_lng/lat`：那是 schematic 画布锚点，与基地点差约 273 m，
 *     用它当基地会让"在场数"静默归零（§13.68 有断言钉住这一点）。
 */
/**
 * 基地在场判定的半径（米）。这是**前端渲染阈值**，后端没有同源字段，别顺手统一它
 * （§13.33 的休眠常数教训）。
 */
export const ZJF_BASE_GEO_RADIUS_METERS = 75

/**
 * V37 五大配送分区（GCJ-02 多边形）
 * 基于叠石桥家纺城真实道路网格划分，与后端 t_park_geofence 一致
 */
export interface ZjfDeliveryZone {
  code: string
  name: string
  description: string
  color: string
}

export const ZJF_DELIVERY_ZONES: ZjfDeliveryZone[] = [
  {
    code: 'ZJF-ZONE-CORE-SOUTH',
    name: '家纺城核心南排区',
    description: '门市取货 · 沿南排门市街',
    color: '#1677ff',
  },
  {
    code: 'ZJF-ZONE-CORE-NORTH',
    name: '家纺城核心北排区',
    description: '仓库 · 沿北排仓库街',
    color: '#52c41a',
  },
  {
    code: 'ZJF-ZONE-HUB',
    name: '代发仓集散区',
    description: '代发仓主枢纽 · 沿志远路',
    color: '#fa8c16',
  },
  {
    code: 'ZJF-ZONE-EAST',
    name: '东排代拿仓区',
    description: '代拿仓 · 志浩面料方向',
    color: '#722ed1',
  },
  {
    code: 'ZJF-ZONE-EXPRESS',
    name: '快递接驳物流区',
    description: '快递网点接驳 · 沿纺都大道',
    color: '#eb2f96',
  },
]

/**
 * V-COORD-AUDIT：真实世界权威基准锚点（GCJ-02，与公开地理数据交叉验证）
 * 来源：腾讯地图(主市场/物流港)、poi86 同点四系统实测(步行街)、
 *       Nominatim/OSM(三星镇质心)。基准判据的现行载体：`geo-py/fsd_geo/datum.py` 的
 *       `STORED_DATUM = "GCJ02"` 与 Java 侧 `com.fsd.common.geo.Wgs84Gcj02Converter`；
 *       决策过程见《DispatchFlow_已完成工作记录_2026-09-22》§13.40/§13.49
 *       （原 `docs/坐标基准-叠石桥家纺城.md` 已随 2026-09-22 文档收敛删除）。
 * 约束：ZJF 全部站点坐标必须落在上述 GCJ-02 框架内；禁止混入 WGS-84
 *       实测点后直接做欧氏/Haversine 距离比较（见路线审查 4.6）。
 *       新增/导入坐标须先用 scripts/coord_benchmark.py 转 GCJ-02。
 *
 * <p>这份表**没有代码消费者**，是有意保留的基准审计台账（6 个点的出处）：它不参与渲染，
 * 所以删掉 §6.4 那两份几何副本之后仍然留着。要改几何请改库，别往这里加。
 */
export const ZJF_REAL_WORLD_REFERENCE = {
  mainMarket:      { name: '叠石桥国际家纺城(主市场·大岛路88号)', lng: 121.076301, lat: 31.966722, src: '腾讯地图 GCJ-02' },
  westGate:        { name: '主市场西门(叠林路×大岛路)',          lng: 121.073272, lat: 31.967058, src: '腾讯地图 GCJ-02' },
  marketWalkingSt: { name: '叠石桥步行街实测基准点',            lng: 121.079287, lat: 31.964539, src: 'poi86 WGS-84→GCJ-02 实测' },
  logisticsPort:   { name: '深国际·综合物流港(茅珵路)',         lng: 121.101561, lat: 31.918410, src: '腾讯地图 GCJ-02' },
  townCenter:      { name: '三星镇(海门区)镇中心',             lng: 121.115222, lat: 31.966141, src: 'Nominatim WGS-84→GCJ-02' },
  chuanjiang:      { name: '川姜/志浩面料市场(双中心西南)',     lng: 121.062280, lat: 31.912450, src: '既有 ZJF_L0_COVERAGE' },
} as const
