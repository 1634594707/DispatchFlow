import { OrderStatus, TaskStatus, OnlineStatus, DispatchStatus, ExceptionType, ExceptionStatus } from './enums'

interface StatusConfig {
  label: string
  color: string
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
  [OnlineStatus.OFFLINE]: { label: '离线', color: 'error' },
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
