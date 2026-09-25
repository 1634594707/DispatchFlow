<template>
  <PageContainer title="调度任务" subtitle="管理所有调度任务">
    <template #actions>
      <a-button @click="handleRefresh"> <ReloadOutlined /> 刷新 </a-button>
    </template>

    <QueryToolbar
      title="筛选条件"
      :result-summary="`共 ${store.total} 条结果`"
      :active-chips="activeFilterChips"
      @remove="removeFilterChip"
      @clear="handleReset"
    >
      <a-select
        v-model:value="queryForm.status"
        placeholder="任务状态"
        allow-clear
        style="width: 160px"
      >
        <a-select-option v-for="(cfg, key) in taskStatusMap" :key="key" :value="key">
          {{ cfg.label }}
        </a-select-option>
      </a-select>
      <a-input
        v-model:value="queryForm.taskNo"
        placeholder="任务编号"
        allow-clear
        style="width: 180px"
      />
      <a-input-number
        v-model:value="queryForm.orderId"
        placeholder="订单ID"
        style="width: 140px"
        :min="1"
      />
      <a-input-number
        v-model:value="queryForm.vehicleId"
        placeholder="车辆ID"
        style="width: 140px"
        :min="1"
      />
      <a-button type="primary" @click="handleSearch"> <SearchOutlined /> 查询 </a-button>
      <a-button @click="handleReset">重置</a-button>
      <template #extra>
        <a-button :disabled="store.total === 0" @click="handleExport">
          <DownloadOutlined /> 导出
        </a-button>
      </template>
    </QueryToolbar>

    <!-- §6.4：批量撤销/改派/取消三个端点此前零 UI 入口（只有 e2e 里裸 fetch 用过），这里补真按钮 -->
    <div
      v-if="selectedTaskIds.length > 0"
      class="batch-bar"
      role="group"
      aria-label="批量操作"
      style="display: flex; align-items: center; gap: 8px; margin-bottom: 12px; flex-wrap: wrap"
    >
      <span class="mono-text">已选 {{ selectedTaskIds.length }} 项</span>
      <a-button size="small" :disabled="!authStore.canWrite" :loading="batchLoading" @click="runBatch('auto')">
        批量自动派车
      </a-button>
      <a-popconfirm title="确认批量撤销？将释放车辆并退回待派队列。" ok-text="确认" cancel-text="取消" @confirm="runBatch('unassign')">
        <a-button size="small" :disabled="!authStore.canWrite" :loading="batchLoading">批量撤销</a-button>
      </a-popconfirm>
      <a-button size="small" :disabled="!authStore.canWrite" :loading="batchLoading" @click="openBatchReassign">
        批量改派
      </a-button>
      <a-popconfirm title="确认批量取消所选任务？" ok-text="确认" cancel-text="取消" @confirm="runBatch('cancel')">
        <a-button size="small" danger :disabled="!authStore.canWrite" :loading="batchLoading">批量取消</a-button>
      </a-popconfirm>
      <a-button type="link" size="small" @click="clearSelection">清空选择</a-button>
      <span v-if="batchError" style="color: var(--fsd-error)">{{ batchError }}</span>
    </div>

    <!-- V9-UI3: Skeleton screen for initial load -->
    <SkeletonLoader v-if="store.loading && store.list.length === 0" variant="table" :rows="6" />
    <a-table
      v-else
      :columns="columns"
      :data-source="store.list"
      :loading="store.loading"
      :pagination="pagination"
      row-key="taskId"
      :row-selection="rowSelection"
      size="middle"
      :scroll="{ x: 'max-content' }"
      @change="handleTableChange"
    >
      <template #emptyText>
        <EmptyState description="暂无调度任务，可前往工作台处理待派单" />
      </template>
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'taskNo'">
          <router-link :to="`/tasks/${record.taskId}`" class="link-cell">
            {{ record.taskNo }}
          </router-link>
        </template>
        <template v-else-if="column.dataIndex === 'orderId'">
          <router-link :to="`/orders/${record.orderId}`" class="link-cell">
            {{ record.orderId }}
          </router-link>
        </template>
        <template v-else-if="column.dataIndex === 'vehicleId'">
          <router-link
            v-if="record.vehicleId"
            :to="`/vehicles/${record.vehicleId}`"
            class="link-cell"
          >
            {{ record.vehicleId }}
          </router-link>
          <span v-else class="text-muted">-</span>
        </template>
        <template v-else-if="column.dataIndex === 'status'">
          <StatusBadge :status="record.status" type="task" />
        </template>
        <template v-else-if="column.dataIndex === 'dispatchType'">
          <a-tag class="metadata-tag">
            {{ record.dispatchType === 'AUTO' ? '自动' : '手动' }}
          </a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'createdAt'">
          <span class="mono-text">{{ formatTime(record.createdAt) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'action'">
          <div class="action-cell">
            <a-button type="link" size="small" @click="router.push(`/tasks/${record.taskId}`)">
              查看
            </a-button>
            <span v-if="record.status === 'ASSIGNING'" class="action-status">派车处理中…</span>
            <a-button
              v-if="authStore.canWrite && canDispatch(record.status)"
              type="link"
              size="small"
              :disabled="isActionBusy(record.taskId)"
              @click="openDispatchModal(record)"
            >
              {{ isActionBusy(record.taskId) ? '处理中…' : '派单' }}
            </a-button>
            <a-button
              v-if="authStore.canWrite && canReassign(record.status)"
              type="link"
              size="small"
              :disabled="isActionBusy(record.taskId)"
              @click="openReassignModal(record)"
            >
              改派
            </a-button>
            <a-popconfirm
              v-if="authStore.canWrite && canCancel(record.status) && !isActionBusy(record.taskId)"
              title="确认取消该任务？"
              ok-text="确认"
              cancel-text="取消"
              @confirm="handleCancel(record)"
            >
              <a-button type="link" size="small" danger :loading="isActionBusy(record.taskId)"
                >取消</a-button
              >
            </a-popconfirm>
          </div>
        </template>
      </template>
    </a-table>

    <a-modal
      v-model:open="dispatchModalVisible"
      title="派单"
      :confirm-loading="dispatchLoading"
      :ok-button-props="{ disabled: assignableVehicles.length === 0 }"
      @ok="handleDispatch"
    >
      <a-form layout="vertical">
        <a-form-item label="选择车辆" required>
          <a-select
            v-model:value="dispatchForm.vehicleId"
            placeholder="请选择在线空闲车辆"
            show-search
            :filter-option="filterOption"
            :loading="assignableVehiclesLoading"
            :not-found-content="assignableVehiclesLoading ? '加载中...' : '暂无在线空闲车辆'"
          >
            <a-select-option
              v-for="vehicle in assignableVehicles"
              :key="vehicle.vehicleId"
              :value="vehicle.vehicleId"
            >
              {{ formatVehicleOption(vehicle) }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="备注">
          <a-input v-model:value="dispatchForm.remark" placeholder="选填" />
        </a-form-item>
      </a-form>
      <p v-if="assignableVehiclesError" class="mono-text" style="color: var(--fsd-error)">
        {{ assignableVehiclesError }}
      </p>
    </a-modal>

    <a-modal
      v-model:open="reassignModalVisible"
      title="改派"
      :confirm-loading="reassignLoading"
      :ok-button-props="{ disabled: reassignableVehicles.length === 0 }"
      @ok="handleReassign"
    >
      <a-alert
        message="改派会导致当前车辆释放，请确认是否继续。"
        type="warning"
        show-icon
        style="margin-bottom: 16px"
      />
      <a-form layout="vertical">
        <a-form-item label="当前车辆">
          <a-input :value="currentTask?.vehicleId" disabled />
        </a-form-item>
        <a-form-item label="新车辆" required>
          <a-select
            v-model:value="reassignForm.newVehicleId"
            placeholder="请选择新车辆"
            show-search
            :filter-option="filterOption"
            :loading="assignableVehiclesLoading"
            :not-found-content="assignableVehiclesLoading ? '加载中...' : '暂无在线空闲车辆'"
          >
            <a-select-option
              v-for="vehicle in reassignableVehicles"
              :key="vehicle.vehicleId"
              :value="vehicle.vehicleId"
            >
              {{ formatVehicleOption(vehicle) }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="改派原因" required>
          <a-textarea
            v-model:value="reassignForm.reason"
            placeholder="请输入改派原因（至少5个字符）"
            :rows="3"
          />
        </a-form-item>
      </a-form>
    </a-modal>
    <a-modal
      v-model:open="batchReassignVisible"
      title="批量改派"
      :confirm-loading="batchLoading"
      :ok-button-props="{ disabled: batchReassignForm.vehicleId == null }"
      @ok="runBatch('reassign')"
    >
      <a-alert
        message="批量改派会把所选任务全部指向同一台车，已派出的走改派、未派的直接指派。"
        type="warning"
        show-icon
        style="margin-bottom: 16px"
      />
      <a-form layout="vertical">
        <a-form-item label="目标车辆" required>
          <a-select
            v-model:value="batchReassignForm.vehicleId"
            placeholder="请选择在线空闲车辆"
            show-search
            :filter-option="filterOption"
            :loading="assignableVehiclesLoading"
            :not-found-content="assignableVehiclesLoading ? '加载中...' : '暂无在线空闲车辆'"
          >
            <a-select-option
              v-for="vehicle in assignableVehicles"
              :key="vehicle.vehicleId"
              :value="vehicle.vehicleId"
            >
              {{ formatVehicleOption(vehicle) }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="备注">
          <a-input v-model:value="batchReassignForm.remark" placeholder="选填" />
        </a-form-item>
      </a-form>
      <p class="mono-text">本次将提交 {{ selectedTaskIds.length }} 个任务。</p>
      <p v-if="assignableVehiclesError" class="mono-text" style="color: var(--fsd-error)">
        {{ assignableVehiclesError }}
      </p>
    </a-modal>
  </PageContainer>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import { ReloadOutlined, SearchOutlined, DownloadOutlined } from '@ant-design/icons-vue'
import PageContainer from '@/components/common/PageContainer.vue'
import QueryToolbar from '@/components/common/QueryToolbar.vue'
import type { FilterChip } from '@/components/common/QueryToolbar.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import SkeletonLoader from '@/components/common/SkeletonLoader.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import { useTaskStore } from '@/stores/task'
import { downloadAnalyticsFile, getAnalyticsExportUrl } from '@/api/analytics'
import { useParkScopeStore } from '@/stores/parkScope'
import { useAuthStore } from '@/stores/auth'
import { useRealtimeStore } from '@/stores/realtime'
import { taskStatusMap, enumLabel } from '@/constants/statusMap'
import { TaskStatus } from '@/constants/enums'
import { DEFAULT_PAGE_SIZE } from '@/config'
import { manualAssignTask, reassignTask, cancelTask, batchAutoAssign, batchCancelTasks, batchReassignTasks, batchUnassignTasks } from '@/api/task'
import type { BatchTaskResult } from '@/types/operateLog'
import { getDispatchWorkbench } from '@/api/dispatch'
import dayjs from 'dayjs'
import type { TaskAdminListItem } from '@/types/task'
import type { ParkVehicleSnapshot } from '@/types/park'

const router = useRouter()
const route = useRoute()
const store = useTaskStore()
const parkScope = useParkScopeStore()
const authStore = useAuthStore()
const realtimeStore = useRealtimeStore()

const queryForm = reactive({
  status: undefined as TaskStatus | undefined,
  taskNo: '',
  orderId: undefined as number | undefined,
  vehicleId: undefined as number | undefined,
})

const pageNo = ref(1)
const pageSize = ref(DEFAULT_PAGE_SIZE)
let stopRealtimeRefresh: (() => void) | null = null

const columns = [
  { title: '任务编号', dataIndex: 'taskNo', width: 200 },
  { title: '关联订单', dataIndex: 'orderId', width: 100 },
  { title: '车辆ID', dataIndex: 'vehicleId', width: 100 },
  { title: '状态', dataIndex: 'status', width: 130 },
  { title: '派单类型', dataIndex: 'dispatchType', width: 100 },
  { title: '创建时间', dataIndex: 'createdAt', width: 180 },
  { title: '操作', dataIndex: 'action', width: 200, fixed: 'right' as const },
]

const pagination = computed(() => ({
  current: pageNo.value,
  pageSize: pageSize.value,
  total: store.total,
  showSizeChanger: true,
  showQuickJumper: true,
  showTotal: (total: number) => `共 ${total} 条`,
  pageSizeOptions: ['10', '20', '50', '100'],
}))

const activeFilterChips = computed((): FilterChip[] => {
  const chips: FilterChip[] = []
  if (queryForm.status) {
    chips.push({
      key: 'status',
      label: `状态：${enumLabel(taskStatusMap, queryForm.status, '任务状态')}`,
    })
  }
  if (queryForm.taskNo.trim()) {
    chips.push({ key: 'taskNo', label: `编号：${queryForm.taskNo.trim()}` })
  }
  if (queryForm.orderId) {
    chips.push({ key: 'orderId', label: `订单：${queryForm.orderId}` })
  }
  if (queryForm.vehicleId) {
    chips.push({ key: 'vehicleId', label: `车辆：${queryForm.vehicleId}` })
  }
  return chips
})

function removeFilterChip(key: string) {
  if (key === 'status') queryForm.status = undefined
  if (key === 'taskNo') queryForm.taskNo = ''
  if (key === 'orderId') queryForm.orderId = undefined
  if (key === 'vehicleId') queryForm.vehicleId = undefined
  handleSearch()
}

function formatTime(t: string) {
  return dayjs(t).format('YYYY-MM-DD HH:mm:ss')
}

function canDispatch(status: TaskStatus) {
  return [TaskStatus.PENDING, TaskStatus.MANUAL_PENDING].includes(status)
}

function canReassign(status: TaskStatus) {
  return status === TaskStatus.ASSIGNED
}

function canCancel(status: TaskStatus) {
  return [
    TaskStatus.PENDING,
    TaskStatus.MANUAL_PENDING,
    TaskStatus.ASSIGNED,
    TaskStatus.EXECUTING,
  ].includes(status)
}

function filterOption(input: string, option: any) {
  return option.children?.[0]?.toLowerCase().includes(input.toLowerCase())
}

function fetchData() {
  store.fetchList({
    ...queryForm,
    parkId: parkScope.selectedParkId,
    pageNo: pageNo.value,
    pageSize: pageSize.value,
  })
}

function handleSearch() {
  pageNo.value = 1
  fetchData()
}

function handleReset() {
  queryForm.status = undefined
  queryForm.taskNo = ''
  queryForm.orderId = undefined
  queryForm.vehicleId = undefined
  pageNo.value = 1
  fetchData()
}

function handleRefresh() {
  fetchData()
}

async function handleExport() {
  try {
    await downloadAnalyticsFile(
      getAnalyticsExportUrl('tasks', 'week', parkScope.selectedParkId),
      'tasks-week.csv',
    )
    message.success('任务导出已开始')
  } catch (err) {
    message.error(err instanceof Error && err.message ? err.message : '任务导出失败，请重试')
  }
}

function handleTableChange(pag: any) {
  pageNo.value = pag.current
  pageSize.value = pag.pageSize
  fetchData()
}

type BatchKind = 'auto' | 'unassign' | 'cancel' | 'reassign'

const BATCH_LABELS: Record<BatchKind, string> = {
  auto: '批量自动派车',
  unassign: '批量撤销',
  cancel: '批量取消',
  reassign: '批量改派',
}

const selectedTaskIds = ref<number[]>([])
const batchLoading = ref(false)
const batchError = ref<string | null>(null)
const batchReassignVisible = ref(false)
const batchReassignForm = reactive({ vehicleId: undefined as number | undefined, remark: '' })

const rowSelection = computed(() => ({
  selectedRowKeys: selectedTaskIds.value,
  onChange: (keys: (string | number)[]) => {
    selectedTaskIds.value = keys.map(Number)
  },
}))

/**
 * 翻页或刷新后，不在当前页的行必须从选择里剔除：批量端点按 id 集合执行，
 * 留着一行"看不见的选中项"就是误操作面。
 */
watch(
  () => store.list,
  (rows) => {
    const visible = new Set(rows.map((row) => row.taskId))
    selectedTaskIds.value = selectedTaskIds.value.filter((taskId) => visible.has(taskId))
  },
)

function clearSelection() {
  selectedTaskIds.value = []
  batchError.value = null
}

async function runBatch(kind: BatchKind) {
  const taskIds = [...selectedTaskIds.value]
  if (taskIds.length === 0 || batchLoading.value) {
    return
  }
  const parkId = parkScope.selectedParkId ?? undefined
  batchLoading.value = true
  batchError.value = null
  try {
    const response =
      kind === 'auto'
        ? await batchAutoAssign(taskIds, parkId)
        : kind === 'unassign'
          ? await batchUnassignTasks(taskIds, undefined, parkId)
          : kind === 'cancel'
            ? await batchCancelTasks(taskIds, undefined, parkId)
            : await batchReassignTasks(taskIds, batchReassignForm.vehicleId as number, batchReassignForm.remark || undefined, parkId)
    reportBatchResult(kind, response.data)
    fetchData()
  } catch (err) {
    // 失败必须可见：这里不能退成"什么都没发生"，也不能退成空列表
    batchError.value = `${BATCH_LABELS[kind]}失败：${err instanceof Error ? err.message : String(err)}`
  } finally {
    batchLoading.value = false
    if (kind === 'reassign') {
      batchReassignVisible.value = false
    }
  }
}

/** 部分成功是批量操作的常态：整批结果与逐条失败原因都要露出来，不能只报"成功"。 */
function reportBatchResult(kind: BatchKind, result?: BatchTaskResult) {
  if (!result) {
    batchError.value = `${BATCH_LABELS[kind]}未返回结果，请在刷新后核对任务状态`
    return
  }
  const retryable = result.retryableTaskIds?.length ? `，${result.retryableTaskIds.length} 项可重试` : ''
  if (result.failureCount > 0) {
    message.warning(`${BATCH_LABELS[kind]}：成功 ${result.successCount} / 失败 ${result.failureCount}${retryable}`)
  } else {
    message.success(`${BATCH_LABELS[kind]}：${result.successCount} 项已完成`)
  }
  const failures = (result.results || []).filter((item) => !item.success && item.reasonCode).slice(0, 3)
  batchError.value = failures.length
    ? `未完成：${failures.map((item) => `${item.taskNo || item.taskId} · ${item.reasonMessage || item.reasonCode}`).join('；')}`
    : null
  selectedTaskIds.value = (result.results || [])
    .filter((item) => !item.success)
    .map((item) => item.taskId)
    .filter((taskId) => selectedTaskIds.value.includes(taskId))
}

function openBatchReassign() {
  batchReassignForm.vehicleId = undefined
  batchReassignForm.remark = ''
  batchError.value = null
  loadAssignableVehicles()
  batchReassignVisible.value = true
}

const dispatchModalVisible = ref(false)
const dispatchLoading = ref(false)
const assignableVehiclesLoading = ref(false)
/** 取数失败要可见（§6.4）：把"接口挂了"显示成"暂无在线空闲车辆"会误导调度员再等一会儿。 */
const assignableVehiclesError = ref<string | null>(null)
/** 进行中的单条操作任务 ID（路线图 3.2：处理中禁用重复操作） */
const actionTaskIds = ref<Set<number>>(new Set())

function isActionBusy(taskId: number) {
  return actionTaskIds.value.has(taskId)
}
const currentTask = ref<TaskAdminListItem | null>(null)
const dispatchForm = reactive({ vehicleId: undefined as number | undefined, remark: '' })
const assignableVehicles = ref<ParkVehicleSnapshot[]>([])

const reassignableVehicles = computed(() =>
  assignableVehicles.value.filter((vehicle) => vehicle.vehicleId !== currentTask.value?.vehicleId),
)

function isAssignableVehicle(vehicle: ParkVehicleSnapshot) {
  return vehicle.onlineStatus === 'ONLINE' && vehicle.dispatchStatus === 'IDLE'
}

function formatVehicleOption(vehicle: ParkVehicleSnapshot) {
  return `${vehicle.vehicleCode} (${vehicle.onlineStatus === 'ONLINE' ? '在线' : vehicle.onlineStatus}·${vehicle.dispatchStatus === 'IDLE' ? '空闲' : vehicle.dispatchStatus})`
}

async function loadAssignableVehicles() {
  assignableVehiclesLoading.value = true
  assignableVehiclesError.value = null
  try {
    const res = await getDispatchWorkbench(parkScope.selectedParkId)
    assignableVehicles.value = (res.data.vehicles || []).filter(isAssignableVehicle)
    if (assignableVehicles.value.length === 0) {
      assignableVehiclesError.value = '当前无在线空闲车辆可派'
    }
  } catch (err) {
    assignableVehicles.value = []
    assignableVehiclesError.value = `车辆列表读取失败：${err instanceof Error ? err.message : String(err)}`
  } finally {
    assignableVehiclesLoading.value = false
  }
}

function openDispatchModal(record: TaskAdminListItem) {
  currentTask.value = record
  dispatchForm.vehicleId = undefined
  dispatchForm.remark = ''
  dispatchModalVisible.value = true
  loadAssignableVehicles()
}

async function handleDispatch() {
  if (!currentTask.value) return
  if (!dispatchForm.vehicleId) {
    message.warning('请选择车辆')
    return
  }
  dispatchLoading.value = true
  try {
    await manualAssignTask(
      currentTask.value.taskId,
      {
        vehicleId: dispatchForm.vehicleId,
        remark: dispatchForm.remark,
      },
      parkScope.selectedParkId,
    )
    message.success('派单成功')
    dispatchModalVisible.value = false
    fetchData()
  } catch {
    // handled by interceptor
  } finally {
    dispatchLoading.value = false
  }
}

const reassignModalVisible = ref(false)
const reassignLoading = ref(false)
const reassignForm = reactive({ newVehicleId: undefined as number | undefined, reason: '' })

function openReassignModal(record: TaskAdminListItem) {
  currentTask.value = record
  reassignForm.newVehicleId = undefined
  reassignForm.reason = ''
  reassignModalVisible.value = true
  loadAssignableVehicles()
}

async function handleReassign() {
  if (!currentTask.value) return
  if (!reassignForm.newVehicleId) {
    message.warning('请选择新车辆')
    return
  }
  if (reassignForm.reason.length < 5) {
    message.warning('改派原因至少5个字符')
    return
  }
  reassignLoading.value = true
  try {
    await reassignTask(
      currentTask.value.taskId,
      {
        vehicleId: reassignForm.newVehicleId,
        remark: reassignForm.reason,
      },
      parkScope.selectedParkId,
    )
    message.success('改派成功')
    reassignModalVisible.value = false
    fetchData()
  } catch {
    // handled by interceptor
  } finally {
    reassignLoading.value = false
  }
}

async function handleCancel(record: TaskAdminListItem) {
  if (isActionBusy(record.taskId)) return
  actionTaskIds.value.add(record.taskId)
  try {
    await cancelTask(record.taskId, '任务列表取消', parkScope.selectedParkId)
    message.success('任务已取消')
    fetchData()
  } catch {
    // handled by interceptor
  } finally {
    actionTaskIds.value.delete(record.taskId)
  }
}

onMounted(() => {
  const statusParam = route.query.status as string
  if (statusParam && taskStatusMap[statusParam as TaskStatus]) {
    queryForm.status = statusParam as TaskStatus
  }
  fetchData()
  stopRealtimeRefresh = realtimeStore.subscribeRefresh(() => {
    if (!store.loading) return fetchData()
  })
})

onUnmounted(() => {
  stopRealtimeRefresh?.()
})

watch(
  () => parkScope.scopeVersion,
  () => {
    pageNo.value = 1
    fetchData()
  },
)
</script>

<style scoped lang="less">
@mobile-break: 768px;

.metadata-tag {
  color: var(--fsd-text-secondary);
  border-color: var(--fsd-border);
  background: var(--fsd-bg-hover);
}

.action-status {
  color: var(--fsd-text-secondary);
  font-size: var(--fsd-text-xs);
  white-space: nowrap;
}

.link-cell {
  color: var(--fsd-accent);
  font-family: 'JetBrains Mono', monospace;
  font-size: 13px;

  &:hover {
    text-decoration: underline;
  }
}

.text-muted {
  color: var(--fsd-text-tertiary);
}

.mono-text {
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
  color: var(--fsd-text-secondary);
}

.action-cell {
  display: flex;
  align-items: center;
  gap: 0;

  @media (max-width: @mobile-break) {
    flex-direction: column;
    gap: 4px;
  }
}
</style>
