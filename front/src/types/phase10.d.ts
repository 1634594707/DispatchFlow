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

/** V5-S3: 扩展指标 - MQ 堆积量 */
export interface MqQueueBacklog {
  queueName: string
  backlog: number
  status: 'OK' | 'WARNING' | 'CRITICAL'
}

/** V5-S3: 扩展指标 - 数据库连接池 */
export interface DbConnectionPool {
  active: number
  idle: number
  max: number
  usagePercent: number
  status: 'OK' | 'WARNING' | 'CRITICAL'
}

/** V5-S3: 扩展指标 - Redis 内存 */
export interface RedisMemory {
  usedBytes: number
  maxBytes: number
  usagePercent: number
  status: 'OK' | 'WARNING' | 'CRITICAL'
}

/** V5-S3: 扩展指标 - SSE 连接 */
export interface SseConnection {
  activeConnections: number
  status: 'OK' | 'WARNING' | 'CRITICAL'
}

/** V5-S3: 扩展指标 - API P99 延迟 */
export interface ApiP99Latency {
  currentMs: number
  p50Ms: number
  p95Ms: number
  p99Ms: number
  status: 'OK' | 'WARNING' | 'CRITICAL'
  history?: { time: string; value: number }[]
}

/** V5-S3: 详细指标响应 */
export interface DetailedMetricsResponse {
  mqBacklogs: MqQueueBacklog[]
  dbConnectionPool: DbConnectionPool
  redisMemory: RedisMemory
  sseConnections: SseConnection
  apiP99Latency: ApiP99Latency
}

/** V5-S3: 健康时间线条目 */
export interface HealthTimelineItem {
  time: string
  component: string
  status: string
  message: string
}

/** V5-S3: 健康时间线响应 */
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

export interface DigitalTwinScenarioParams {
  speed: number          // 0.5 - 2.0 (multiplier)
  orderInterval: number  // 1-20 minutes
  vehicleCount: number   // active vehicle count
}

export interface DigitalTwinAreaStats {
  vehicleCount: number
  stationCount: number
  bounds: { minX: number; minY: number; maxX: number; maxY: number }
}

export interface DigitalTwinSimulateResult {
  scenario: string
  simulationMode?: 'ENGINE' | 'ESTIMATE'
  summary: string
  estimatedMinutes: number
  recommendedVehicleCount: number
  notes: string[]
  /** V5-DT2: Extended metrics */
  dispatchEfficiency?: number    // 派车效率 %
  avgWaitTime?: number           // 平均等待时间 (minutes)
  socConsumption?: number        // SOC 消耗 %
  completedOrders?: number       // 完成订单数
}

export interface DigitalTwinScenarioCompare {
  scenario: string
  params: DigitalTwinScenarioParams
  result: DigitalTwinSimulateResult | null
}

export interface DigitalTwinComparisonResult {
  scenarioA: DigitalTwinScenarioCompare
  scenarioB: DigitalTwinScenarioCompare
  winner: 'A' | 'B' | 'TIE'
  overallRecommendation: string
}
