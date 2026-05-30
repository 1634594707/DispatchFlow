import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'
import type {
  DispatchStrategyProfile,
  DispatchStrategyUpsertPayload,
  StrategyChangeLog,
} from '@/types/dispatchStrategy'

export function fetchStrategyProfiles() {
  return request.get<any, ApiResponse<DispatchStrategyProfile[]>>('/admin/dispatch/strategy/profiles')
}

export function fetchStrategyChangeLogs() {
  return request.get<any, ApiResponse<StrategyChangeLog[]>>('/admin/dispatch/strategy/change-logs')
}

export function createStrategyProfile(payload: DispatchStrategyUpsertPayload) {
  return request.post<any, ApiResponse<DispatchStrategyProfile>>('/admin/dispatch/strategy/profiles', payload)
}

export function updateStrategyProfile(id: number, payload: DispatchStrategyUpsertPayload) {
  return request.put<any, ApiResponse<DispatchStrategyProfile>>(`/admin/dispatch/strategy/profiles/${id}`, payload)
}

export function activateStrategyProfile(id: number) {
  return request.post<any, ApiResponse<void>>(`/admin/dispatch/strategy/profiles/${id}/activate`)
}
