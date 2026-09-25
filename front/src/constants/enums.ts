export enum OrderStatus {
  CREATED = 'CREATED',
  WAITING_DISPATCH = 'WAITING_DISPATCH',
  DISPATCHED = 'DISPATCHED',
  IN_PROGRESS = 'IN_PROGRESS',
  COMPLETED = 'COMPLETED',
  CANCELLED = 'CANCELLED',
  FAILED = 'FAILED',
}

export enum TaskStatus {
  PENDING = 'PENDING',
  ASSIGNING = 'ASSIGNING',
  ASSIGNED = 'ASSIGNED',
  EXECUTING = 'EXECUTING',
  SUCCESS = 'SUCCESS',
  FAILED = 'FAILED',
  CANCELLED = 'CANCELLED',
  MANUAL_PENDING = 'MANUAL_PENDING',
}

export enum OnlineStatus {
  ONLINE = 'ONLINE',
  OFFLINE = 'OFFLINE',
}

export enum DispatchStatus {
  IDLE = 'IDLE',
  BUSY = 'BUSY',
  UNAVAILABLE = 'UNAVAILABLE',
}

export enum ExceptionType {
  TASK_EXECUTE_FAILED = 'TASK_EXECUTE_FAILED',
  VEHICLE_OFFLINE = 'VEHICLE_OFFLINE',
  EXECUTE_TIMEOUT = 'EXECUTE_TIMEOUT',
  STATUS_REPORT_ERROR = 'STATUS_REPORT_ERROR',
  // ↓ 下面 8 个才是后端 `t_dispatch_exception_record.exception_type` 真正写库的值
  //   （该列是裸字符串、没有 Java 枚举，权威集合只能从 recordException 的调用点收）。
  //   此前这张表只有上面 4 项，于是异常任务页每一条真实异常都渲染成"未知异常类型(X)"。
  GEOFENCE_EXIT = 'GEOFENCE_EXIT',
  GEOFENCE_ENTER = 'GEOFENCE_ENTER',
  LOW_SOC = 'LOW_SOC',
  NO_VEHICLE = 'NO_VEHICLE',
  NO_MATCHING_VEHICLE = 'NO_MATCHING_VEHICLE',
  UNREACHABLE = 'UNREACHABLE',
  TASK_TIMEOUT = 'TASK_TIMEOUT',
  ZONE_PAUSED = 'ZONE_PAUSED',
}

export enum ExceptionStatus {
  OPEN = 'OPEN',
  RESOLVED = 'RESOLVED',
}
