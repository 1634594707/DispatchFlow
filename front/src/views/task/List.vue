<template>
  <PageContainer title="调度任务" subtitle="管理所有调度任务">
    <template #actions>
      <a-button @click="handleRefresh">
        <ReloadOutlined /> 刷新
      </a-button>
      <a-button @click="handleExport">
        <DownloadOutlined /> 导出
      </a-button>
    </template>

    <div class="search-bar">
      <a-select
        v-model:value="queryForm.status"
        placeholder="任务状态"
        allow-clear
        style="width: 160px;"
      >
        <a-select-option v-for="(cfg, key) in taskStatusMap" :key="key" :value="key">
          {{ cfg.label }}
        </a-select-option>
      </a-select>
      <a-input
        v-model:value="queryForm.taskNo"
        placeholder="任务编号"
        allow-clear
        style="width: 180px;"
      />
      <a-input-number
        v-model:value="queryForm.orderId"
        placeholder="订单ID"
        style="width: 140px;"
        :min="1"
      />
      <a-input-number
        v-model:value="queryForm.vehicleId"
        placeholder="车辆ID"
        style="width: 140px;"
        :min="1"
      />
      <a-button type="primary" @click="handleSearch">
        <SearchOutlined /> 查询
      </a-button>
      <a-button @click="handleReset">重置</a-button>
    </div>

    <a-table
      :columns="columns"
      :data-source="store.list"
      :loading="store.loading"
      :pagination="pagination"
      row-key="taskId"
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
          <a-tag :color="record.dispatchType === 'AUTO' ? 'cyan' : 'orange'">
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
            <a-button
              v-if="authStore.canWrite && canDispatch(record.status)"
              type="link"
              size="small"
              @click="openDispatchModal(record)"
            >
              派单
            </a-button>
            <a-button
              v-if="authStore.canWrite && canReassign(record.status)"
              type="link"
              size="small"
              @click="openReassignModal(record)"
            >
              改派
            </a-button>
            <a-popconfirm
              v-if="authStore.canWrite && canCancel(record.status)"
              title="确认取消该任务？"
              ok-text="确认"
              cancel-text="取消"
              @confirm="handleCancel(record)"
            >
              <a-button type="link" size="small" danger>取消</a-button>
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
        style="margin-bottom: 16px;"
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
  </PageContainer>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import { ReloadOutlined, SearchOutlined, DownloadOutlined } from '@ant-design/icons-vue'
import PageContainer from '@/components/common/PageContainer.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import { useTaskStore } from '@/stores/task'
import { useParkScopeStore } from '@/stores/parkScope'
import { useAuthStore } from '@/stores/auth'
import { taskStatusMap } from '@/constants/statusMap'
import { TaskStatus } from '@/constants/enums'
import { DEFAULT_PAGE_SIZE } from '@/config'
import { manualAssignTask, reassignTask, cancelTask } from '@/api/task'
import { getDispatchWorkbench } from '@/api/dispatch'
import dayjs from 'dayjs'
import type { TaskAdminListItem } from '@/types/task'
import type { ParkVehicleSnapshot } from '@/types/park'

const router = useRouter()
const route = useRoute()
const store = useTaskStore()
const parkScope = useParkScopeStore()
const authStore = useAuthStore()

const queryForm = reactive({
  status: undefined as TaskStatus | undefined,
  taskNo: '',
  orderId: undefined as number | undefined,
  vehicleId: undefined as number | undefined,
})

const pageNo = ref(1)
const pageSize = ref(DEFAULT_PAGE_SIZE)
let silentRefreshTimer: ReturnType<typeof setInterval> | null = null

const ACTIVE_TASK_STATUSES: TaskStatus[] = [
  TaskStatus.PENDING,
  TaskStatus.MANUAL_PENDING,
  TaskStatus.ASSIGNED,
  TaskStatus.EXECUTING,
]

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
  return [TaskStatus.PENDING, TaskStatus.MANUAL_PENDING, TaskStatus.ASSIGNED, TaskStatus.EXECUTING].includes(status)
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

function handleExport() {
  const base = import.meta.env.VITE_API_BASE_URL || ''
  const params = new URLSearchParams({ dataset: 'tasks', period: 'week' })
  if (parkScope.selectedParkId) params.set('parkId', String(parkScope.selectedParkId))
  window.open(`${base}/api/admin/analytics/export/csv?${params.toString()}`, '_blank')
}

function handleTableChange(pag: any) {
  pageNo.value = pag.current
  pageSize.value = pag.pageSize
  fetchData()
}

const dispatchModalVisible = ref(false)
const dispatchLoading = ref(false)
const assignableVehiclesLoading = ref(false)
const currentTask = ref<TaskAdminListItem | null>(null)
const dispatchForm = reactive({ vehicleId: undefined as number | undefined, remark: '' })
const assignableVehicles = ref<ParkVehicleSnapshot[]>([])

const reassignableVehicles = computed(() =>
  assignableVehicles.value.filter(vehicle => vehicle.vehicleId !== currentTask.value?.vehicleId),
)

function isAssignableVehicle(vehicle: ParkVehicleSnapshot) {
  return vehicle.onlineStatus === 'ONLINE' && vehicle.dispatchStatus === 'IDLE'
}

function formatVehicleOption(vehicle: ParkVehicleSnapshot) {
  return `${vehicle.vehicleCode} (${vehicle.onlineStatus === 'ONLINE' ? '在线' : vehicle.onlineStatus}·${vehicle.dispatchStatus === 'IDLE' ? '空闲' : vehicle.dispatchStatus})`
}

async function loadAssignableVehicles() {
  assignableVehiclesLoading.value = true
  try {
    const res = await getDispatchWorkbench(parkScope.selectedParkId)
    assignableVehicles.value = (res.data.vehicles || []).filter(isAssignableVehicle)
  } catch {
    assignableVehicles.value = []
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
    await manualAssignTask(currentTask.value.taskId, {
      vehicleId: dispatchForm.vehicleId,
      remark: dispatchForm.remark,
    })
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
    await reassignTask(currentTask.value.taskId, {
      vehicleId: reassignForm.newVehicleId,
      remark: reassignForm.reason,
    })
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
  try {
    await cancelTask(record.taskId, '任务列表取消')
    message.success('任务已取消')
    fetchData()
  } catch {
    // handled by interceptor
  }
}

function hasActiveTasksInList() {
  return store.list.some(item => ACTIVE_TASK_STATUSES.includes(item.status))
}

function syncSilentRefresh() {
  if (silentRefreshTimer) {
    clearInterval(silentRefreshTimer)
    silentRefreshTimer = null
  }
  if (hasActiveTasksInList()) {
    silentRefreshTimer = setInterval(() => {
      if (!store.loading) fetchData()
    }, 10_000)
  }
}

onMounted(() => {
  const statusParam = route.query.status as string
  if (statusParam && taskStatusMap[statusParam as TaskStatus]) {
    queryForm.status = statusParam as TaskStatus
  }
  fetchData()
})

onUnmounted(() => {
  if (silentRefreshTimer) clearInterval(silentRefreshTimer)
})

watch(
  () => store.list,
  () => syncSilentRefresh(),
  { deep: true },
)

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

.search-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;

  @media (max-width: @mobile-break) {
    flex-direction: column;
    align-items: stretch;

    > * {
      width: 100% !important;
    }
  }
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
