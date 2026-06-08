import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'
import type { EscalationRule, SmsNotificationConfig, SmsNotificationHistoryItem, SilenceRule } from '@/types/alert'

export function fetchAlertSettings() {
  return request.get<any, ApiResponse<{ rulesJson: string }>>('/admin/alert-settings')
}

export function saveAlertSettings(rulesJson: string) {
  return request.put<any, ApiResponse<{ rulesJson: string }>>('/admin/alert-settings', { rulesJson })
}

// ── V5-N2: SMS 通知 ──
export async function fetchSmsConfig(): Promise<{ data: SmsNotificationConfig }> {
  const res = await request.get<any, ApiResponse<{ smsConfig: string }>>('/admin/alert-sms-config')
  return { data: JSON.parse(res.data.smsConfig) }
}

export async function saveSmsConfig(data: SmsNotificationConfig): Promise<void> {
  await request.put('/admin/alert-sms-config', { smsConfig: JSON.stringify(data) })
}

export async function fetchSmsHistory(): Promise<{ data: SmsNotificationHistoryItem[] }> {
  const res = await request.get<any, ApiResponse<{ history: string }>>('/admin/alert-sms-history')
  return { data: JSON.parse(res.data.history) }
}

export async function testSmsNotification(phone: string): Promise<boolean> {
  await request.post('/admin/alert-sms-test', { phone })
  return true
}

// ── V5-N6: 告警升级 ──
export async function fetchEscalationRules(): Promise<{ data: EscalationRule[] }> {
  const res = await request.get<any, ApiResponse<{ rules: string }>>('/admin/alert-escalation-rules')
  return { data: JSON.parse(res.data.rules) }
}

export async function saveEscalationRules(rules: EscalationRule[]): Promise<void> {
  await request.put('/admin/alert-escalation-rules', { rules: JSON.stringify(rules) })
}

export async function fetchEscalationHistory(): Promise<{ data: EscalationRule[] }> {
  const res = await request.get<any, ApiResponse<{ history: string }>>('/admin/alert-escalation-history')
  return { data: JSON.parse(res.data.history) }
}

// ── V5-N7: 静默规则 ──
export async function fetchSilenceRules(): Promise<{ data: SilenceRule[] }> {
  const res = await request.get<any, ApiResponse<{ rules: string }>>('/admin/alert-silence-rules')
  return { data: JSON.parse(res.data.rules) }
}

export async function saveSilenceRules(rules: SilenceRule[]): Promise<void> {
  await request.put('/admin/alert-silence-rules', { rules: JSON.stringify(rules) })
}