export interface SystemComponentHealth {
  name: string
  status: string
  message: string
  details?: Record<string, unknown>
}

export interface SystemHealthResponse {
  overallStatus: string
  checkedAt: string
  components: SystemComponentHealth[]
}

export interface GlobalSearchItem {
  type: 'ORDER' | 'TASK' | 'VEHICLE' | string
  id: number
  code: string
  title: string
  subtitle?: string
  routePath: string
}

export interface GlobalSearchResponse {
  keyword: string
  items: GlobalSearchItem[]
}

export interface AssistantAction {
  actionType: string
  label: string
  payload?: Record<string, unknown>
}

export interface AssistantResponse {
  intent: string
  reply: string
  suggestions: string[]
  actions: AssistantAction[]
}

export interface DigitalTwinSnapshot {
  layout: import('@/types/park').ParkLayout | null
  vehicles: import('@/types/park').ParkVehicleSnapshot[]
  pendingTaskCount: number
  openExceptionCount: number
  idleVehicleCount: number
  lowBatteryVehicleCount: number
}

export interface DigitalTwinSimulateResult {
  scenario: string
  summary: string
  estimatedMinutes: number
  recommendedVehicleCount: number
  notes: string[]
}
