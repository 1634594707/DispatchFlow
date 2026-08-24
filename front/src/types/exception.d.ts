import type { ExceptionType, ExceptionStatus, TaskStatus } from '@/constants/enums'

export interface ExceptionQueryRequest {
  exceptionType?: ExceptionType
  exceptionStatus?: ExceptionStatus
  taskNo?: string
  orderId?: number
  vehicleId?: number
  parkId?: number
  taskStatus?: TaskStatus
  onlyManualPendingTask?: boolean
  pageNo: number
  pageSize: number
}

export interface ExceptionAdminListItem {
  id: number
  taskId: number
  taskNo?: string | null
  taskStatus?: TaskStatus | null
  taskFailReasonCode?: string | null
  taskFailReasonMsg?: string | null
  orderId: number
  vehicleId: number | null
  exceptionType: ExceptionType
  exceptionStatus: ExceptionStatus
  exceptionMsg: string
  occurTime: string
  resolvedTime: string | null
  resolverId: string | null
  resolveRemark: string | null
  aggCount?: number
  createdAt: string
  updatedAt: string
}

export type ResolveExceptionAction =
  | 'REASSIGN'
  | 'MARK_FAILED'
  | 'CLOSE'
  | 'VEHICLE_OFFLINE'

export interface ResolveExceptionRequest {
  resolverId: string
  resolverName: string
  action: ResolveExceptionAction
  remark: string
  vehicleId?: number
}
