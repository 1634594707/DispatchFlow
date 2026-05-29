import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { getDispatchWorkbench } from '@/api/dispatch'
import { autoAssignTask, manualAssignTask } from '@/api/task'
import { resolveException } from '@/api/exception'
import type { ExceptionAdminListItem } from '@/types/exception'
import type { TaskAdminListItem } from '@/types/task'
import type { ResolveExceptionRequest } from '@/types/exception'
import type { ParkLayout, ParkVehicleSnapshot } from '@/types/park'

export type WorkbenchTaskFilter = 'ALL' | 'PENDING' | 'MANUAL_PENDING'

export const useWorkbenchStore = defineStore('workbench', () => {
  const loading = ref(false)
  const pendingTasks = ref<TaskAdminListItem[]>([])
  const manualPendingTasks = ref<TaskAdminListItem[]>([])
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

  const taskPool = computed(() => {
    if (taskFilter.value === 'PENDING') return pendingTasks.value
    if (taskFilter.value === 'MANUAL_PENDING') return manualPendingTasks.value
    return [...pendingTasks.value, ...manualPendingTasks.value]
  })

  const interventionTotal = computed(() => pendingCount.value + manualPendingCount.value)

  async function fetchQueue() {
    loading.value = true
    try {
      const res = await getDispatchWorkbench()
      const data = res.data
      const intervention = data.intervention
      pendingTasks.value = intervention?.pendingTasks || []
      manualPendingTasks.value = intervention?.manualPendingTasks || []
      openExceptions.value = intervention?.openExceptions || []
      pendingCount.value = intervention?.pendingCount ?? pendingTasks.value.length
      manualPendingCount.value = intervention?.manualPendingCount ?? manualPendingTasks.value.length
      openExceptionCount.value = intervention?.openExceptionCount ?? openExceptions.value.length
      assignableVehicleCount.value = data.fleetMetrics?.assignableVehicleCount ?? 0
      pluggedStandbyCount.value = data.fleetMetrics?.pluggedStandbyCount ?? 0
      chargingCount.value = data.fleetMetrics?.chargingCount ?? 0
      onlineVehicleCount.value = data.fleetMetrics?.onlineVehicleCount ?? 0
      parkLayout.value = data.parkLayout ?? null
      parkVehicles.value = data.vehicles ?? []
    } catch (e) {
      console.error('Failed to fetch intervention queue', e)
    } finally {
      loading.value = false
    }
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

  return {
    loading,
    pendingTasks,
    manualPendingTasks,
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
    dispatchAuto,
    dispatchManual,
    resolveOpenException,
    selectTask,
    selectException,
  }
})
