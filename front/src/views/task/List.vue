<template>
  <PageContainer title="调度任务" subtitle="管理所有调度任务">
    <template #actions>
      <a-button @click="handleRefresh">
        <ReloadOutlined /> 刷新
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
              v-if="canDispatch(record.status)"
              type="link"
              size="small"
              @click="openDispatchModal(record)"
            >
              派单
            </a-button>
            <a-button
              v-if="canReassign(record.status)"
              type="link"
              size="small"
              @click="openReassignModal(record)"
            >
              改派
            </a-button>
            <a-popconfirm
              v-if="canCancel(record.status)"
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
      @ok="handleDispatch"
      :confirmLoading="dispatchLoading"
    >
      <a-form layout="vertical">
        <a-form-item label="选择车辆" required>
          <a-select
            v-model:value="dispatchForm.vehicleId"
            placeholder="请选择在线空闲车辆"
            show-search
            :filter-option="filterOption"
          >
            <a-select-option :value="100">VH-001 (在线·空闲)</a-select-option>
            <a-select-option :value="101">VH-002 (在线·空闲)</a-select-option>
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
      @ok="handleReassign"
      :confirmLoading="reassignLoading"
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
          >
            <a-select-option :value="101">VH-002 (在线·空闲)</a-select-option>
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
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import { ReloadOutlined, SearchOutlined } from '@ant-design/icons-vue'
import PageContainer from '@/components/common/PageContainer.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import { useTaskStore } from '@/stores/task'
import { taskStatusMap } from '@/constants/statusMap'
import { TaskStatus } from '@/constants/enums'
import { DEFAULT_PAGE_SIZE } from '@/config'
import dayjs from 'dayjs'
import type { TaskAdminListItem } from '@/types/task'

const router = useRouter()
const route = useRoute()
const store = useTaskStore()

const queryForm = reactive({
  status: undefined as TaskStatus | undefined,
  taskNo: '',
  orderId: undefined as number | undefined,
  vehicleId: undefined as number | undefined,
})

const pageNo = ref(1)
const pageSize = ref(DEFAULT_PAGE_SIZE)

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
  return status !== TaskStatus.EXECUTING
}

function filterOption(input: string, option: any) {
  return option.children?.[0]?.toLowerCase().includes(input.toLowerCase())
}

function fetchData() {
  store.fetchList({
    ...queryForm,
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

function handleTableChange(pag: any) {
  pageNo.value = pag.current
  pageSize.value = pag.pageSize
  fetchData()
}

const dispatchModalVisible = ref(false)
const dispatchLoading = ref(false)
const currentTask = ref<TaskAdminListItem | null>(null)
const dispatchForm = reactive({ vehicleId: undefined as number | undefined, remark: '' })

function openDispatchModal(record: TaskAdminListItem) {
  currentTask.value = record
  dispatchForm.vehicleId = undefined
  dispatchForm.remark = ''
  dispatchModalVisible.value = true
}

async function handleDispatch() {
  if (!dispatchForm.vehicleId) {
    message.warning('请选择车辆')
    return
  }
  dispatchLoading.value = true
  try {
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
}

async function handleReassign() {
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
  message.success('任务已取消')
  fetchData()
}

onMounted(() => {
  const statusParam = route.query.status as string
  if (statusParam && taskStatusMap[statusParam as TaskStatus]) {
    queryForm.status = statusParam as TaskStatus
  }
  fetchData()
})
</script>

<style scoped lang="less">
.search-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
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
}
</style>
