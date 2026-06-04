import type { TaskStatus } from '@/constants/enums'

export interface TaskQueryRequest {
  taskNo?: string
  orderId?: number
  vehicleId?: number
  parkId?: number
  status?: TaskStatus
  manualFlag?: boolean
  withOpenExceptionOnly?: boolean
  pageNo: number
  pageSize: number
}

export interface OpenExceptionBrief {
  exceptionId: number
  exceptionType: string
  exceptionMsg: string
  severity: string
  exceptionStatus: string
  occurTime: string
}

export interface TaskAdminListItem {
  taskId: number
  taskNo: string
  orderId: number
  vehicleId: number | null
  status: TaskStatus
  failReasonCode: string | null
  failReasonMsg: string | null
  createdAt: string
  updatedAt: string
  openExceptionCount?: number
  primaryOpenException?: OpenExceptionBrief | null
  openExceptions?: OpenExceptionBrief[]
  orderPriority?: string
  waitMinutes?: number
  routeId?: number
  routeCode?: string
  routeName?: string
}

export interface TaskDetailResponse {
  taskId: number
  taskNo: string
  orderId: number
  pickupStationCode?: string | null
  pickupPointName?: string | null
  dropoffStationCode?: string | null
  dropoffPointName?: string | null
  vehicleId: number | null
  dispatchType: string
  status: TaskStatus
  failReasonCode: string | null
  failReasonMsg: string | null
  assignTime: string | null
  startTime: string | null
  finishTime: string | null
  manualFlag: number
  retryCount: number
  remark: string | null
  createdAt: string
  updatedAt: string
  openExceptionCount?: number
  openExceptions?: OpenExceptionBrief[]
}

export interface DispatchForm {
  vehicleId: number
  remark?: string
}

export interface ReassignForm {
  currentVehicleId: number
  newVehicleId: number
  reason: string
}
