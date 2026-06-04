import type { OrderStatus } from '@/constants/enums'

export interface OrderQueryRequest {
  orderNo?: string
  externalOrderNo?: string
  status?: OrderStatus
  priority?: string
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
  remark?: string
}
