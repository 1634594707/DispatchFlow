const EXCEPTION_LABELS: Record<string, string> = {
  TASK_TIMEOUT: '任务超时',
  MANUAL_PENDING: '人工待处理',
  LOW_SOC: '低电量',
  VEHICLE_OFFLINE: '车辆离线',
  TASK_EXECUTE_FAILED: '任务执行失败',
  EXECUTE_TIMEOUT: '执行超时',
  STATUS_REPORT_ERROR: '状态回报异常',
  VEHICLE_GEOFENCE: '车辆越界',
  DEFAULT_BOUNDARY: '边界告警',
}

export function exceptionLabel(type?: string | null): string {
  const value = String(type || '').trim().toUpperCase()
  if (EXCEPTION_LABELS[value]) return EXCEPTION_LABELS[value]
  if (!value) return '调度异常'
  return value
    .toLowerCase()
    .split('_')
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ')
}

function normalizeMessage(value: string): string {
  return value
    .replace(/TSK[A-Z0-9_-]+/gi, '任务')
    .replace(/ORD[A-Z0-9_-]+/gi, '订单')
    .replace(/ZJF-[A-Z0-9_-]+/gi, '车辆')
    .replace(/\b\d{6,}\b/g, '#')
    .replace(/\s+/g, ' ')
    .trim()
}

export function safeAlertMessage(message?: string | null, eventType?: string | null): string {
  const raw = String(message || '').replace(/\s+/g, ' ').trim()
  const type = String(eventType || '').toUpperCase()
  if (!raw || /\?{2,}|�/.test(raw)) return `${exceptionLabel(type)}详情暂不可用`
  if (type === 'TASK_TIMEOUT' || /MANUAL_PENDING.*(30|超时)/i.test(raw)) {
    return '人工待处理任务超过 30 分钟，已进入异常队列'
  }
  if (type === 'LOW_SOC' || /SOC|电量低/i.test(raw)) return '车辆电量低于可派车阈值'
  if (type === 'VEHICLE_OFFLINE') return '车辆已离线，请检查车辆状态'
  if (type === 'VEHICLE_GEOFENCE' || /驶出围栏|越界|BOUNDARY/i.test(raw)) return '车辆驶出当前作业边界'
  return raw
}

export function alertDedupKey(message?: string | null, eventType?: string | null): string {
  return `${String(eventType || '').toUpperCase()}|${normalizeMessage(safeAlertMessage(message, eventType))}`
}

export function exceptionDedupKey(item: {
  taskId?: number | null
  exceptionType?: string | null
  exceptionMsg?: string | null
}): string {
  return `${item.taskId ?? 'unknown'}|${alertDedupKey(item.exceptionMsg, item.exceptionType)}`
}
