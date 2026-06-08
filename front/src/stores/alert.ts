import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { AlertHistoryItem, AlertRuleConfig, DispatchPredictionAlert, PredictiveAlert, SilenceRule } from '@/types/alert'
import { DEFAULT_ALERT_RULES } from '@/types/alert'
import {
  isInSilentPeriod,
  loadAlertHistory,
  loadAlertRules,
  playAlertTone,
  pushAlertHistory,
  requestBrowserNotifyPermission,
  saveAlertRules,
  showBrowserNotification,
} from '@/utils/alertSound'
import type { ExceptionStreamPayload } from '@/types/realtime'

export const useAlertStore = defineStore('alert', () => {
  const rules = ref<AlertRuleConfig>(loadAlertRules())
  const history = ref<AlertHistoryItem[]>(loadAlertHistory())
  const lastExceptionKey = ref<string>('')

  // ── V5-N3: 预测性告警 ──
  const predictiveAlerts = ref<PredictiveAlert[]>([])

  function handlePredictiveSocAlert(vehicleId: number, vehicleCode: string, currentSoc: number, predictedMinutes: number, slope: number) {
    let trend: PredictiveAlert['trend'] = 'stable'
    const slopePerMin = slope * 60000
    if (slopePerMin < -3) trend = 'rapid_decline'
    else if (slopePerMin < -0.5) trend = 'slight_decline'

    const existing = predictiveAlerts.value.findIndex(a => a.vehicleId === vehicleId)
    const alert: PredictiveAlert = { vehicleId, vehicleCode, currentSoc, predictedMinutes, trend }
    if (existing >= 0) {
      predictiveAlerts.value[existing] = alert
    } else {
      predictiveAlerts.value.push(alert)
    }

    // Also create a history item if not silenced
    if (!isInSilentPeriod(rules.value.silentStartHour, rules.value.silentEndHour)) {
      const item: AlertHistoryItem = {
        id: `pred-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
        title: 'SOC 预测告警',
        message: `车辆 ${vehicleCode} 预计 ${predictedMinutes} 分钟后 SOC 降至 ${currentSoc}%`,
        severity: 'HIGH',
        eventType: 'SOC_PREDICTION',
        createdAt: new Date().toISOString(),
        read: false,
      }
      history.value = pushAlertHistory(item)

      if (rules.value.soundEnabled && rules.value.levelRules.HIGH?.sound) {
        playAlertTone(false)
      }
      if (rules.value.browserNotifyEnabled && rules.value.levelRules.HIGH?.notify) {
        showBrowserNotification(item.title, item.message)
      }
    }
  }

  // ── V5-N4: 派车预测告警 ──
  const dispatchPredictionAlerts = ref<DispatchPredictionAlert[]>([])

  function handleDispatchPrediction(ratio: number, riskLevel: 'safe' | 'warning' | 'critical') {
    if (riskLevel === 'safe') {
      dispatchPredictionAlerts.value = []
      return
    }
    const message = riskLevel === 'critical'
      ? `派车资源严重不足！可用/待派比率 ${ratio.toFixed(1)}`
      : `派车资源趋紧，比率 ${ratio.toFixed(1)}`

    dispatchPredictionAlerts.value = [{ ratio, riskLevel, estimatedWaitMinutes: Math.round(Math.max(0, 1 / ratio - 1) * 10), message }]

    if (riskLevel === 'critical' && !isInSilentPeriod(rules.value.silentStartHour, rules.value.silentEndHour)) {
      const item: AlertHistoryItem = {
        id: `disp-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
        title: '派车预测告警',
        message,
        severity: riskLevel === 'critical' ? 'CRITICAL' : 'HIGH',
        eventType: 'DISPATCH_PREDICTION',
        createdAt: new Date().toISOString(),
        read: false,
      }
      history.value = pushAlertHistory(item)

      if (rules.value.soundEnabled && rules.value.levelRules.CRITICAL?.sound) {
        playAlertTone(true)
      }
      if (rules.value.browserNotifyEnabled && rules.value.levelRules.CRITICAL?.notify) {
        showBrowserNotification(item.title, item.message)
      }
    }
  }

  // ── V5-N7: 静默规则 ──
  const silenceRules = ref<SilenceRule[]>([])

  function isSilenced(vehicleId?: number | null, exceptionType?: string): boolean {
    const now = new Date()
    const currentHour = now.getHours()
    const currentMinute = now.getMinutes()
    const currentWeekDay = now.getDay()

    for (const rule of silenceRules.value) {
      if (!rule.enabled) continue
      if (rule.vehicleId != null && rule.vehicleId !== vehicleId) continue
      if (rule.exceptionType && rule.exceptionType !== exceptionType) continue
      if (rule.weekDays.length > 0 && !rule.weekDays.includes(currentWeekDay)) continue
      if (rule.startTime && rule.endTime) {
        const [startH, startM] = rule.startTime.split(':').map(Number)
        const [endH, endM] = rule.endTime.split(':').map(Number)
        const startMinutes = startH * 60 + startM
        const endMinutes = endH * 60 + endM
        const currentMinutes = currentHour * 60 + currentMinute
        if (startMinutes <= endMinutes) {
          if (currentMinutes >= startMinutes && currentMinutes < endMinutes) return true
        } else {
          if (currentMinutes >= startMinutes || currentMinutes < endMinutes) return true
        }
      }
    }
    return false
  }

  function addSilenceRule(rule: SilenceRule) {
    silenceRules.value.push(rule)
  }

  function removeSilenceRule(id: string) {
    silenceRules.value = silenceRules.value.filter(r => r.id !== id)
  }

  function updateSilenceRule(rule: SilenceRule) {
    const idx = silenceRules.value.findIndex(r => r.id === rule.id)
    if (idx >= 0) silenceRules.value[idx] = rule
  }

  function updateRules(next: AlertRuleConfig) {
    rules.value = next
    saveAlertRules(next)
  }

  async function ensureNotifyPermission() {
    if (!rules.value.browserNotifyEnabled) return
    await requestBrowserNotifyPermission()
  }

  function handleExceptionAlert(payload: ExceptionStreamPayload) {
    const key = `${payload.eventType}:${payload.businessKey}:${payload.eventTime}`
    if (key === lastExceptionKey.value) return
    lastExceptionKey.value = key

    const severity = String((payload.payload as Record<string, unknown> | undefined)?.severity || 'HIGH')
    const message = String((payload.payload as Record<string, unknown> | undefined)?.exceptionMsg || '新的调度异常')

    // V5-N7: check silence rules
    const vehicleId = (payload.payload as Record<string, unknown> | undefined)?.vehicleId as number | undefined
    if (isSilenced(vehicleId, payload.eventType)) return

    const silent = isInSilentPeriod(rules.value.silentStartHour, rules.value.silentEndHour)
    const levelRule = rules.value.levelRules[severity] || rules.value.levelRules.HIGH
    const critical = severity === 'CRITICAL'

    const item: AlertHistoryItem = {
      id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
      title: critical ? '严重异常' : '调度异常',
      message,
      severity,
      eventType: payload.eventType,
      createdAt: new Date().toISOString(),
      read: false,
    }
    history.value = pushAlertHistory(item)

    if (silent) return

    if (rules.value.soundEnabled && levelRule?.sound) {
      playAlertTone(critical && rules.value.criticalSoundEnabled)
    }
    if (rules.value.browserNotifyEnabled && levelRule?.notify) {
      showBrowserNotification(item.title, item.message)
    }
  }

  function markAllRead() {
    history.value = history.value.map((item) => ({ ...item, read: true }))
    localStorage.setItem('fsd_alert_history', JSON.stringify(history.value))
  }

  function resetRules() {
    updateRules({ ...DEFAULT_ALERT_RULES })
  }

  return {
    rules,
    history,
    predictiveAlerts,
    dispatchPredictionAlerts,
    silenceRules,
    updateRules,
    ensureNotifyPermission,
    handleExceptionAlert,
    handlePredictiveSocAlert,
    handleDispatchPrediction,
    isSilenced,
    addSilenceRule,
    removeSilenceRule,
    updateSilenceRule,
    markAllRead,
    resetRules,
  }
})