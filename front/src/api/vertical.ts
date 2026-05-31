import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'

export interface RouteStation {
  stationId: number
  stationCode?: string
  stationName?: string
  stationType?: string
  sequenceNo: number
}

export interface DispatchRoute {
  id: number
  parkId: number
  routeCode: string
  routeName: string
  status: string
  serviceStartTime?: string
  serviceEndTime?: string
  requiredVehicleType?: string
  maxConcurrentTasks?: number
  activeTaskCount?: number
  stations?: RouteStation[]
  remark?: string
}

export interface RouteUpsertPayload {
  parkId: number
  routeCode: string
  routeName: string
  status?: string
  serviceStartTime?: string
  serviceEndTime?: string
  requiredVehicleType?: string
  maxConcurrentTasks?: number
  stationIds: number[]
  remark?: string
}

export interface PeakModeState {
  parkId: number
  mode: string
  templateCode: string
  scheduleCron?: string
  scheduleEndCron?: string
  enabledAt?: string
  updatedAt?: string
}

export interface HubStationStatus {
  stationId: number
  stationCode: string
  stationName: string
  stationType: string
  capacityLimit?: number
  occupancy: number
  full: boolean
}

export interface HubQueuedTask {
  taskId: number
  taskNo: string
  orderId: number
  status: string
  hubStationId: number
  hubStationName: string
  suggestion: string
}

export interface HubOverview {
  hubs: HubStationStatus[]
  queuedTasks: HubQueuedTask[]
}

export interface AutomationRule {
  id: number
  parkId: number
  ruleName: string
  conditionType: string
  conditionValue: string
  actionType: string
  actionParamsJson?: string
  enabled: boolean
  updatedAt?: string
}

export function fetchRoutes(parkId?: number) {
  return request.get<any, ApiResponse<DispatchRoute[]>>('/admin/vertical/routes', { params: { parkId } })
}

export function createRoute(payload: RouteUpsertPayload) {
  return request.post<any, ApiResponse<DispatchRoute>>('/admin/vertical/routes', payload)
}

export function updateRoute(routeId: number, payload: RouteUpsertPayload) {
  return request.put<any, ApiResponse<DispatchRoute>>(`/admin/vertical/routes/${routeId}`, payload)
}

export function toggleRouteStatus(routeId: number) {
  return request.post<any, ApiResponse<DispatchRoute>>(`/admin/vertical/routes/${routeId}/toggle-status`)
}

export function fetchPeakMode(parkId: number) {
  return request.get<any, ApiResponse<PeakModeState>>('/admin/vertical/peak-mode', { params: { parkId } })
}

export function updatePeakMode(payload: {
  parkId: number
  mode: string
  templateCode?: string
  scheduleCron?: string
  scheduleEndCron?: string
}) {
  return request.put<any, ApiResponse<PeakModeState>>('/admin/vertical/peak-mode', payload)
}

export function fetchHubOverview(parkId?: number) {
  return request.get<any, ApiResponse<HubOverview>>('/admin/vertical/hub-overview', { params: { parkId } })
}

export interface OpsSnapshot {
  lowBatteryClusters: { gridKey: string; vehicleCount: number; centerX: number; centerY: number; minSoc: number }[]
  offlineVehicles: { vehicleId: number; vehicleCode: string; soc?: number; offlineMinutes: number }[]
  hubQueuedTasks: HubQueuedTask[]
}

export function fetchOpsSnapshot(parkId?: number) {
  return request.get<any, ApiResponse<OpsSnapshot>>('/admin/vertical/ops-snapshot', { params: { parkId } })
}

export function fetchAutomationRules(parkId?: number) {
  return request.get<any, ApiResponse<AutomationRule[]>>('/admin/vertical/automation-rules', { params: { parkId } })
}

export function createAutomationRule(payload: Omit<AutomationRule, 'id' | 'enabled' | 'updatedAt'> & { enabled?: boolean }) {
  return request.post<any, ApiResponse<AutomationRule>>('/admin/vertical/automation-rules', payload)
}

export function updateAutomationRule(ruleId: number, payload: Omit<AutomationRule, 'id' | 'updatedAt'>) {
  return request.put<any, ApiResponse<AutomationRule>>(`/admin/vertical/automation-rules/${ruleId}`, payload)
}

export function deleteAutomationRule(ruleId: number) {
  return request.delete<any, ApiResponse<void>>(`/admin/vertical/automation-rules/${ruleId}`)
}

export function toggleAutomationRule(ruleId: number) {
  return request.post<any, ApiResponse<AutomationRule>>(`/admin/vertical/automation-rules/${ruleId}/toggle`)
}

export interface AutomationRuleAudit {
  id: number
  ruleId: number
  action: string
  operator?: string
  detail?: string
  createdAt?: string
}

export function fetchAutomationRuleAudit(ruleId: number) {
  return request.get<any, ApiResponse<AutomationRuleAudit[]>>(`/admin/vertical/automation-rules/${ruleId}/audit`)
}
