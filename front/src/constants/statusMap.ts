import { OrderStatus, TaskStatus, OnlineStatus, DispatchStatus, ExceptionType, ExceptionStatus } from './enums'

interface StatusConfig {
  label: string
  color: string
}

/** Maps legacy color keys to unified risk semantics for UI components. */
export type StatusSemantic = 'critical' | 'warning' | 'active' | 'normal' | 'muted'

export function statusColorToSemantic(color: string): StatusSemantic {
  const map: Record<string, StatusSemantic> = {
    error: 'critical',
    warning: 'warning',
    processing: 'active',
    cyan: 'active',
    success: 'normal',
    default: 'muted',
  }
  return map[color] || 'muted'
}

export const orderStatusMap: Record<OrderStatus, StatusConfig> = {
  [OrderStatus.CREATED]: { label: '已创建', color: 'default' },
  [OrderStatus.WAITING_DISPATCH]: { label: '待调度', color: 'processing' },
  [OrderStatus.DISPATCHED]: { label: '已调度', color: 'cyan' },
  [OrderStatus.IN_PROGRESS]: { label: '执行中', color: 'warning' },
  [OrderStatus.COMPLETED]: { label: '已完成', color: 'success' },
  [OrderStatus.CANCELLED]: { label: '已取消', color: 'default' },
  [OrderStatus.FAILED]: { label: '失败', color: 'error' },
}

export const taskStatusMap: Record<TaskStatus, StatusConfig> = {
  [TaskStatus.PENDING]: { label: '待派单', color: 'processing' },
  [TaskStatus.ASSIGNING]: { label: '派单中', color: 'processing' },
  [TaskStatus.ASSIGNED]: { label: '已派单', color: 'cyan' },
  [TaskStatus.EXECUTING]: { label: '执行中', color: 'warning' },
  [TaskStatus.SUCCESS]: { label: '已完成', color: 'success' },
  [TaskStatus.FAILED]: { label: '失败', color: 'error' },
  [TaskStatus.CANCELLED]: { label: '已取消', color: 'default' },
  [TaskStatus.MANUAL_PENDING]: { label: '人工待处理', color: 'error' },
}

export const onlineStatusMap: Record<OnlineStatus, StatusConfig> = {
  [OnlineStatus.ONLINE]: { label: '在线', color: 'success' },
  [OnlineStatus.OFFLINE]: { label: '离线', color: 'default' },
}

export const dispatchStatusMap: Record<DispatchStatus, StatusConfig> = {
  [DispatchStatus.IDLE]: { label: '空闲', color: 'success' },
  [DispatchStatus.BUSY]: { label: '忙碌', color: 'warning' },
  [DispatchStatus.UNAVAILABLE]: { label: '不可用', color: 'default' },
}

interface ExceptionTypeConfig {
  label: string
  icon: string
}

export const exceptionTypeMap: Record<ExceptionType, ExceptionTypeConfig> = {
  [ExceptionType.TASK_EXECUTE_FAILED]: { label: '任务执行失败', icon: 'CloseCircleOutlined' },
  [ExceptionType.VEHICLE_OFFLINE]: { label: '车辆离线', icon: 'DisconnectOutlined' },
  [ExceptionType.EXECUTE_TIMEOUT]: { label: '执行超时', icon: 'ClockCircleOutlined' },
  [ExceptionType.STATUS_REPORT_ERROR]: { label: '状态回传异常', icon: 'ExclamationCircleOutlined' },
}

export const exceptionStatusMap: Record<ExceptionStatus, StatusConfig> = {
  [ExceptionStatus.OPEN]: { label: '待处理', color: 'error' },
  [ExceptionStatus.RESOLVED]: { label: '已处理', color: 'success' },
}

// ── Infra: Parking Slot / Charging Pile ──────────────────
export const slotStatusMap: Record<string, StatusConfig> = {
  FREE:     { label: '空闲',   color: 'success' },
  OCCUPIED: { label: '占用',   color: 'warning' },
  RESERVED: { label: '预留',   color: 'processing' },
  CHARGING: { label: '充电中', color: 'cyan' },
  FAULT:    { label: '故障',   color: 'error' },
}

// ── Infra: Entity Active/Inactive ────────────────────────
export const infraActiveMap: Record<string, StatusConfig> = {
  ACTIVE:   { label: '启用', color: 'success' },
  INACTIVE: { label: '停用', color: 'default' },
  DISABLED: { label: '禁用', color: 'default' },
}

// ── Field Ops: Ticket ────────────────────────────────────
export const ticketStatusMap: Record<string, StatusConfig> = {
  OPEN:        { label: '待处理', color: 'error' },
  IN_PROGRESS: { label: '处理中', color: 'processing' },
  DONE:        { label: '已完成', color: 'success' },
}

// ── System: Alert Escalation ─────────────────────────────
export const alertStatusMap: Record<string, StatusConfig> = {
  pending:      { label: '待确认', color: 'warning' },
  acknowledged: { label: '已确认', color: 'processing' },
  resolved:     { label: '已解决', color: 'success' },
}

// ── System: Health ───────────────────────────────────────
export const healthStatusMap: Record<string, StatusConfig> = {
  UP:       { label: '正常', color: 'success' },
  OK:       { label: '正常', color: 'success' },
  DOWN:     { label: '异常', color: 'error' },
  DEGRADED: { label: '降级', color: 'warning' },
  WARNING:  { label: '警告', color: 'warning' },
  CRITICAL: { label: '严重', color: 'error' },
}

// ── System: Config Check ─────────────────────────────────
export const configCheckMap: Record<string, StatusConfig> = {
  ok:   { label: '通过', color: 'success' },
  warn: { label: '可选', color: 'warning' },
  fail: { label: '缺失', color: 'error' },
}

// ── User ─────────────────────────────────────────────────
export const userStatusMap: Record<string, StatusConfig> = {
  ACTIVE:   { label: '正常', color: 'success' },
  DISABLED: { label: '禁用', color: 'default' },
}

// ── Report / Execution ───────────────────────────────────
export const executionStatusMap: Record<string, StatusConfig> = {
  SUCCESS: { label: '成功', color: 'success' },
  FAILURE: { label: '失败', color: 'error' },
}

// ── Vehicle Health ───────────────────────────────────────
export const vehicleHealthMap: Record<string, StatusConfig> = {
  GOOD:     { label: '良好', color: 'success' },
  FAIR:     { label: '一般', color: 'warning' },
  POOR:     { label: '较差', color: 'error' },
  CRITICAL: { label: '严重', color: 'error' },
}

export const DISPATCH_FAIL_REASON: Record<string, string> = {
  NO_VEHICLE: '无可用车辆',
  NO_IDLE_VEHICLE: '无在线空闲车辆',
  LOW_SOC: '电量不足',
  LOW_BATTERY: '电量不足',
  UNREACHABLE: '取货点不可达',
  ROUTE_BLOCKED: '路网不可达或路段管制',
  HUB_CAPACITY_FULL: '枢纽容量已满',
  CONFLICT: '占车/占桩冲突',
}
