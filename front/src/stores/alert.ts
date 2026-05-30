import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { AlertHistoryItem, AlertRuleConfig } from '@/types/alert'
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
    updateRules,
    ensureNotifyPermission,
    handleExceptionAlert,
    markAllRead,
    resetRules,
  }
})
