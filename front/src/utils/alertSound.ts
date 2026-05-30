import type { AlertHistoryItem, AlertRuleConfig } from '@/types/alert'
import { DEFAULT_ALERT_RULES } from '@/types/alert'

const STORAGE_KEY = 'fsd_alert_rules'
const HISTORY_KEY = 'fsd_alert_history'
const MAX_HISTORY = 50

let audioCtx: AudioContext | null = null

function getAudioContext() {
  if (!audioCtx) {
    audioCtx = new AudioContext()
  }
  return audioCtx
}

export function playAlertTone(critical = false) {
  try {
    const ctx = getAudioContext()
    const oscillator = ctx.createOscillator()
    const gain = ctx.createGain()
    oscillator.type = critical ? 'square' : 'sine'
    oscillator.frequency.value = critical ? 880 : 660
    gain.gain.value = 0.001
    oscillator.connect(gain)
    gain.connect(ctx.destination)
    oscillator.start()
    gain.gain.exponentialRampToValueAtTime(0.25, ctx.currentTime + 0.02)
    gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + (critical ? 0.45 : 0.25))
    oscillator.stop(ctx.currentTime + (critical ? 0.5 : 0.3))
  } catch (e) {
    console.warn('[AlertSound] Failed to play tone', e)
  }
}

export async function requestBrowserNotifyPermission() {
  if (!('Notification' in window)) return false
  if (Notification.permission === 'granted') return true
  if (Notification.permission === 'denied') return false
  const result = await Notification.requestPermission()
  return result === 'granted'
}

export function showBrowserNotification(title: string, body: string) {
  if (!('Notification' in window) || Notification.permission !== 'granted') return
  new Notification(title, { body, tag: `fsd-${Date.now()}` })
}

export function loadAlertRules(): AlertRuleConfig {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return { ...DEFAULT_ALERT_RULES }
    return { ...DEFAULT_ALERT_RULES, ...JSON.parse(raw) }
  } catch {
    return { ...DEFAULT_ALERT_RULES }
  }
}

export function saveAlertRules(rules: AlertRuleConfig) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(rules))
}

export function loadAlertHistory(): AlertHistoryItem[] {
  try {
    const raw = localStorage.getItem(HISTORY_KEY)
    return raw ? JSON.parse(raw) : []
  } catch {
    return []
  }
}

export function pushAlertHistory(item: AlertHistoryItem) {
  const history = [item, ...loadAlertHistory()].slice(0, MAX_HISTORY)
  localStorage.setItem(HISTORY_KEY, JSON.stringify(history))
  return history
}

export function isInSilentPeriod(startHour: number | null, endHour: number | null) {
  if (startHour == null || endHour == null) return false
  const hour = new Date().getHours()
  if (startHour <= endHour) {
    return hour >= startHour && hour < endHour
  }
  return hour >= startHour || hour < endHour
}
