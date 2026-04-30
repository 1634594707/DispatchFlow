import request from '@/utils/request'
import type { ApiResponse, PageResponse } from '@/types/api'
import type { ExceptionQueryRequest, ExceptionAdminListItem, ResolveExceptionRequest } from '@/types/exception'

export function getExceptionList() {
  return request.get<any, ApiResponse<ExceptionAdminListItem[]>>('/admin/exceptions')
}

export function queryExceptions(data: ExceptionQueryRequest) {
  return request.post<any, ApiResponse<PageResponse<ExceptionAdminListItem>>>('/admin/exceptions/query', data)
}

export function resolveException(exceptionId: number, data: ResolveExceptionRequest) {
  return request.post<any, ApiResponse<null>>(`/admin/exceptions/${exceptionId}/resolve`, data)
}
