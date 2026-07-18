/**
 * 叠石桥 L1 试点站点锚点（GCJ-02）
 * 与 V37 迁移、pilot_osm_geo.json 一致；选点需距 OSM 道路 ≤30m 且不在禁行建筑块内。
 * V37 新增：serviceHours 营业时间、avgServiceSeconds 平均服务时长、capacityLimit 承载上限
 */
export type ZjfStationRole = 'pickup' | 'dropoff' | 'express' | 'idle' | 'charging'

export interface ZjfStationAnchor {
  code: string
  name: string
  role: ZjfStationRole
  lng: number
  lat: number
  serviceHours?: string
  avgServiceSeconds?: number
  capacityLimit?: number
  remark?: string
}

export const ZJF_STATION_ANCHORS: ZjfStationAnchor[] = [
  // 取货站点（门市 · 南排核心区）
  { code: 'ZJF-PICK-01', name: '南通家纺城门市', role: 'pickup', lng: 121.074453, lat: 31.960396, serviceHours: '06:00-22:00', avgServiceSeconds: 180, capacityLimit: 50 },
  { code: 'ZJF-PICK-02', name: '成品展示中心门市', role: 'pickup', lng: 121.072610, lat: 31.960726, serviceHours: '08:00-20:00', avgServiceSeconds: 240, capacityLimit: 30 },
  // 送货站点（仓库）
  { code: 'ZJF-DROP-01', name: '找家纺代发仓', role: 'dropoff', lng: 121.079762, lat: 31.963627, serviceHours: '06:00-23:00', avgServiceSeconds: 300, capacityLimit: 200 },
  { code: 'ZJF-DROP-02', name: '找家纺代拿仓', role: 'dropoff', lng: 121.087005, lat: 31.961780, serviceHours: '07:00-22:00', avgServiceSeconds: 360, capacityLimit: 150 },
  { code: 'ZJF-DROP-03', name: '西排北仓', role: 'dropoff', lng: 121.074367, lat: 31.963548, serviceHours: '08:00-20:00', avgServiceSeconds: 240, capacityLimit: 80 },
  { code: 'ZJF-DROP-04', name: '东排集散仓', role: 'dropoff', lng: 121.083893, lat: 31.962833, serviceHours: '07:00-22:00', avgServiceSeconds: 300, capacityLimit: 120 },
  // 快递接驳点（西北角 · V37 校准）
  { code: 'ZJF-EXPRESS-01', name: '快递接驳点', role: 'express', lng: 121.073200, lat: 31.963800, serviceHours: '08:00-22:00', avgServiceSeconds: 120, capacityLimit: 500 },
  // 车辆待命区
  { code: 'ZJF-IDLE-01', name: '车辆待命区', role: 'idle', lng: 121.080055, lat: 31.961922, serviceHours: '24h', avgServiceSeconds: 0, capacityLimit: 20 },
  // 充电站（24小时服务）
  { code: 'ZJF-CHG-01', name: '待命区充电站', role: 'charging', lng: 121.080069, lat: 31.961850, serviceHours: '24h', avgServiceSeconds: 1800, capacityLimit: 4 },
  { code: 'ZJF-CHG-02', name: '代发仓充电站', role: 'charging', lng: 121.079780, lat: 31.963518, serviceHours: '24h', avgServiceSeconds: 1800, capacityLimit: 6 },
  { code: 'ZJF-CHG-03', name: '快递接驳充电站', role: 'charging', lng: 121.072610, lat: 31.963700, serviceHours: '24h', avgServiceSeconds: 3600, capacityLimit: 2 },
  { code: 'ZJF-CHG-04', name: '南排取货充电站', role: 'charging', lng: 121.074442, lat: 31.960671, serviceHours: '24h', avgServiceSeconds: 1800, capacityLimit: 4 },
  { code: 'ZJF-CHG-05', name: '东排快充站', role: 'charging', lng: 121.084334, lat: 31.962890, serviceHours: '24h', avgServiceSeconds: 1800, capacityLimit: 4 },
]

/** 道路走廊参考线（选点时优先落在此附近） */
export const ZJF_ROAD_CORRIDORS = {
  southRowLat: 31.960646,
  midRowLat: 31.961977,
  northRowLat: 31.963523,
  westColLng: 121.072682,
  midColLng: 121.07516,
  eastColLng: 121.079152,
  farEastLng: 121.088022,
} as const

/**
 * V37 五大配送分区（GCJ-02 多边形）
 * 基于叠石桥家纺城真实道路网格划分，与后端 t_park_geofence 一致
 */
export interface ZjfDeliveryZone {
  code: string
  name: string
  description: string
  polygon: [number, number][] // [lng, lat][]
  color: string
}

export const ZJF_DELIVERY_ZONES: ZjfDeliveryZone[] = [
  {
    code: 'ZJF-ZONE-CORE-SOUTH',
    name: '家纺城核心南排区',
    description: '门市取货 · 沿南排门市街',
    polygon: [
      [121.071812, 31.960126],
      [121.073405, 31.959762],
      [121.075820, 31.959683],
      [121.077914, 31.959821],
      [121.079352, 31.960235],
      [121.079588, 31.961045],
      [121.079152, 31.961698],
      [121.077213, 31.961912],
      [121.074893, 31.961856],
      [121.072610, 31.961624],
    ],
    color: '#1677ff',
  },
  {
    code: 'ZJF-ZONE-CORE-NORTH',
    name: '家纺城核心北排区',
    description: '仓库 · 沿北排仓库街',
    polygon: [
      [121.072051, 31.962157],
      [121.074521, 31.962051],
      [121.076852, 31.962214],
      [121.078765, 31.962471],
      [121.079352, 31.962968],
      [121.079028, 31.963734],
      [121.077156, 31.964025],
      [121.074621, 31.964078],
      [121.072862, 31.963921],
      [121.071812, 31.963486],
    ],
    color: '#52c41a',
  },
  {
    code: 'ZJF-ZONE-HUB',
    name: '代发仓集散区',
    description: '代发仓主枢纽 · 沿志远路',
    polygon: [
      [121.079428, 31.961823],
      [121.081125, 31.961642],
      [121.082765, 31.961882],
      [121.083872, 31.962356],
      [121.084291, 31.963112],
      [121.084052, 31.963792],
      [121.082831, 31.964061],
      [121.081012, 31.964103],
      [121.079612, 31.963847],
      [121.079152, 31.963246],
    ],
    color: '#fa8c16',
  },
  {
    code: 'ZJF-ZONE-EAST',
    name: '东排代拿仓区',
    description: '代拿仓 · 志浩面料方向',
    polygon: [
      [121.084052, 31.960156],
      [121.085621, 31.959885],
      [121.087342, 31.959912],
      [121.088673, 31.960345],
      [121.088891, 31.961228],
      [121.088432, 31.962156],
      [121.087612, 31.963112],
      [121.086125, 31.963834],
      [121.084521, 31.964078],
      [121.084052, 31.963446],
    ],
    color: '#722ed1',
  },
  {
    code: 'ZJF-ZONE-EXPRESS',
    name: '快递接驳物流区',
    description: '快递网点接驳 · 沿纺都大道',
    polygon: [
      [121.071812, 31.963343],
      [121.072682, 31.963215],
      [121.073612, 31.963312],
      [121.074521, 31.963586],
      [121.075340, 31.963952],
      [121.075621, 31.964101],
      [121.074821, 31.964201],
      [121.073405, 31.964155],
      [121.072152, 31.963921],
    ],
    color: '#eb2f96',
  },
]

/**
 * V-COORD-AUDIT：真实世界权威基准锚点（GCJ-02，与公开地理数据交叉验证）
 * 来源：腾讯地图(主市场/物流港)、poi86 同点四系统实测(步行街)、
 *       Nominatim/OSM(三星镇质心)。详见 docs/坐标基准-叠石桥家纺城.md。
 * 约束：ZJF 全部站点坐标必须落在上述 GCJ-02 框架内；禁止混入 WGS-84
 *       实测点后直接做欧氏/Haversine 距离比较（见路线审查 4.6）。
 *       新增/导入坐标须先用 scripts/coord_benchmark.py 转 GCJ-02。
 */
export const ZJF_REAL_WORLD_REFERENCE = {
  mainMarket:      { name: '叠石桥国际家纺城(主市场·大岛路88号)', lng: 121.076301, lat: 31.966722, src: '腾讯地图 GCJ-02' },
  westGate:        { name: '主市场西门(叠林路×大岛路)',          lng: 121.073272, lat: 31.967058, src: '腾讯地图 GCJ-02' },
  marketWalkingSt: { name: '叠石桥步行街实测基准点',            lng: 121.079287, lat: 31.964539, src: 'poi86 WGS-84→GCJ-02 实测' },
  logisticsPort:   { name: '深国际·综合物流港(茅珵路)',         lng: 121.101561, lat: 31.918410, src: '腾讯地图 GCJ-02' },
  townCenter:      { name: '三星镇(海门区)镇中心',             lng: 121.115222, lat: 31.966141, src: 'Nominatim WGS-84→GCJ-02' },
  chuanjiang:      { name: '川姜/志浩面料市场(双中心西南)',     lng: 121.062280, lat: 31.912450, src: '既有 ZJF_L0_COVERAGE' },
} as const
