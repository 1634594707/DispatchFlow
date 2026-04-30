import { defineStore } from 'pinia'
import { ref } from 'vue'
import { queryTasks, getTaskDetail } from '@/api/task'
import type { TaskAdminListItem, TaskDetailResponse, TaskQueryRequest } from '@/types/task'
import type { PageResponse } from '@/types/api'

export const useTaskStore = defineStore('task', () => {
  const list = ref<TaskAdminListItem[]>([])
  const total = ref(0)
  const loading = ref(false)
  const detail = ref<TaskDetailResponse | null>(null)
  const detailLoading = ref(false)

  async function fetchList(params: TaskQueryRequest) {
    loading.value = true
    try {
      const res = await queryTasks(params)
      const page = res.data as unknown as PageResponse<TaskAdminListItem>
      list.value = page.records
      total.value = page.total
    } catch (e) {
      console.error('Failed to fetch tasks', e)
    } finally {
      loading.value = false
    }
  }

  async function fetchDetail(taskId: number) {
    detailLoading.value = true
    try {
      const res = await getTaskDetail(taskId)
      detail.value = res.data
    } catch (e) {
      console.error('Failed to fetch task detail', e)
    } finally {
      detailLoading.value = false
    }
  }

  return { list, total, loading, detail, detailLoading, fetchList, fetchDetail }
})
