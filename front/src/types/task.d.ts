import type { TaskStatus } from '@/constants/enums'

export interface TaskQueryRequest {
  taskNo?: string
  orderId?: number
  vehicleId?: number
  status?: TaskStatus
  manualFlag?: boolean
  pageNo: number
  pageSize: number
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
}

export interface TaskDetailResponse {
  taskId: number
  taskNo: string
  orderId: number
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
