import { defineStore } from 'pinia'
import { ref } from 'vue'
import { queryExceptions, resolveException } from '@/api/exception'
import type { ExceptionAdminListItem, ExceptionQueryRequest, ResolveExceptionRequest } from '@/types/exception'
import type { PageResponse } from '@/types/api'

export const useExceptionStore = defineStore('exception', () => {
  const list = ref<ExceptionAdminListItem[]>([])
  const total = ref(0)
  const loading = ref(false)
  const openCount = ref(0)

  async function fetchList(params: ExceptionQueryRequest) {
    loading.value = true
    try {
      const res = await queryExceptions(params)
      const page = res.data as unknown as PageResponse<ExceptionAdminListItem>
      list.value = page.records
      total.value = page.total
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

  return { list, total, loading, openCount, fetchList, fetchOpenCount, handleResolve }
})
