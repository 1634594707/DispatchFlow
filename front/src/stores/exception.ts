import { defineStore } from 'pinia'
import { ref } from 'vue'
import { queryExceptions, resolveException } from '@/api/exception'
import type { ExceptionAdminListItem, ExceptionQueryRequest, ResolveExceptionRequest } from '@/types/exception'
import type { PageResponse } from '@/types/api'
import type { EscalationRecord } from '@/types/alert'

export const useExceptionStore = defineStore('exception', () => {
  const list = ref<ExceptionAdminListItem[]>([])
  const total = ref(0)
  const loading = ref(false)
  const openCount = ref(0)

  // ── V5-N6: 告警升级 ──
  const escalationRules = ref<{ severity: string; timeoutMinutes: number }[]>([])
  const escalationNeeded = ref<EscalationRecord[]>([])

  async function fetchList(params: ExceptionQueryRequest) {
    loading.value = true
    try {
      const res = await queryExceptions(params)
      const page = res.data as unknown as PageResponse<ExceptionAdminListItem>
      list.value = page.records
      total.value = page.total

      // V5-N6: Check OPEN exceptions for escalation
      checkEscalation()
    } catch (e) {
      console.error('Failed to fetch exceptions', e)
    } finally {
      loading.value = false
    }
  }

  async function fetchOpenCount() {
    try {
      const res = await queryExceptions({ pageNo: 1, pageSize: 1, exceptionStatus: 'OPEN' as any })
      const page = res.data as unknown as PageResponse<ExceptionAdminListItem>
      openCount.value = page.total
    } catch {
      // silent
    }
  }

  async function handleResolve(exceptionId: number, data: ResolveExceptionRequest) {
    await resolveException(exceptionId, data)
  }

  // V5-N6: 升级检查
  function checkEscalation() {
    if (escalationRules.value.length === 0) return
    const now = Date.now()
    const escalated: EscalationRecord[] = []

    for (const item of list.value) {
      if (item.exceptionStatus !== 'OPEN') continue
      const severity = String((item as any).severity || 'HIGH')
      const rule = escalationRules.value.find(r => r.severity === severity)
      if (!rule) continue

      const occurTime = new Date(item.occurTime).getTime()
      const elapsedMinutes = (now - occurTime) / 60000
      if (elapsedMinutes >= rule.timeoutMinutes) {
        escalated.push({
          id: `esc-${item.id}-${Date.now()}`,
          exceptionId: item.id,
          escalatedAt: new Date().toISOString(),
          fromSeverity: severity,
          escalatedTo: rule.severity === 'CRITICAL' ? 'ADMIN' : 'OPERATOR',
          status: 'pending',
        })
      }
    }

    escalationNeeded.value = escalated
  }

  return { list, total, loading, openCount, escalationRules, escalationNeeded, fetchList, fetchOpenCount, handleResolve, checkEscalation }
})