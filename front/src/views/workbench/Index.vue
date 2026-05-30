<template>
  <div class="workbench-page">
    <header class="workbench-header">
      <div class="header-main">
        <h1 class="page-title">调度工作台</h1>
        <p class="page-sub">任务池 · 园区态势 · 异常处置</p>
      </div>
      <div class="header-metrics">
        <div class="metric">
          <span class="metric-value">{{ store.pendingCount }}</span>
          <span class="metric-label">待派单</span>
        </div>
        <div class="metric metric-warn">
          <span class="metric-value">{{ store.manualPendingCount }}</span>
          <span class="metric-label">人工待处理</span>
        </div>
        <div class="metric metric-danger">
          <span class="metric-value">{{ store.openExceptionCount }}</span>
          <span class="metric-label">OPEN 异常</span>
        </div>
        <div class="metric-divider"></div>
        <div class="metric metric-success">
          <span class="metric-value">{{ store.assignableVehicleCount }}</span>
          <span class="metric-label">可派车</span>
        </div>
        <div class="metric metric-info">
          <span class="metric-value">{{ store.pluggedStandbyCount }}</span>
          <span class="metric-label">插枪待命</span>
        </div>
        <div class="metric metric-charging">
          <span class="metric-value">{{ store.chargingCount }}</span>
          <span class="metric-label">充电中</span>
        </div>
        <div class="metric metric-online">
          <span class="metric-value">{{ store.onlineVehicleCount }}</span>
          <span class="metric-label">在线车辆</span>
        </div>
      </div>
      <a-button :loading="store.loading" @click="refreshAll">
        <ReloadOutlined /> 刷新
      </a-button>
    </header>

    <div class="workbench-grid">
      <!-- 左：任务池 -->
      <section class="panel panel-tasks">
        <div class="panel-head">
          <h2>任务池</h2>
          <div class="filter-tabs">
            <button
              v-for="tab in taskTabs"
              :key="tab.key"
              class="filter-tab"
              :class="{ active: store.taskFilter === tab.key }"
              @click="store.taskFilter = tab.key"
            >
              {{ tab.label }}
              <span v-if="tab.count > 0" class="tab-count">{{ tab.count }}</span>
            </button>
          </div>
        </div>
        <div v-if="selectedTaskIds.length > 0" class="batch-toolbar">
          <span class="batch-hint">已选 {{ selectedTaskIds.length }} 项</span>
          <a-button size="small" type="primary" :loading="batchLoading" @click="handleBatchAuto">
            批量自动派车
          </a-button>
          <a-button size="small" :loading="batchLoading" @click="openBatchReassign">批量改派</a-button>
          <a-button size="small" danger :loading="batchLoading" @click="handleBatchCancel">批量取消</a-button>
          <a-button size="small" type="link" @click="clearSelection">清空</a-button>
        </div>
        <a-spin :spinning="store.loading">
          <div class="task-list">
            <article
              v-for="task in store.taskPool"
              :key="task.taskId"
              class="task-card"
              :class="{ selected: store.selectedTaskId === task.taskId, checked: selectedTaskIds.includes(task.taskId) }"
              draggable="true"
              @dragstart="onTaskDragStart(task.taskId)"
              @dragover.prevent
              @drop="onTaskDrop(task.taskId)"
              @click="store.selectTask(task.taskId)"
            >
              <label class="task-check" @click.stop>
                <input
                  type="checkbox"
                  :checked="selectedTaskIds.includes(task.taskId)"
                  @change="toggleTaskSelection(task.taskId)"
                />
              </label>
              <div class="task-card-head">
                <span class="task-no">{{ task.taskNo }}</span>
                <span v-if="task.orderPriority" class="priority-badge" :class="`priority-${task.orderPriority}`">
                  {{ task.orderPriority }}
                </span>
                <StatusBadge :status="task.status" type="task" />
              </div>
              <div class="task-meta">
                <span>订单 #{{ task.orderId }}</span>
                <span v-if="task.waitMinutes != null" class="wait-badge">等待 {{ task.waitMinutes }} 分</span>
                <span v-if="task.openExceptionCount" class="exc-badge">
                  {{ task.openExceptionCount }} 异常
                </span>
              </div>
              <p v-if="taskFailLabel(task)" class="task-reason">{{ taskFailLabel(task) }}</p>
              <div class="task-actions" @click.stop>
                <a-button
                  size="small"
                  type="primary"
                  :loading="actionLoading === `auto-${task.taskId}`"
                  @click="handleAutoAssign(task)"
                >
                  自动派车
                </a-button>
                <a-button size="small" @click="openManualModal(task)">手动派车</a-button>
                <a-button size="small" @click="handleBumpPriority(task)">紧急插队</a-button>
                <a-button type="link" size="small" @click="router.push(`/tasks/${task.taskId}`)">
                  详情
                </a-button>
              </div>
            </article>
            <EmptyState v-if="!store.loading && store.taskPool.length === 0" description="暂无待处理任务" />
          </div>
        </a-spin>
      </section>

      <!-- 中：地图缩略 -->
      <section class="panel panel-map">
        <div class="panel-head">
          <h2>园区态势</h2>
          <span class="panel-hint">选中任务高亮关联车辆</span>
        </div>
        <ParkMiniMap
          :layout="parkLayout"
          :vehicles="parkVehicles"
          :highlight-task-id="store.selectedTaskId"
        />
      </section>

      <!-- 右：异常队列 -->
      <section class="panel panel-exceptions">
        <div class="panel-head">
          <h2>异常队列</h2>
          <span class="panel-hint">OPEN · 快捷处置</span>
        </div>
        <a-spin :spinning="store.loading">
          <div class="exception-list">
            <article
              v-for="item in store.openExceptions"
              :key="item.id"
              class="exception-card"
              :class="{ selected: store.selectedExceptionId === item.id }"
              @click="store.selectException(item.id)"
            >
              <div class="exception-card-head">
                <span class="exc-type">{{ getExceptionLabel(item.exceptionType) }}</span>
                <span class="exc-time">{{ formatTime(item.occurTime) }}</span>
              </div>
              <p class="exc-msg">{{ item.exceptionMsg }}</p>
              <div class="exc-link">
                任务 {{ item.taskNo || `#${item.taskId}` }}
                <StatusBadge v-if="item.taskStatus" :status="item.taskStatus" type="task" />
              </div>
              <div class="exception-actions" @click.stop>
                <a-button
                  size="small"
                  type="primary"
                  :loading="actionLoading === `reassign-${item.id}`"
                  @click="handleExceptionReassign(item)"
                >
                  重新派车
                </a-button>
                <a-button
                  size="small"
                  danger
                  :loading="actionLoading === `fail-${item.id}`"
                  @click="handleExceptionResolve(item, 'MARK_FAILED')"
                >
                  标记失败
                </a-button>
                <a-button
                  size="small"
                  :loading="actionLoading === `close-${item.id}`"
                  @click="handleExceptionResolve(item, 'CLOSE')"
                >
                  关闭
                </a-button>
              </div>
            </article>
            <EmptyState v-if="!store.loading && store.openExceptions.length === 0" description="暂无 OPEN 异常" />
          </div>
        </a-spin>
      </section>
    </div>

    <a-modal
      v-model:open="manualModalVisible"
      title="手动派车"
      ok-text="确认派车"
      :confirm-loading="manualLoading"
      @ok="submitManualAssign"
    >
      <a-form layout="vertical">
        <a-form-item label="任务">
          <a-input :value="manualTask?.taskNo" disabled />
        </a-form-item>
        <a-form-item label="选择车辆" required>
          <a-select
            v-model:value="manualForm.vehicleId"
            placeholder="在线且空闲的车辆"
            show-search
            :loading="vehiclesLoading"
            :filter-option="filterVehicle"
          >
            <a-select-option
              v-for="v in assignableVehicles"
              :key="v.vehicleId"
              :value="v.vehicleId"
            >
              {{ v.vehicleCode }} · {{ v.batteryLevel }}% · {{ dispatchLabel(v.dispatchStatus) }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="备注">
          <a-input v-model:value="manualForm.remark" placeholder="选填" />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="batchReassignVisible"
      title="批量改派"
      ok-text="确认改派"
      :confirm-loading="batchLoading"
      @ok="submitBatchReassign"
    >
      <p class="batch-modal-hint">将对 {{ selectedTaskIds.length }} 个任务改派到同一车辆</p>
      <a-form layout="vertical">
        <a-form-item label="选择车辆" required>
          <a-select
            v-model:value="batchReassignVehicleId"
            placeholder="在线且空闲的车辆"
            :loading="vehiclesLoading"
            :options="assignableVehicleOptions"
          />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="batchResultVisible"
      title="批量操作结果"
      :footer="null"
      width="560px"
    >
      <p class="batch-result-summary">
        成功 {{ batchResult?.successCount ?? 0 }} / 失败 {{ batchResult?.failureCount ?? 0 }}（共 {{ batchResult?.total ?? 0 }}）
      </p>
      <a-table
        size="small"
        :pagination="false"
        row-key="taskId"
        :data-source="batchResult?.results || []"
        :columns="[
          { title: '任务', dataIndex: 'taskNo', key: 'taskNo' },
          { title: '状态', dataIndex: 'status', key: 'status' },
          { title: '说明', dataIndex: 'message', key: 'message' },
        ]"
      />
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { ReloadOutlined } from '@ant-design/icons-vue'
import dayjs from 'dayjs'
import StatusBadge from '@/components/common/StatusBadge.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import ParkMiniMap from '@/components/workbench/ParkMiniMap.vue'
import { useWorkbenchStore } from '@/stores/workbench'
import { queryVehicles } from '@/api/vehicle'
import { batchAutoAssign, batchCancelTasks, batchReassignTasks, bumpTaskPriority } from '@/api/task'
import { exceptionTypeMap, DISPATCH_FAIL_REASON } from '@/constants/statusMap'
import type { BatchTaskResult } from '@/types/operateLog'
import { DispatchStatus, TaskStatus } from '@/constants/enums'
import type { TaskAdminListItem } from '@/types/task'
import type { ExceptionAdminListItem } from '@/types/exception'

const router = useRouter()
const store = useWorkbenchStore()
const draggingTaskId = ref<number | null>(null)

const parkLayout = computed(() => store.parkLayout)
const parkVehicles = computed(() => store.parkVehicles)
interface ManualVehicleOption {
  vehicleId: number
  vehicleCode: string
  vehicleName: string
  batteryLevel: number
  dispatchStatus: string
}

const assignableVehicles = ref<ManualVehicleOption[]>([])
const actionLoading = ref<string | null>(null)
const manualModalVisible = ref(false)
const manualLoading = ref(false)
const vehiclesLoading = ref(false)
const manualTask = ref<TaskAdminListItem | null>(null)
const manualForm = reactive({ vehicleId: undefined as number | undefined, remark: '' })
const selectedTaskIds = ref<number[]>([])
const batchLoading = ref(false)
const batchReassignVisible = ref(false)
const batchReassignVehicleId = ref<number | undefined>()
const batchResultVisible = ref(false)
const batchResult = ref<BatchTaskResult | null>(null)

const assignableVehicleOptions = computed(() =>
  assignableVehicles.value.map((v) => ({
    label: `${v.vehicleCode} · ${v.batteryLevel}%`,
    value: v.vehicleId,
  }))
)

const taskTabs = computed(() => [
  { key: 'ALL' as const, label: '全部', count: store.interventionTotal },
  { key: 'PENDING' as const, label: '待派单', count: store.pendingCount },
  { key: 'MANUAL_PENDING' as const, label: '人工', count: store.manualPendingCount },
])

function formatTime(value: string) {
  return dayjs(value).format('MM-DD HH:mm')
}

function getExceptionLabel(type: string) {
  return exceptionTypeMap[type as keyof typeof exceptionTypeMap]?.label || type
}

function dispatchLabel(status: string) {
  if (status === DispatchStatus.IDLE) return '空闲'
  if (status === DispatchStatus.BUSY) return '忙碌'
  return '不可用'
}

function taskFailLabel(task: TaskAdminListItem) {
  if (task.failReasonCode && DISPATCH_FAIL_REASON[task.failReasonCode]) {
    return DISPATCH_FAIL_REASON[task.failReasonCode]
  }
  return task.failReasonMsg || ''
}

function filterVehicle(input: string, option: { value: number }) {
  const v = assignableVehicles.value.find((item) => item.vehicleId === option.value)
  if (!v) return false
  const label = `${v.vehicleCode} ${v.vehicleName}`.toLowerCase()
  return label.includes(input.toLowerCase())
}

function vehiclesFromWorkbench(): ManualVehicleOption[] {
  return store.parkVehicles
    .filter((v) => v.onlineStatus === 'ONLINE' && v.dispatchStatus === DispatchStatus.IDLE)
    .map((v) => ({
      vehicleId: v.vehicleId,
      vehicleCode: v.vehicleCode,
      vehicleName: v.vehicleName,
      batteryLevel: v.batteryLevel,
      dispatchStatus: v.dispatchStatus,
    }))
}

async function loadAssignableVehicles() {
  const fromWorkbench = vehiclesFromWorkbench()
  if (fromWorkbench.length > 0) {
    assignableVehicles.value = fromWorkbench
    return
  }
  vehiclesLoading.value = true
  try {
    const res = await queryVehicles({
      onlineStatus: 'ONLINE' as any,
      dispatchStatus: DispatchStatus.IDLE,
      pageNo: 1,
      pageSize: 100,
    })
    assignableVehicles.value = (res.data.records || []).map((v) => ({
      vehicleId: v.vehicleId,
      vehicleCode: v.vehicleCode,
      vehicleName: v.vehicleName,
      batteryLevel: v.batteryLevel ?? 0,
      dispatchStatus: v.dispatchStatus,
    }))
  } catch {
    assignableVehicles.value = []
  } finally {
    vehiclesLoading.value = false
  }
}

async function refreshAll() {
  await store.fetchQueue()
}

async function handleAutoAssign(task: TaskAdminListItem) {
  actionLoading.value = `auto-${task.taskId}`
  try {
    const result = await store.dispatchAuto(task.taskId)
    if (result.status === TaskStatus.ASSIGNED) {
      const explanation = result.assignExplanation
      const vehicleCode = result.selectedVehicleCode
      const score = result.assignScore
      let msg = '自动派车成功'
      if (vehicleCode) msg += ` · ${vehicleCode}`
      if (explanation) msg += `：${explanation}`
      else if (score != null) msg += `（评分 ${score.toFixed(1)}）`
      message.success(msg, 5)
    } else {
      const failCode = result.failReasonCode
      const failMsg = failCode ? DISPATCH_FAIL_REASON[failCode] : null
      message.error(failMsg || result.message || '自动派车失败，请人工处理', 5)
    }
  } catch {
    // interceptor handles
  } finally {
    actionLoading.value = null
  }
}

function clearSelection() {
  selectedTaskIds.value = []
}

function toggleTaskSelection(taskId: number) {
  const idx = selectedTaskIds.value.indexOf(taskId)
  if (idx >= 0) {
    selectedTaskIds.value.splice(idx, 1)
  } else {
    selectedTaskIds.value.push(taskId)
  }
}

async function handleBatchAuto() {
  if (selectedTaskIds.value.length === 0) return
  batchLoading.value = true
  try {
    const res = await batchAutoAssign([...selectedTaskIds.value])
    batchResult.value = res.data
    batchResultVisible.value = true
    clearSelection()
    await refreshAll()
  } finally {
    batchLoading.value = false
  }
}

async function handleBatchCancel() {
  if (selectedTaskIds.value.length === 0) return
  batchLoading.value = true
  try {
    const res = await batchCancelTasks([...selectedTaskIds.value])
    batchResult.value = res.data
    batchResultVisible.value = true
    clearSelection()
    await refreshAll()
  } finally {
    batchLoading.value = false
  }
}

function openBatchReassign() {
  batchReassignVehicleId.value = undefined
  loadAssignableVehicles()
  batchReassignVisible.value = true
}

async function submitBatchReassign() {
  if (!batchReassignVehicleId.value || selectedTaskIds.value.length === 0) {
    message.warning('请选择车辆')
    return
  }
  batchLoading.value = true
  try {
    const res = await batchReassignTasks([...selectedTaskIds.value], batchReassignVehicleId.value)
    batchResult.value = res.data
    batchResultVisible.value = true
    batchReassignVisible.value = false
    clearSelection()
    await refreshAll()
  } finally {
    batchLoading.value = false
  }
}

function openManualModal(task: TaskAdminListItem) {
  manualTask.value = task
  manualForm.vehicleId = undefined
  manualForm.remark = ''
  manualModalVisible.value = true
  loadAssignableVehicles()
}

async function submitManualAssign() {
  if (!manualTask.value || !manualForm.vehicleId) {
    message.warning('请选择车辆')
    return
  }
  manualLoading.value = true
  try {
    const result = await store.dispatchManual(
      manualTask.value.taskId,
      manualForm.vehicleId,
      manualForm.remark
    )
    if (result.status === TaskStatus.ASSIGNED) {
      message.success('手动派车成功')
      manualModalVisible.value = false
    } else {
      message.warning(result.message || '派车未成功')
    }
  } catch {
    // interceptor handles
  } finally {
    manualLoading.value = false
  }
}

async function handleExceptionReassign(item: ExceptionAdminListItem) {
  if (!item.taskId) return
  actionLoading.value = `reassign-${item.id}`
  try {
    const result = await store.dispatchAuto(item.taskId)
    if (result.status === TaskStatus.ASSIGNED) {
      const explanation = result.assignExplanation
      const vehicleCode = result.selectedVehicleCode
      let msg = '重新派车成功，异常已自动关闭'
      if (vehicleCode) msg += ` · ${vehicleCode}`
      if (explanation) msg += `：${explanation}`
      message.success(msg, 5)
    } else {
      const failCode = result.failReasonCode
      const failMsg = failCode ? DISPATCH_FAIL_REASON[failCode] : null
      message.error(failMsg || result.message || '重新派车失败', 5)
    }
  } catch {
    // interceptor handles
  } finally {
    actionLoading.value = null
  }
}

async function handleBumpPriority(task: TaskAdminListItem) {
  actionLoading.value = `bump-${task.taskId}`
  try {
    await bumpTaskPriority(task.taskId)
    message.success(`已将 ${task.taskNo} 提升至 P0 优先级`)
    await refreshAll()
  } finally {
    actionLoading.value = null
  }
}

function onTaskDragStart(taskId: number) {
  draggingTaskId.value = taskId
}

function onTaskDrop(targetTaskId: number) {
  if (draggingTaskId.value == null || draggingTaskId.value === targetTaskId) return
  const ids = store.taskPool.map((t) => t.taskId)
  const from = ids.indexOf(draggingTaskId.value)
  const to = ids.indexOf(targetTaskId)
  if (from < 0 || to < 0) return
  ids.splice(from, 1)
  ids.splice(to, 0, draggingTaskId.value)
  store.reorderTasks(ids)
  draggingTaskId.value = null
}

async function handleExceptionResolve(item: ExceptionAdminListItem, action: 'MARK_FAILED' | 'CLOSE') {
  actionLoading.value = `${action === 'MARK_FAILED' ? 'fail' : 'close'}-${item.id}`
  try {
    await store.resolveOpenException(item.id, {
      resolverId: 'admin',
      resolverName: '调度员',
      action,
      remark: action === 'MARK_FAILED' ? '工作台标记失败' : '工作台关闭异常',
    })
    message.success(action === 'MARK_FAILED' ? '已标记失败' : '异常已关闭')
  } catch {
    // interceptor handles
  } finally {
    actionLoading.value = null
  }
}

onMounted(() => {
  refreshAll()
})
</script>

<style scoped lang="less">
.workbench-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: calc(100vh - 112px);
}

.workbench-header {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 20px 24px;
  border-radius: 14px;
  background: linear-gradient(135deg, rgba(0, 119, 182, 0.12) 0%, rgba(13, 17, 23, 0.95) 55%);
  border: 1px solid var(--fsd-border);
}

.header-main {
  flex: 1;
  min-width: 0;
}

.page-title {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: var(--fsd-text-primary);
}

.page-sub {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--fsd-text-tertiary);
}

.header-metrics {
  display: flex;
  gap: 20px;
}

.metric {
  text-align: center;
  min-width: 72px;

  .metric-value {
    display: block;
    font-size: 24px;
    font-weight: 700;
    font-family: 'JetBrains Mono', monospace;
    color: var(--fsd-accent);
    line-height: 1.1;
  }

  .metric-label {
    font-size: 11px;
    color: var(--fsd-text-tertiary);
    letter-spacing: 0.04em;
  }

  &.metric-warn .metric-value {
    color: var(--fsd-warning);
  }

  &.metric-danger .metric-value {
    color: var(--fsd-error);
  }

  &.metric-success .metric-value {
    color: var(--fsd-success);
  }

  &.metric-info .metric-value {
    color: var(--fsd-info, #1890ff);
  }

  &.metric-charging .metric-value {
    color: #722ed1;
  }

  &.metric-online .metric-value {
    color: #52c41a;
  }
}

.metric-divider {
  width: 1px;
  height: 36px;
  background: var(--fsd-border);
  margin: 0 8px;
}

.workbench-grid {
  flex: 1;
  display: grid;
  grid-template-columns: minmax(300px, 340px) 1fr minmax(300px, 360px);
  gap: 16px;
  min-height: 520px;
}

.panel {
  display: flex;
  flex-direction: column;
  border-radius: 14px;
  border: 1px solid var(--fsd-border);
  background: rgba(22, 27, 34, 0.6);
  overflow: hidden;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--fsd-border);

  h2 {
    margin: 0;
    font-size: 14px;
    font-weight: 600;
    color: var(--fsd-text-primary);
  }
}

.panel-hint {
  font-size: 11px;
  color: var(--fsd-text-tertiary);
}

.filter-tabs {
  display: flex;
  gap: 4px;
}

.filter-tab {
  padding: 4px 10px;
  border: 1px solid transparent;
  border-radius: 6px;
  background: transparent;
  color: var(--fsd-text-secondary);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s;

  &:hover {
    background: var(--fsd-bg-hover);
  }

  &.active {
    border-color: rgba(0, 180, 216, 0.35);
    background: rgba(0, 180, 216, 0.1);
    color: var(--fsd-accent);
  }
}

.tab-count {
  margin-left: 4px;
  padding: 0 5px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.08);
  font-family: 'JetBrains Mono', monospace;
  font-size: 10px;
}

.task-list,
.exception-list {
  flex: 1;
  overflow: auto;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: calc(100vh - 260px);
}

.task-card {
  position: relative;
  padding: 12px 12px 12px 36px;
  border-radius: 10px;
  border: 1px solid var(--fsd-border);
  background: rgba(13, 17, 23, 0.5);
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s;

  &:hover {
    border-color: rgba(0, 180, 216, 0.25);
  }

  &.selected,
  &.checked {
    border-color: rgba(0, 180, 216, 0.5);
    box-shadow: 0 0 0 1px rgba(0, 180, 216, 0.15);
  }
}

.exception-card {
  padding: 12px;
  border-radius: 10px;
  border: 1px solid var(--fsd-border);
  background: rgba(13, 17, 23, 0.5);
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s;

  &:hover {
    border-color: rgba(0, 180, 216, 0.25);
  }

  &.selected {
    border-color: rgba(0, 180, 216, 0.5);
    box-shadow: 0 0 0 1px rgba(0, 180, 216, 0.15);
  }
}

.task-check {
  position: absolute;
  left: 10px;
  top: 14px;
}

.batch-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 12px 10px;
  flex-wrap: wrap;
}

.batch-hint {
  font-size: 12px;
  color: var(--fsd-text-secondary);
}

.batch-modal-hint,
.batch-result-summary {
  margin-bottom: 12px;
  color: var(--fsd-text-secondary);
}

.task-card-head,
.exception-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.task-no {
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
  color: var(--fsd-text-primary);
  font-weight: 600;
}

.priority-badge {
  font-size: 10px;
  font-weight: 700;
  padding: 2px 6px;
  border-radius: 4px;
  letter-spacing: 0.04em;
}

.priority-P0 { background: rgba(255, 61, 113, 0.2); color: #ff3d71; }
.priority-P1 { background: rgba(255, 176, 32, 0.2); color: #ffb020; }
.priority-P2 { background: rgba(0, 180, 216, 0.15); color: #00b4d8; }
.priority-P3 { background: rgba(160, 160, 160, 0.15); color: #a0a0a0; }

.wait-badge {
  font-size: 11px;
  color: var(--fsd-warning);
}

.task-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 6px;
  font-size: 12px;
  color: var(--fsd-text-secondary);
}

.exc-badge {
  color: var(--fsd-error);
  font-size: 11px;
}

.task-reason,
.exc-msg {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--fsd-text-tertiary);
  line-height: 1.45;
}

.task-actions,
.exception-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.exc-type {
  font-size: 12px;
  font-weight: 600;
  color: var(--fsd-warning);
}

.exc-time {
  font-size: 11px;
  font-family: 'JetBrains Mono', monospace;
  color: var(--fsd-text-tertiary);
}

.exc-link {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
  font-size: 12px;
  color: var(--fsd-text-secondary);
}

.panel-map {
  min-height: 360px;

  :deep(.park-mini-map) {
    flex: 1;
    margin: 12px;
    min-height: 0;
  }
}

@media (max-width: 1200px) {
  .workbench-grid {
    grid-template-columns: 1fr;
  }

  .task-list,
  .exception-list {
    max-height: 360px;
  }
}
</style>
