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
      <template #emptyText>
        <EmptyState description="暂无车辆，可新建仿真车或调整筛选条件" />
      </template>
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
        <template v-if="form.linkMode === 'VDA5050'">
          <a-form-item label="Manufacturer" required>
            <a-input v-model:value="form.vdaManufacturer" placeholder="如 DispatchFlow" />
          </a-form-item>
          <a-form-item label="Serial Number" required>
            <a-input v-model:value="form.vdaSerialNumber" placeholder="如 AGV-001" />
          </a-form-item>
          <a-form-item label="Interface">
            <a-input v-model:value="form.vdaInterfaceName" placeholder="默认 uagv/v2" />
          </a-form-item>
        </template>
        <a-form-item label="备注"><a-textarea v-model:value="form.remark" :rows="2" /></a-form-item>
      </a-form>
    </a-modal>
  </PageContainer>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ReloadOutlined, SearchOutlined, PlusOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import PageContainer from '@/components/common/PageContainer.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import { useVehicleStore } from '@/stores/vehicle'
import { useAuthStore } from '@/stores/auth'
import { useParkScopeStore } from '@/stores/parkScope'
import { createVehicle, updateVehicle, getVehicleDetail } from '@/api/vehicle'
import { onlineStatusMap, dispatchStatusMap } from '@/constants/statusMap'
import { DispatchStatus } from '@/constants/enums'
import type { OnlineStatus } from '@/constants/enums'
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
const parkScope = useParkScopeStore()

const modalOpen = ref(false)
const saving = ref(false)
const editing = ref<VehicleAdminListItem | null>(null)
const form = reactive({
  vehicleCode: '',
  vehicleName: '',
  vehicleType: 'AGV',
  linkMode: 'SIM',
  vdaManufacturer: '',
  vdaSerialNumber: '',
  vdaInterfaceName: 'uagv/v2',
  remark: '',
})

const linkModeOptions = [
  { label: '仿真 SIM', value: 'SIM' },
  { label: '真实 REAL (HTTP 遥测)', value: 'REAL' },
  { label: 'VDA5050 MQTT', value: 'VDA5050' },
]

const queryForm = reactive({
  onlineStatus: undefined as OnlineStatus | undefined,
  dispatchStatus: undefined as DispatchStatus | undefined,
  vehicleCode: '',
})

const pageNo = ref(1)
const pageSize = ref(DEFAULT_PAGE_SIZE)
let silentRefreshTimer: ReturnType<typeof setInterval> | null = null

const ACTIVE_DISPATCH_STATUSES: DispatchStatus[] = [
  DispatchStatus.BUSY,
]

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

function resetForm() {
  form.vehicleCode = ''
  form.vehicleName = ''
  form.vehicleType = 'AGV'
  form.linkMode = 'SIM'
  form.vdaManufacturer = ''
  form.vdaSerialNumber = ''
  form.vdaInterfaceName = 'uagv/v2'
  form.remark = ''
}

function openCreate() {
  editing.value = null
  resetForm()
  modalOpen.value = true
}

async function openEdit(record: VehicleAdminListItem) {
  editing.value = record
  resetForm()
  const detail = (await getVehicleDetail(record.vehicleId)).data
  form.vehicleCode = detail.vehicleCode
  form.vehicleName = detail.vehicleName
  form.vehicleType = detail.vehicleType || 'AGV'
  form.linkMode = detail.linkMode || 'SIM'
  form.vdaManufacturer = detail.vdaManufacturer || ''
  form.vdaSerialNumber = detail.vdaSerialNumber || ''
  form.vdaInterfaceName = detail.vdaInterfaceName || 'uagv/v2'
  form.remark = detail.remark || ''
  modalOpen.value = true
}

async function handleSave() {
  if (!form.vehicleCode || !form.vehicleName) {
    message.warning('请填写完整信息')
    return
  }
  if (form.linkMode === 'VDA5050' && (!form.vdaManufacturer || !form.vdaSerialNumber)) {
    message.warning('VDA5050 车辆须填写 manufacturer 与 serialNumber')
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

function hasActiveVehiclesInList() {
  return store.list.some(item =>
    ACTIVE_DISPATCH_STATUSES.includes(item.dispatchStatus)
    || (item.onlineStatus === 'ONLINE' && item.currentTaskId != null),
  )
}

function syncSilentRefresh() {
  if (silentRefreshTimer) {
    clearInterval(silentRefreshTimer)
    silentRefreshTimer = null
  }
  if (hasActiveVehiclesInList()) {
    silentRefreshTimer = setInterval(() => {
      if (!store.loading) fetchData()
    }, 10_000)
  }
}

onMounted(() => {
  const onlineParam = route.query.onlineStatus as string
  if (onlineParam) {
    queryForm.onlineStatus = onlineParam as OnlineStatus
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
