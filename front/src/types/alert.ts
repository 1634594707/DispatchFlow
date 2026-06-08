export interface AlertRuleConfig {
  soundEnabled: boolean
  soundVolume: number
  browserNotifyEnabled: boolean
  criticalSoundEnabled: boolean
  silentStartHour: number | null
  silentEndHour: number | null
  levelRules: Record<string, { sound: boolean; notify: boolean }>
}

export interface AlertHistoryItem {
  id: string
  title: string
  message: string
  severity: string
  eventType: string
  createdAt: string
  read: boolean
}

// ── V5-N2: SMS 通知配置 ──
export interface SmsNotificationConfig {
  enabled: boolean
  phoneNumbers: string[]
  severityThreshold: 'CRITICAL' | 'HIGH+' | 'ALL'
  eventTypes: string[]
}

export const DEFAULT_SMS_CONFIG: SmsNotificationConfig = {
  enabled: false,
  phoneNumbers: [],
  severityThreshold: 'CRITICAL',
  eventTypes: ['VEHICLE_OFFLINE', 'CHARGING_PILE_FAULT', 'CRITICAL_SOC'],
}

export interface SmsNotificationHistoryItem {
  id: string
  phone: string
  message: string
  status: 'success' | 'failed'
  sentAt: string
}

// ── V5-N6: 告警升级规则 ──
export interface EscalationRule {
  id: string
  severity: string
  timeoutMinutes: number
  escalateToRole: string
  notifyMethod: 'sound' | 'browser' | 'sms'
}

export interface EscalationRecord {
  id: string
  exceptionId: number
  escalatedAt: string
  fromSeverity: string
  escalatedTo: string
  status: 'pending' | 'acknowledged' | 'resolved'
}

export const DEFAULT_ESCALATION_RULES: EscalationRule[] = [
  { id: '1', severity: 'CRITICAL', timeoutMinutes: 15, escalateToRole: 'ADMIN', notifyMethod: 'sms' },
  { id: '2', severity: 'HIGH', timeoutMinutes: 30, escalateToRole: 'ADMIN', notifyMethod: 'browser' },
  { id: '3', severity: 'MEDIUM', timeoutMinutes: 60, escalateToRole: 'OPERATOR', notifyMethod: 'sound' },
]

// ── V5-N7: 告警静默规则 ──
export interface SilenceRule {
  id: string
  enabled: boolean
  vehicleId?: number
  vehicleCode?: string
  exceptionType?: string
  startTime?: string  // HH:mm
  endTime?: string    // HH:mm
  weekDays: number[]   // 0=Sun, 1=Mon, ..., 6=Sat
  note: string
}

export interface PredictiveAlert {
  vehicleId: number
  vehicleCode: string
  currentSoc: number
  predictedMinutes: number
  trend: 'stable' | 'slight_decline' | 'rapid_decline'
}

export interface DispatchPredictionAlert {
  ratio: number
  riskLevel: 'safe' | 'warning' | 'critical'
  estimatedWaitMinutes: number
  message: string
}

export const DEFAULT_ALERT_RULES: AlertRuleConfig = {
  soundEnabled: true,
  soundVolume: 0.6,
  browserNotifyEnabled: true,
  criticalSoundEnabled: true,
  silentStartHour: null,
  silentEndHour: null,
  levelRules: {
    CRITICAL: { sound: true, notify: true },
    HIGH: { sound: true, notify: true },
    MEDIUM: { sound: true, notify: false },
    LOW: { sound: false, notify: false },
  },
}
