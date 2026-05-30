<template>
  <PageContainer title="车辆管理" subtitle="监控所有车辆状态">
    <template #actions>
      <a-space>
        <a-button v-if="authStore.isAdmin" type="primary" @click="openCreate">
          <PlusOutlined /> 新建车辆
        </a-button>
        <a-button @click="handleRefresh">
          <ReloadOutlined /> 刷新
        </a-button>
      </a-space>
    </template>

    <div class="search-bar">
      <a-select
        v-model:value="queryForm.onlineStatus"
        placeholder="在线状态"
        allow-clear
        style="width: 140px;"
      >
        <a-select-option v-for="(cfg, key) in onlineStatusMap" :key="key" :value="key">
          {{ cfg.label }}
        </a-select-option>
      </a-select>
      <a-select
        v-model:value="queryForm.dispatchStatus"
        placeholder="调度状态"
        allow-clear
        style="width: 140px;"
      >
        <a-select-option v-for="(cfg, key) in dispatchStatusMap" :key="key" :value="key">
          {{ cfg.label }}
        </a-select-option>
      </a-select>
      <a-input
        v-model:value="queryForm.vehicleCode"
        placeholder="车辆编号"
        allow-clear
        style="width: 180px;"
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
      row-key="vehicleId"
      size="middle"
      :scroll="{ x: 'max-content' }"
      :rowClassName="rowClassName"
      @change="handleTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'vehicleCode'">
          <router-link :to="`/vehicles/${record.vehicleId}`" class="link-cell">
            {{ record.vehicleCode }}
          </router-link>
        </template>
        <template v-else-if="column.dataIndex === 'onlineStatus'">
          <StatusBadge :status="record.onlineStatus" type="online" />
        </template>
        <template v-else-if="column.dataIndex === 'dispatchStatus'">
          <StatusBadge :status="record.dispatchStatus" type="dispatch" />
        </template>
        <template v-else-if="column.dataIndex === 'currentTaskId'">
          <router-link
            v-if="record.currentTaskId"
            :to="`/tasks/${record.currentTaskId}`"
            class="link-cell"
          >
            {{ record.currentTaskId }}
          </router-link>
          <span v-else class="text-muted">-</span>
        </template>
        <template v-else-if="column.dataIndex === 'batteryLevel'">
          <a-progress
            :percent="record.batteryLevel"
            :stroke-color="record.batteryLevel < 20 ? '#FF3D71' : '#00E676'"
            size="small"
            :style="{ width: '80px' }"
          />
          <span class="battery-text" :class="{ 'battery-low': record.batteryLevel < 20 }">
            {{ record.batteryLevel }}%
          </span>
        </template>
        <template v-else-if="column.dataIndex === 'lastReportTime'">
          <a-tooltip :title="formatTime(record.lastReportTime)">
            <span class="mono-text">{{ formatRelative(record.lastReportTime) }}</span>
          </a-tooltip>
        </template>
        <template v-else-if="column.dataIndex === 'action'">
          <a-space>
            <a-button type="link" size="small" @click="router.push(`/vehicles/${record.vehicleId}`)">
              查看详情
            </a-button>
            <a-button v-if="authStore.isAdmin" type="link" size="small" @click="openEdit(record)">编辑</a-button>
          </a-space>
        </template>
      </template>
    </a-table>

    <a-modal v-model:open="modalOpen" :title="editing ? '编辑车辆' : '新建车辆'" :confirm-loading="saving" @ok="handleSave">
      <a-form layout="vertical">
        <a-form-item label="车辆编码" required><a-input v-model:value="form.vehicleCode" /></a-form-item>
        <a-form-item label="车辆名称" required><a-input v-model:value="form.vehicleName" /></a-form-item>
        <a-form-item label="车辆类型"><a-input v-model:value="form.vehicleType" placeholder="如 AGV" /></a-form-item>
        <a-form-item label="连接模式">
          <a-select v-model:value="form.linkMode" :options="linkModeOptions" />
        </a-form-item>
        <a-form-item label="备注"><a-textarea v-model:value="form.remark" :rows="2" /></a-form-item>
      </a-form>
    </a-modal>
  </PageContainer>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ReloadOutlined, SearchOutlined, PlusOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import PageContainer from '@/components/common/PageContainer.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import { useVehicleStore } from '@/stores/vehicle'
import { useAuthStore } from '@/stores/auth'
import { createVehicle, updateVehicle } from '@/api/vehicle'
import { onlineStatusMap, dispatchStatusMap } from '@/constants/statusMap'
import type { OnlineStatus, DispatchStatus } from '@/constants/enums'
import { DEFAULT_PAGE_SIZE } from '@/config'
import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import 'dayjs/locale/zh-cn'
import type { VehicleAdminListItem } from '@/types/vehicle'

dayjs.extend(relativeTime)
dayjs.locale('zh-cn')

const router = useRouter()
const route = useRoute()
const store = useVehicleStore()
const authStore = useAuthStore()

const modalOpen = ref(false)
const saving = ref(false)
const editing = ref<VehicleAdminListItem | null>(null)
const form = reactive({
  vehicleCode: '',
  vehicleName: '',
  vehicleType: 'AGV',
  linkMode: 'SIM',
  remark: '',
})

const linkModeOptions = [
  { label: '仿真 SIM', value: 'SIM' },
  { label: '真实 REAL', value: 'REAL' },
]

const queryForm = reactive({
  onlineStatus: undefined as OnlineStatus | undefined,
  dispatchStatus: undefined as DispatchStatus | undefined,
  vehicleCode: '',
})

const pageNo = ref(1)
const pageSize = ref(DEFAULT_PAGE_SIZE)

const columns = [
  { title: '车辆编号', dataIndex: 'vehicleCode', width: 140 },
  { title: '车辆名称', dataIndex: 'vehicleName', width: 140 },
  { title: '在线状态', dataIndex: 'onlineStatus', width: 100 },
  { title: '调度状态', dataIndex: 'dispatchStatus', width: 100 },
  { title: '当前任务', dataIndex: 'currentTaskId', width: 100 },
  { title: '电量', dataIndex: 'batteryLevel', width: 140 },
  { title: '最后回传', dataIndex: 'lastReportTime', width: 160 },
  { title: '操作', dataIndex: 'action', width: 100, fixed: 'right' as const },
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

function formatRelative(t: string) {
  return dayjs(t).fromNow()
}

function isOfflineOverFiveMin(record: VehicleAdminListItem) {
  if (record.onlineStatus !== 'OFFLINE') return false
  return dayjs().diff(dayjs(record.lastReportTime), 'minute') > 5
}

function rowClassName(record: VehicleAdminListItem) {
  return isOfflineOverFiveMin(record) ? 'row-offline-alert' : ''
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
  queryForm.onlineStatus = undefined
  queryForm.dispatchStatus = undefined
  queryForm.vehicleCode = ''
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

function openCreate() {
  editing.value = null
  form.vehicleCode = ''
  form.vehicleName = ''
  form.vehicleType = 'AGV'
  form.linkMode = 'SIM'
  form.remark = ''
  modalOpen.value = true
}

function openEdit(record: VehicleAdminListItem) {
  editing.value = record
  form.vehicleCode = record.vehicleCode
  form.vehicleName = record.vehicleName
  form.vehicleType = 'AGV'
  form.linkMode = 'SIM'
  form.remark = ''
  modalOpen.value = true
}

async function handleSave() {
  if (!form.vehicleCode || !form.vehicleName) {
    message.warning('请填写完整信息')
    return
  }
  saving.value = true
  try {
    const payload = { ...form }
    if (editing.value) {
      await updateVehicle(editing.value.vehicleId, payload)
      message.success('车辆已更新')
    } else {
      await createVehicle(payload)
      message.success('车辆已创建')
    }
    modalOpen.value = false
    fetchData()
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  const onlineParam = route.query.onlineStatus as string
  if (onlineParam) {
    queryForm.onlineStatus = onlineParam as OnlineStatus
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

.battery-text {
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
  color: var(--fsd-text-secondary);
  margin-left: 8px;
}

.battery-low {
  color: var(--fsd-error);
  font-weight: 600;
}

:deep(.row-offline-alert) {
  background: #FFF2F0 !important;
}
</style>
