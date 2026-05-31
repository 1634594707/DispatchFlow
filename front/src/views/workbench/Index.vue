<template>
  <div class="workbench-page">
    <header class="workbench-header">
      <div class="header-top">
        <div class="header-main">
          <h1 class="page-title">调度工作台</h1>
          <p class="page-sub">任务池 · 园区态势 · 异常处置</p>
        </div>
        <div class="header-actions">
          <a-switch
            v-if="authStore.isAdmin"
            v-model:checked="dispatchPaused"
            checked-children="暂停派单"
            un-checked-children="正常派单"
            @change="onDispatchPauseChange"
          />
          <a-button :loading="store.loading" @click="refreshAll">
            <ReloadOutlined /> 刷新 <span class="kbd-hint">R</span>
          </a-button>
          <router-link
            v-if="trafficSummary"
            class="congestion-bar"
            :class="congestionBarClass"
            to="/infrastructure/traffic"
          >
            拥堵 L{{ trafficSummary.maxCongestionLevel }}
            <span v-if="trafficSummary.highCongestionSegmentCount > 0">
              · {{ trafficSummary.highCongestionSegmentCount }} 条高拥堵
            </span>
            <span v-if="trafficSummary.pausedZoneCount > 0">
              · {{ trafficSummary.pausedZoneCount }} 个管制区
            </span>
          </router-link>
        </div>
      </div>
      <div class="header-bottom">
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
        <span class="shortcut-hint">快捷键：R 刷新 · A 自动派车 · M 手动派车 · ↑↓ 切换任务</span>
      </div>
    </header>

    <a-alert
      v-if="!authStore.canWrite"
      type="info"
      show-icon
      class="viewer-readonly-banner"
      message="当前为只读账号（VIEWER），无法执行派车、改派或异常处置操作"
    />

    <div class="workbench-grid">
      <!-- 左：任务池 -->
      <section class="panel panel-tasks">
        <div class="panel-head">
          <div class="panel-head-title">
            <h2>任务池</h2>
            <span class="panel-order-hint">拖动排序仅在本浏览器有效</span>
          </div>
          <div class="panel-head-toolbar">
            <div class="filter-tabs">
              <button
                v-for="tab in taskTabs"
                :key="tab.key"
                class="filter-tab"
                :class="{ active: store.taskFilter === tab.key }"
                @click="onTaskTabChange(tab.key)"
              >
                {{ tab.label }}
                <span v-if="tab.count > 0" class="tab-count">{{ tab.count }}</span>
              </button>
            </div>
            <a-select
              v-model:value="routeFilter"
              allow-clear
              placeholder="按线路筛选"
              :options="routeOptions"
              class="route-filter"
              size="small"
            />
            <router-link to="/vertical/hub" class="hub-link">母港分流</router-link>
          </div>
        </div>
        <div v-if="authStore.canWrite && selectedTaskIds.length > 0" class="batch-toolbar">
          <span class="batch-hint">已选 {{ selectedTaskIds.length }} 项</span>
          <a-button size="small" type="primary" :loading="batchLoading" @click="handleBatchAuto">
            批量自动派车
          </a-button>
          <a-button size="small" :loading="batchLoading" @click="openBatchReassign">批量改派</a-button>
          <a-button size="small" danger :loading="batchLoading" @click="handleBatchCancel">批量取消</a-button>
          <a-button size="small" type="link" @click="clearSelection">清空</a-button>
        </div>
        <a-spin :spinning="store.loading || store.poolLoading">
          <div class="task-list">
            <article
              v-for="task in filteredTaskPool"
              :key="task.taskId"
              class="task-card"
              :class="{ selected: store.selectedTaskId === task.taskId, checked: selectedTaskIds.includes(task.taskId) }"
              draggable="true"
              @dragstart="onTaskDragStart(task.taskId)"
              @dragover.prevent
              @drop="onTaskDrop(task.taskId)"
              @click="store.selectTask(task.taskId)"
            >
              <label v-if="authStore.canWrite" class="task-check" @click.stop>
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
                <span v-if="task.routeCode" class="route-badge">{{ task.routeCode }}</span>
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
              <div v-if="task.failReasonCode" class="task-fail-detail">
                <ul v-if="taskFailSuggestions(task).length" class="fail-suggestions">
                  <li v-for="(s, i) in taskFailSuggestions(task)" :key="i">{{ s }}</li>
                </ul>
                <div class="fail-links">
                  <router-link
                    v-for="link in taskFailLinks(task)"
                    :key="link.path"
                    :to="link.path"
                    class="fail-link"
                  >
                    {{ link.label }}
                  </router-link>
                </div>
              </div>
              <div v-if="authStore.canWrite" class="task-actions" @click.stop>
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
            <EmptyState v-if="!store.loading && !store.poolLoading && filteredTaskPool.length === 0" description="暂无待处理任务" />
          </div>
          <div v-if="store.poolHasMore" class="pool-load-more">
            <a-button block :loading="store.poolLoading" @click="store.loadMoreTasks">
              加载更多（{{ filteredTaskPool.length }} / {{ store.poolTotal }}）
            </a-button>
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
              <div v-if="authStore.canWrite" class="exception-actions" @click.stop>
                <a-button
                  size="small"
                  type="primary"
                  :loading="actionLoading === `reassign-${item.id}`"
                  @click="handleExceptionReassign(item)"
                >
                  重新派车
                </a-button>
                <a-button
                  v-if="authStore.isAdmin"
                  size="small"
                  :loading="actionLoading === `field-${item.id}`"
                  @click="handleAssignFieldOps(item)"
                >
                  指派现场
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
        :columns="batchResultColumns"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'success'">
            <a-tag :color="record.success ? 'success' : 'error'">{{ record.success ? '成功' : '失败' }}</a-tag>
          </template>
          <template v-else-if="column.key === 'detail'">
            <div v-if="!record.success" class="batch-fail-detail">
              <strong>{{ record.reasonMessage || record.message }}</strong>
              <ul v-if="record.suggestions?.length">
                <li v-for="(s, i) in record.suggestions" :key="i">{{ s }}</li>
              </ul>
            </div>
            <span v-else>{{ record.message || '-' }}</span>
          </template>
        </template>
      </a-table>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useWorkbenchShortcuts } from '@/composables/useWorkbenchShortcuts'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { ReloadOutlined } from '@ant-design/icons-vue'
import dayjs from 'dayjs'
import StatusBadge from '@/components/common/StatusBadge.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import ParkMiniMap from '@/components/workbench/ParkMiniMap.vue'
import { useWorkbenchStore } from '@/stores/workbench'
import { useAuthStore } from '@/stores/auth'
import { useParkScopeStore } from '@/stores/parkScope'
import { queryVehicles } from '@/api/vehicle'
import { fetchTrafficSummary } from '@/api/traffic'
import { fetchDispatchPauseStatus, setDispatchPause } from '@/api/dispatchPause'
import { batchAutoAssign, batchCancelTasks, batchReassignTasks, bumpTaskPriority } from '@/api/task'
import { fetchRoutes } from '@/api/vertical'
import { assignFieldOps } from '@/api/fieldOps'
import { fetchUsers } from '@/api/auth'
import type { DispatchRoute } from '@/api/vertical'
import { exceptionTypeMap } from '@/constants/statusMap'
import {
  explainFromAssignResponse,
  failActionLinks,
  failReasonLabel,
  normalizeFailCode,
} from '@/constants/dispatchFail'
import type { BatchTaskResult } from '@/types/operateLog'
import type { TrafficSummary } from '@/types/traffic'
import { DispatchStatus, TaskStatus } from '@/constants/enums'
import type { TaskAdminListItem } from '@/types/task'
import type { ExceptionAdminListItem } from '@/types/exception'

const router = useRouter()
const route = useRoute()
const store = useWorkbenchStore()
const authStore = useAuthStore()
const parkScope = useParkScopeStore()
const workbenchShortcutsEnabled = computed(() => route.path === '/workbench')
const trafficSummary = ref<TrafficSummary | null>(null)
const dispatchPaused = ref(false)
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
const routeFilter = ref<number | undefined>()
const routeOptions = ref<{ label: string; value: number }[]>([])

const filteredTaskPool = computed(() => {
  if (!routeFilter.value) return store.taskPool
  return store.taskPool.filter((task) => task.routeId === routeFilter.value)
})

const batchResultColumns = [
  { title: '任务', dataIndex: 'taskNo', key: 'taskNo', width: 120 },
  { title: '结果', key: 'success', width: 72 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 100 },
  { title: '说明', key: 'detail' },
]

const congestionBarClass = computed(() => {
  const level = trafficSummary.value?.maxCongestionLevel ?? 0
  if (level >= 3) return 'congestion-critical'
  if (level >= 2) return 'congestion-warn'
  return 'congestion-ok'
})

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

function onTaskTabChange(key: 'ALL' | 'PENDING' | 'MANUAL_PENDING') {
  store.taskFilter = key
  void store.fetchTaskPool()
}

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
  return failReasonLabel(task.failReasonCode, task.failReasonMsg)
}

function taskFailSuggestions(task: TaskAdminListItem) {
  const code = normalizeFailCode(task.failReasonCode)
  const defaults: Record<string, string[]> = {
    NO_IDLE_VEHICLE: ['检查在线空闲车辆', '尝试手动派车'],
    LOW_BATTERY: ['引导车辆充电', '降低 SOC 阈值或等待'],
    ROUTE_BLOCKED: ['检查禁用路段与管制区', '确认取货点可达'],
    HUB_CAPACITY_FULL: ['枢纽容量已满（预留）'],
  }
  return defaults[code] || []
}

function taskFailLinks(task: TaskAdminListItem) {
  return failActionLinks(task.failReasonCode)
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
  await Promise.all([store.fetchQueue(), loadTrafficSummary()])
}

async function loadTrafficSummary() {
  const parkId = parkScope.resolveLayoutParkId()
  if (!parkId) {
    trafficSummary.value = null
    return
  }
  try {
    const res = await fetchTrafficSummary(parkId)
    trafficSummary.value = res.data
  } catch {
    trafficSummary.value = null
  }
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
      const explained = explainFromAssignResponse(result)
      message.error(`${explained.reasonMessage}${explained.suggestions.length ? ' · ' + explained.suggestions[0] : ''}`, 6)
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
      const explained = explainFromAssignResponse(result)
      message.error(explained.reasonMessage || '重新派车失败', 5)
    }
  } catch {
    // interceptor handles
  } finally {
    actionLoading.value = null
  }
}

async function handleAssignFieldOps(item: ExceptionAdminListItem) {
  actionLoading.value = `field-${item.id}`
  try {
    const users = (await fetchUsers()).data.filter((u) => u.role === 'FIELD_OPS' && u.status === 'ACTIVE')
    if (users.length === 0) {
      message.warning('暂无 FIELD_OPS 用户，请先在用户管理创建')
      return
    }
    const assignee = users[0]
    await assignFieldOps(item.id, assignee.id, '工作台指派')
    message.success(`已指派给 ${assignee.displayName || assignee.username}`)
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

function moveTaskSelection(delta: number) {
  const pool = store.taskPool
  if (pool.length === 0) return
  const currentIndex = pool.findIndex((t) => t.taskId === store.selectedTaskId)
  const nextIndex = currentIndex < 0
    ? (delta > 0 ? 0 : pool.length - 1)
    : (currentIndex + delta + pool.length) % pool.length
  store.selectTask(pool[nextIndex].taskId)
}

useWorkbenchShortcuts(workbenchShortcutsEnabled, {
  refresh: refreshAll,
  autoAssignSelected: async () => {
    const taskId = store.selectedTaskId
    const task = store.taskPool.find((t) => t.taskId === taskId)
    if (task) {
      await handleAutoAssign(task)
    }
  },
  openManualAssign: () => {
    const task = store.taskPool.find((t) => t.taskId === store.selectedTaskId)
    if (task) openManualModal(task)
  },
  moveSelection: moveTaskSelection,
})

async function loadDispatchPause() {
  if (!authStore.isAdmin) return
  const res = await fetchDispatchPauseStatus(parkScope.selectedParkId ?? undefined)
  dispatchPaused.value = res.data.globalPaused || res.data.parkPaused
}

async function onDispatchPauseChange(checked: boolean) {
  await setDispatchPause(parkScope.selectedParkId ?? null, checked)
  message.success(checked ? '已暂停新派单' : '已恢复新派单')
}

onMounted(() => {
  refreshAll()
  void loadDispatchPause()
  void loadRouteOptions()
})

async function loadRouteOptions() {
  try {
    const res = await fetchRoutes(parkScope.selectedParkId ?? undefined)
    routeOptions.value = res.data.map((r: DispatchRoute) => ({ label: r.routeName, value: r.id }))
  } catch {
    routeOptions.value = []
  }
}

watch(() => parkScope.scopeVersion, () => {
  loadTrafficSummary()
  void loadDispatchPause()
  void loadRouteOptions()
  void store.fetchQueue()
})
</script>

<style scoped lang="less">
.workbench-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: calc(100vh - 112px);
}

.shortcut-hint {
  font-size: 12px;
  color: #8c9bab;
  white-space: nowrap;
}

.kbd-hint {
  margin-left: 4px;
  font-size: 10px;
  border: 1px solid #d7e0e8;
  border-radius: 4px;
  padding: 0 4px;
}

.workbench-header {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 20px 24px;
  border-radius: 14px;
  background: linear-gradient(135deg, rgba(0, 119, 182, 0.12) 0%, rgba(13, 17, 23, 0.95) 55%);
  border: 1px solid var(--fsd-border);
}

.header-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.header-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.viewer-readonly-banner {
  margin-bottom: 16px;
}

.congestion-bar {
  padding: 6px 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  text-decoration: none;
  border: 1px solid var(--fsd-border);
  white-space: nowrap;

  &.congestion-ok {
    color: #00e676;
    background: rgba(0, 230, 118, 0.08);
  }

  &.congestion-warn {
    color: #ffb703;
    background: rgba(255, 183, 3, 0.1);
  }

  &.congestion-critical {
    color: #ff3d71;
    background: rgba(255, 61, 113, 0.12);
  }
}

.panel-order-hint {
  font-size: 11px;
  color: var(--fsd-text-tertiary);
  white-space: nowrap;
}

.task-fail-detail {
  margin-top: 8px;
  padding: 8px 10px;
  border-radius: 8px;
  background: rgba(255, 61, 113, 0.06);
  border: 1px dashed rgba(255, 61, 113, 0.25);
}

.fail-suggestions {
  margin: 0 0 6px;
  padding-left: 18px;
  font-size: 12px;
  color: var(--fsd-text-secondary);
}

.fail-links {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.fail-link {
  font-size: 12px;
  color: var(--fsd-accent);
}

.batch-fail-detail {
  font-size: 12px;

  ul {
    margin: 4px 0 0;
    padding-left: 16px;
    color: var(--fsd-text-secondary);
  }
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
  grid-template-columns: minmax(320px, 380px) 1fr minmax(300px, 360px);
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
  flex-direction: column;
  gap: 10px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--fsd-border);

  h2 {
    margin: 0;
    font-size: 14px;
    font-weight: 600;
    color: var(--fsd-text-primary);
    white-space: nowrap;
  }
}

.panel-head-title {
  display: flex;
  align-items: baseline;
  gap: 10px;
  flex-wrap: wrap;
}

.panel-head-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.route-filter {
  width: 148px;
  flex-shrink: 0;
}

.panel-hint {
  font-size: 11px;
  color: var(--fsd-text-tertiary);
}

.filter-tabs {
  display: flex;
  gap: 4px;
}

.hub-link {
  margin-left: auto;
  font-size: 12px;
  color: var(--fsd-accent);
  text-decoration: none;
  white-space: nowrap;
}

.route-badge {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 4px;
  background: rgba(0, 180, 216, 0.15);
  color: #00b4d8;
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

.pool-load-more {
  margin-top: 12px;
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

  .pool-load-more {
  margin-top: 12px;
}

.task-list,
  .exception-list {
    max-height: 360px;
  }
}
</style>
