<template>
  <PageContainer title="车辆管理" subtitle="监控所有车辆状态">
    <template #actions>
      <a-button @click="handleRefresh">
        <ReloadOutlined /> 刷新
      </a-button>
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
          <a-button type="link" size="small" @click="router.push(`/vehicles/${record.vehicleId}`)">
            查看详情
          </a-button>
        </template>
      </template>
    </a-table>
  </PageContainer>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ReloadOutlined, SearchOutlined } from '@ant-design/icons-vue'
import PageContainer from '@/components/common/PageContainer.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import { useVehicleStore } from '@/stores/vehicle'
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
