import request from '@/utils/request'
import type { ApiResponse, PageResponse } from '@/types/api'
import type { OrderQueryRequest, OrderAdminListItem, OrderDetailResponse } from '@/types/order'

export function getOrderList(parkId?: number) {
  return request.get<any, ApiResponse<OrderAdminListItem[]>>('/admin/orders', { params: { parkId } })
}

export function queryOrders(data: OrderQueryRequest) {
  return request.post<any, ApiResponse<PageResponse<OrderAdminListItem>>>('/admin/orders/query', data)
}

export function getOrderDetail(orderId: number, parkId?: number) {
  return request.get<any, ApiResponse<OrderDetailResponse>>(`/admin/orders/${orderId}`, {
    params: parkId != null ? { parkId } : undefined,
  })
}

export function cancelOrder(orderId: number, remark?: string) {
  return request.post<any, ApiResponse<null>>(`/admin/orders/${orderId}/cancel`, { remark })
}
