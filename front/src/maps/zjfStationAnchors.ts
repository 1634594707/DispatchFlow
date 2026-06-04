/**
 * 叠石桥 L1 试点站点锚点（GCJ-02）
 * 与 V30 迁移、pilot_osm_geo.json 一致；选点需距 OSM 道路 ≤30m 且不在禁行建筑块内。
 */
export type ZjfStationRole = 'pickup' | 'dropoff' | 'express' | 'idle' | 'charging'

export interface ZjfStationAnchor {
  code: string
  name: string
  role: ZjfStationRole
  lng: number
  lat: number
  remark?: string
}

export const ZJF_STATION_ANCHORS: ZjfStationAnchor[] = [
  { code: 'ZJF-PICK-01', name: '南通家纺城门市', role: 'pickup', lng: 121.074453, lat: 31.960396 },
  { code: 'ZJF-PICK-02', name: '成品展示中心门市', role: 'pickup', lng: 121.072610, lat: 31.960726 },
  { code: 'ZJF-DROP-01', name: '找家纺代发仓', role: 'dropoff', lng: 121.079762, lat: 31.963627 },
  { code: 'ZJF-DROP-02', name: '找家纺代拿仓', role: 'dropoff', lng: 121.087005, lat: 31.961780 },
  { code: 'ZJF-DROP-03', name: '西排北仓', role: 'dropoff', lng: 121.074367, lat: 31.963548 },
  { code: 'ZJF-DROP-04', name: '东排集散仓', role: 'dropoff', lng: 121.083893, lat: 31.962833 },
  { code: 'ZJF-EXPRESS-01', name: '快递接驳点', role: 'express', lng: 121.072610, lat: 31.960726 },
  { code: 'ZJF-IDLE-01', name: '车辆待命区', role: 'idle', lng: 121.080055, lat: 31.961922 },
  { code: 'ZJF-CHG-01', name: '待命区充电站', role: 'charging', lng: 121.080069, lat: 31.961850 },
  { code: 'ZJF-CHG-02', name: '代发仓充电站', role: 'charging', lng: 121.079780, lat: 31.963518 },
  { code: 'ZJF-CHG-03', name: '快递接驳充电站', role: 'charging', lng: 121.072610, lat: 31.960726 },
  { code: 'ZJF-CHG-04', name: '南排取货充电站', role: 'charging', lng: 121.074442, lat: 31.960671 },
  { code: 'ZJF-CHG-05', name: '东排快充站', role: 'charging', lng: 121.084334, lat: 31.962890 },
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
