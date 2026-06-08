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

export interface MqQueueBacklog {
  queueName: string
  backlog: number
  status: string
}

export interface DbConnectionPool {
  active: number
  idle: number
  max: number
  usagePercent: number
  status: string
}

export interface RedisMemory {
  usedBytes: number
  maxBytes: number
  usagePercent: number
  status: string
}

export interface SseConnection {
  activeConnections: number
  status: string
}

export interface ApiLatencyHistoryPoint {
  time: string
  value: number
}

export interface ApiP99Latency {
  currentMs: number
  p50Ms: number
  p95Ms: number
  p99Ms: number
  status: string
  history: ApiLatencyHistoryPoint[]
}

export interface DetailedMetricsResponse {
  mqBacklogs: MqQueueBacklog[]
  dbConnectionPool: DbConnectionPool
  redisMemory: RedisMemory
  sseConnections: SseConnection
  apiP99Latency: ApiP99Latency
}

export interface HealthTimelineItem {
  component: string
  status: string
  message: string
  time: string
}

export interface HealthTimelineResponse {
  items: HealthTimelineItem[]
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
  simulationMode?: 'ENGINE' | 'ESTIMATE'
  summary: string
  estimatedMinutes: number
  recommendedVehicleCount: number
  notes: string[]
}
