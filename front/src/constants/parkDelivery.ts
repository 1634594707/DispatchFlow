export const parkDeliveryStageLabelMap: Record<string, string> = {
  PENDING_ASSIGNMENT: '待分配',
  WAITING_DISPATCH: '待派车',
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

export const parkDeliveryDemoRoutes = [
  { label: '门市 A → 代发仓', pickupCode: 'ZJF-PICK-01', dropoffCode: 'ZJF-DROP-01' },
  { label: '门市 B → 代发仓', pickupCode: 'ZJF-PICK-02', dropoffCode: 'ZJF-DROP-01' },
  { label: '代拿仓 → 代发仓', pickupCode: 'ZJF-DROP-02', dropoffCode: 'ZJF-DROP-01' },
  { label: '代发仓 → 快递接驳', pickupCode: 'ZJF-DROP-01', dropoffCode: 'ZJF-EXPRESS-01' },
  { label: '门市 A → 西排北仓', pickupCode: 'ZJF-PICK-01', dropoffCode: 'ZJF-DROP-03' },
  { label: '门市 A → 东排集散仓', pickupCode: 'ZJF-PICK-01', dropoffCode: 'ZJF-DROP-04' },
  { label: '西排北仓 → 代发仓', pickupCode: 'ZJF-DROP-03', dropoffCode: 'ZJF-DROP-01' },
] as const

export const parkSchematicDemoRoutes = [
  { label: 'A1 → B1', pickupCode: 'A1', dropoffCode: 'B1' },
  { label: 'A2 → B2', pickupCode: 'A2', dropoffCode: 'B2' },
  { label: 'A3 → B3', pickupCode: 'A3', dropoffCode: 'B3' },
  { label: 'A4 → B4', pickupCode: 'A4', dropoffCode: 'B4' },
  { label: 'A1 → B4', pickupCode: 'A1', dropoffCode: 'B4' },
  { label: 'A4 → B1', pickupCode: 'A4', dropoffCode: 'B1' },
] as const

export type MobileOrderMode = 'geo' | 'schematic'

export const MOBILE_ORDER_MODE_KEY = 'fsd_mobile_order_mode'

export function loadMobileOrderMode(): MobileOrderMode {
  const stored = localStorage.getItem(MOBILE_ORDER_MODE_KEY)
  return stored === 'schematic' ? 'schematic' : 'geo'
}

export function persistMobileOrderMode(mode: MobileOrderMode) {
  localStorage.setItem(MOBILE_ORDER_MODE_KEY, mode)
}

export function parkDeliveryStageLabel(stage?: string | null) {
  if (!stage) return '--'
  return parkDeliveryStageLabelMap[stage] || stage
}

export function buildGeoTrackingLink(orderId?: number | null, vehicleId?: number | null) {
  const query: Record<string, string> = { mode: 'geo' }
  if (orderId) query.orderId = String(orderId)
  if (vehicleId) query.vehicleId = String(vehicleId)
  return { path: '/vehicle-tracking', query }
}
