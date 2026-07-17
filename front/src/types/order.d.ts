import type { OrderStatus } from '@/constants/enums'

/** 订单配送区域：地理配送 / 园区内部 */
export type OrderDeliveryZone = 'GEO_DELIVERY' | 'SCHEMATIC'

export interface OrderQueryRequest {
  orderNo?: string
  externalOrderNo?: string
  status?: OrderStatus
  priority?: string
  deliveryZone?: OrderDeliveryZone
  parkId?: number
  pageNo: number
  pageSize: number
}

export interface OrderAdminListItem {
  orderId: number
  orderNo: string
  externalOrderNo: string
  status: OrderStatus
  priority: string
  deliveryZone?: OrderDeliveryZone
  weight?: number | null
  dispatchTaskId: number | null
  createdAt: string
  updatedAt: string
}

export interface OrderDetailResponse {
  orderId: number
  orderNo: string
  externalOrderNo: string
  sourceType: string
  bizType: string
  pickupPointId: number
  dropoffPointId: number
  pickupPointName?: string | null
  pickupStationCode?: string | null
  dropoffPointName?: string | null
  dropoffStationCode?: string | null
  vehicleId?: number | null
  vehicleCode?: string | null
  runtimeStage?: string | null
  priority: string
  status: OrderStatus
  deliveryZone?: OrderDeliveryZone
  weight?: number | null
  estimatedArrivalTime?: string | null
  dispatchTaskId: number | null
  remark: string | null
  createdAt: string
  updatedAt: string
}

export interface CreateOrderForm {
  externalOrderNo?: string
  bizType: string
  pickupPointId: number
  dropoffPointId: number
  priority: string
  deliveryZone?: OrderDeliveryZone
  weight?: number
  remark?: string
}
