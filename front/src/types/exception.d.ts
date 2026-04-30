import type { ExceptionType, ExceptionStatus } from '@/constants/enums'

export interface ExceptionQueryRequest {
  exceptionType?: ExceptionType
  exceptionStatus?: ExceptionStatus
  taskNo?: string
  orderId?: number
  vehicleId?: number
  pageNo: number
  pageSize: number
}

export interface ExceptionAdminListItem {
  id: number
  taskId: number
  orderId: number
  vehicleId: number | null
  exceptionType: ExceptionType
  exceptionStatus: ExceptionStatus
  exceptionMsg: string
  occurTime: string
  resolvedTime: string | null
  resolverId: string | null
  resolveRemark: string | null
  createdAt: string
  updatedAt: string
}

export interface ResolveExceptionRequest {
  resolverId: string
  resolverName: string
  action: string
  remark: string
}
