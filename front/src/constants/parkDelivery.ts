import { enumLabel } from './statusMap'

export const parkDeliveryStageLabelMap: Record<string, string> = {
  PENDING_ASSIGNMENT: '待分配',
  PENDING: '待接单',
  ASSIGNED: '已派单',
  RETURNING: '返程中',
  WAITING_DISPATCH: '已受理',
  ASSIGNING: '派车中',
  DISPATCHED: '已派车',
  IN_PROGRESS: '配送中',
  HEADING_TO_PICKUP: '前往取货',
  TO_PICKUP: '前往取货',
  LOADING: '装货中',
  HEADING_TO_DROPOFF: '配送中',
  TO_DROPOFF: '配送中',
  UNLOADING: '卸货中',
  COMPLETED: '已完成',
  FAILED: '失败',
  MANUAL_PENDING: '人工介入',
  EMERGENCY_PARKING: '危急电量驻车',
  WAIT_CHARGING: '等待充电位',
  TO_CHARGING: '前往充电',
  CHARGING: '充电中',
}

// ⛔ 设施形态 v2 之后，"常用/典型线路"这类**写死的站点对**全部失效：
//   `ZJF-PICK-*` / `ZJF-DROP-*` / `ZJF-EXPRESS-*` 已整批 INACTIVE，`A1..B4` 在 `t_station` 里
//   压根不存在（实测 0 行）⇒ 这些预设点了只会撞上"演示站点尚未加载"。
//   现在货的起点唯一是总仓库 `FSD-HUB-01`，终点是用户在地图上选的坐标 ⇒ 没有"固定线路"可言。
//   数据驱动的默认填充留在 `syncDefaultOrderStations()`（读活站表，不读常数）。

export function parkDeliveryStageLabel(stage?: string | null) {
  return enumLabel(parkDeliveryStageLabelMap, stage, '阶段')
}

export function buildGeoTrackingLink(orderId?: number | null, vehicleId?: number | null) {
  const query: Record<string, string> = { mode: 'geo' }
  if (orderId) query.orderId = String(orderId)
  if (vehicleId) query.vehicleId = String(vehicleId)
  return { path: '/vehicle-tracking', query }
}
