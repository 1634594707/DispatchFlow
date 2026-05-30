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
