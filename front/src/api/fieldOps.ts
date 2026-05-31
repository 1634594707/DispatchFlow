import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'

export interface FieldOpsTicket {
  id: number
  exceptionId: number
  assigneeUserId: number
  assigneeName?: string
  status: string
  notes?: string
  exceptionType?: string
  exceptionMsg?: string
  createdAt?: string
}

export function assignFieldOps(exceptionId: number, assigneeUserId: number, notes?: string) {
  return request.post<any, ApiResponse<FieldOpsTicket>>(
    `/admin/field-ops/exceptions/${exceptionId}/assign`,
    { assigneeUserId, notes },
  )
}

export function fetchFieldOpsTickets(params?: { assigneeUserId?: number; status?: string }) {
  return request.get<any, ApiResponse<FieldOpsTicket[]>>('/admin/field-ops/tickets', { params })
}

export function updateFieldOpsTicketStatus(ticketId: number, status: string, notes?: string) {
  return request.put<any, ApiResponse<FieldOpsTicket>>(`/admin/field-ops/tickets/${ticketId}/status`, {
    status,
    notes,
  })
}
