import request from '@/utils/request'
import type { ApiResponse, PageResponse } from '@/types/api'
import type { OrderQueryRequest, OrderAdminListItem, OrderDetailResponse } from '@/types/order'

export function getOrderList() {
  return request.get<any, ApiResponse<OrderAdminListItem[]>>('/admin/orders')
}

export function queryOrders(data: OrderQueryRequest) {
  return request.post<any, ApiResponse<PageResponse<OrderAdminListItem>>>('/admin/orders/query', data)
}

export function getOrderDetail(orderId: number) {
  return request.get<any, ApiResponse<OrderDetailResponse>>(`/admin/orders/${orderId}`)
}
