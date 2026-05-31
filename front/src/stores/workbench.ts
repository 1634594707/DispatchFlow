import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { getDispatchWorkbench, queryTaskPool } from '@/api/dispatch'
import { useParkScopeStore } from '@/stores/parkScope'
import { autoAssignTask, manualAssignTask } from '@/api/task'
import { resolveException } from '@/api/exception'
import type { ExceptionAdminListItem } from '@/types/exception'
import type { TaskAdminListItem } from '@/types/task'
import type { ResolveExceptionRequest } from '@/types/exception'
import type { ParkLayout, ParkVehicleSnapshot } from '@/types/park'

const TASK_ORDER_KEY = 'fsd_workbench_task_order'

export type WorkbenchTaskFilter = 'ALL' | 'PENDING' | 'MANUAL_PENDING'

export const useWorkbenchStore = defineStore('workbench', () => {
  const parkScope = useParkScopeStore()
  const loading = ref(false)
  const poolTasks = ref<TaskAdminListItem[]>([])
  const poolTotal = ref(0)
  const poolPageNo = ref(1)
  const poolPageSize = ref(20)
  const poolLoading = ref(false)
  const openExceptions = ref<ExceptionAdminListItem[]>([])
  const pendingCount = ref(0)
  const manualPendingCount = ref(0)
  const openExceptionCount = ref(0)
  const assignableVehicleCount = ref(0)
  const pluggedStandbyCount = ref(0)
  const chargingCount = ref(0)
  const onlineVehicleCount = ref(0)
  const parkLayout = ref<ParkLayout | null>(null)
  const parkVehicles = ref<ParkVehicleSnapshot[]>([])
  const selectedTaskId = ref<number | null>(null)
  const selectedExceptionId = ref<number | null>(null)
  const taskFilter = ref<WorkbenchTaskFilter>('ALL')
  const manualTaskOrder = ref<number[]>(loadManualTaskOrder())

  function loadManualTaskOrder(): number[] {
    try {
      const raw = localStorage.getItem(TASK_ORDER_KEY)
      return raw ? JSON.parse(raw) : []
    } catch {
      return []
    }
  }

  function saveManualTaskOrder(order: number[]) {
    manualTaskOrder.value = order
    localStorage.setItem(TASK_ORDER_KEY, JSON.stringify(order))
  }

  function applyManualOrder(tasks: TaskAdminListItem[]) {
    if (manualTaskOrder.value.length === 0) return tasks
    const rank = new Map(manualTaskOrder.value.map((id, index) => [id, index]))
    return [...tasks].sort((a, b) => {
      const ra = rank.get(a.taskId)
      const rb = rank.get(b.taskId)
      if (ra != null && rb != null) return ra - rb
      if (ra != null) return -1
      if (rb != null) return 1
      return 0
    })
  }

  const taskPool = computed(() => applyManualOrder(poolTasks.value))

  const poolHasMore = computed(() => poolTasks.value.length < poolTotal.value)

  const interventionTotal = computed(() => pendingCount.value + manualPendingCount.value)

  function mapPoolFilter(): WorkbenchTaskFilter {
    return taskFilter.value === 'ALL' ? 'ALL' : taskFilter.value
  }

  async function fetchTaskPool(options?: { append?: boolean; silent?: boolean }) {
    if (!options?.silent) {
      poolLoading.value = true
    }
    try {
      const page = options?.append ? poolPageNo.value + 1 : 1
      const res = await queryTaskPool({
        parkId: parkScope.selectedParkId,
        poolStatus: mapPoolFilter(),
        pageNo: page,
        pageSize: poolPageSize.value,
      })
      const data = res.data
      poolPageNo.value = page
      poolTotal.value = data.total
      poolTasks.value = options?.append
        ? [...poolTasks.value, ...data.records]
        : data.records
    } catch (e) {
      console.error('Failed to fetch task pool', e)
    } finally {
      poolLoading.value = false
    }
  }

  async function fetchQueue(options?: { silent?: boolean }) {
    if (!options?.silent) {
      loading.value = true
    }
    try {
      const res = await getDispatchWorkbench(parkScope.selectedParkId)
      const data = res.data
      const intervention = data.intervention
      openExceptions.value = intervention?.openExceptions || []
      pendingCount.value = intervention?.pendingCount ?? 0
      manualPendingCount.value = intervention?.manualPendingCount ?? 0
      openExceptionCount.value = intervention?.openExceptionCount ?? openExceptions.value.length
      assignableVehicleCount.value = data.fleetMetrics?.assignableVehicleCount ?? 0
      pluggedStandbyCount.value = data.fleetMetrics?.pluggedStandbyCount ?? 0
      chargingCount.value = data.fleetMetrics?.chargingCount ?? 0
      onlineVehicleCount.value = data.fleetMetrics?.onlineVehicleCount ?? 0
      parkLayout.value = data.parkLayout ?? null
      parkVehicles.value = data.vehicles ?? []
      poolPageNo.value = 1
      await fetchTaskPool({ silent: true })
    } catch (e) {
      console.error('Failed to fetch intervention queue', e)
    } finally {
      if (!options?.silent) {
        loading.value = false
      }
    }
  }

  async function loadMoreTasks() {
    if (!poolHasMore.value || poolLoading.value) return
    await fetchTaskPool({ append: true })
  }

  async function dispatchAuto(taskId: number) {
    const res = await autoAssignTask(taskId)
    await fetchQueue()
    return res.data
  }

  async function dispatchManual(taskId: number, vehicleId: number, remark?: string) {
    const res = await manualAssignTask(taskId, { vehicleId, remark })
    await fetchQueue()
    return res.data
  }

  async function resolveOpenException(exceptionId: number, payload: ResolveExceptionRequest) {
    await resolveException(exceptionId, payload)
    await fetchQueue()
  }

  function selectTask(taskId: number | null) {
    selectedTaskId.value = taskId
    if (taskId != null) {
      selectedExceptionId.value = null
    }
  }

  function selectException(exceptionId: number | null) {
    selectedExceptionId.value = exceptionId
    if (exceptionId != null) {
      const item = openExceptions.value.find((e) => e.id === exceptionId)
      selectedTaskId.value = item?.taskId ?? null
    }
  }

  function reorderTasks(taskIds: number[]) {
    saveManualTaskOrder(taskIds)
  }

  return {
    loading,
    poolTasks,
    poolTotal,
    poolPageNo,
    poolPageSize,
    poolLoading,
    poolHasMore,
    openExceptions,
    pendingCount,
    manualPendingCount,
    openExceptionCount,
    assignableVehicleCount,
    pluggedStandbyCount,
    chargingCount,
    onlineVehicleCount,
    parkLayout,
    parkVehicles,
    interventionTotal,
    taskPool,
    taskFilter,
    selectedTaskId,
    selectedExceptionId,
    fetchQueue,
    fetchTaskPool,
    loadMoreTasks,
    dispatchAuto,
    dispatchManual,
    resolveOpenException,
    selectTask,
    selectException,
    reorderTasks,
  }
})
