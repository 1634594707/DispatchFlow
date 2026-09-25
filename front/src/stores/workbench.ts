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
import { exceptionDedupKey } from '@/utils/notificationDisplay'

const TASK_ORDER_KEY = 'fsd_workbench_task_order'

export type WorkbenchTaskFilter = 'ALL' | 'PENDING' | 'MANUAL_PENDING'

export const useWorkbenchStore = defineStore('workbench', () => {
  const parkScope = useParkScopeStore()
  const loading = ref(false)
  /**
   * §6.3：取数失败要能被人看见。
   *
   * 原来两处 catch 只 `console.error`，页面上的 KPI 于是**停在上一轮的值**上继续显示 ——
   * "待派 0"既可能是真的没有单，也可能是后端已经挂了 5 分钟。
   */
  const queueError = ref<string | null>(null)
  const poolError = ref<string | null>(null)
  const lastQueueAt = ref<Date | null>(null)
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

  function compactOpenExceptions(items: ExceptionAdminListItem[]) {
    const seen = new Set<string>()
    return [...items]
      .filter((item) => !item.exceptionStatus || item.exceptionStatus === 'OPEN')
      .sort((a, b) => Date.parse(b.occurTime || b.createdAt) - Date.parse(a.occurTime || a.createdAt))
      .filter((item) => {
        const key = exceptionDedupKey(item)
        if (seen.has(key)) return false
        seen.add(key)
        return true
      })
  }

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
      poolError.value = null
    } catch (e) {
      console.error('Failed to fetch task pool', e)
      poolError.value = e instanceof Error ? e.message : String(e)
    } finally {
      poolLoading.value = false
    }
  }

  /**
   * §6.5：布局角标和页面首屏会在同一帧各要一次同样的队列数据，SSE 降级兜底也会再要一次。
   * 并发调用合并成一次请求；`force` 留给"刚改完状态，必须看到新值"的调用方。
   */
  let queueInFlight: Promise<void> | null = null

  function fetchQueue(options?: { silent?: boolean; force?: boolean }) {
    if (!options?.force && queueInFlight) return queueInFlight
    const run = loadQueue(options)
    queueInFlight = run
    void run.finally(() => {
      if (queueInFlight === run) queueInFlight = null
    })
    return run
  }

  async function loadQueue(options?: { silent?: boolean }) {
    if (!options?.silent) {
      loading.value = true
    }
    try {
      const res = await getDispatchWorkbench(parkScope.selectedParkId)
      const data = res.data
      const intervention = data.intervention
      openExceptions.value = compactOpenExceptions(intervention?.openExceptions || [])
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
      lastQueueAt.value = new Date()
      queueError.value = null
      await fetchTaskPool({ silent: true })
    } catch (e) {
      console.error('Failed to fetch intervention queue', e)
      queueError.value = e instanceof Error ? e.message : String(e)
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
    const res = await autoAssignTask(taskId, parkScope.selectedParkId)
    await fetchQueue({ force: true })
    return res.data
  }

  async function dispatchManual(taskId: number, vehicleId: number, remark?: string) {
    const res = await manualAssignTask(taskId, { vehicleId, remark }, parkScope.selectedParkId)
    await fetchQueue({ force: true })
    return res.data
  }

  async function resolveOpenException(exceptionId: number, payload: ResolveExceptionRequest) {
    await resolveException(exceptionId, payload)
    await fetchQueue({ force: true })
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

  function clearManualTaskOrder() {
    manualTaskOrder.value = []
    localStorage.removeItem(TASK_ORDER_KEY)
  }

  return {
    queueError,
    poolError,
    lastQueueAt,
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
    manualTaskOrder,
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
    clearManualTaskOrder,
  }
})
