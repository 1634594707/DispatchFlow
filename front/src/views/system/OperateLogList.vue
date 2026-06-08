<template>
  <PageContainer title="操作日志" subtitle="调度任务关键操作审计与追溯">
    <template #actions>
      <a-button v-if="activeTab === 'operate'" @click="handleExport">
        <DownloadOutlined /> 导出 CSV
      </a-button>
    </template>

    <a-tabs v-model:activeKey="activeTab" @change="handleTabChange">
      <a-tab-pane key="operate" tab="操作日志" />
      <a-tab-pane key="config" tab="配置审计" />
    </a-tabs>

    <!-- 操作日志 -->
    <template v-if="activeTab === 'operate'">
      <div class="search-bar">
        <a-input-number v-model:value="query.taskId" placeholder="任务 ID" style="width: 120px" />
        <a-input-number v-model:value="query.vehicleId" placeholder="车辆 ID" style="width: 120px" />
        <a-select
          v-model:value="query.operateType"
          placeholder="操作类型"
          allow-clear
          style="width: 160px"
          :options="operateTypeOptions"
        />
        <a-input v-model:value="query.operatorName" placeholder="操作人" allow-clear style="width: 140px" />
        <a-range-picker v-model:value="timeRange" show-time style="width: 360px" />
        <a-button type="primary" @click="handleSearch"><SearchOutlined /> 查询</a-button>
        <a-button @click="handleReset">重置</a-button>
      </div>

      <a-table
        :columns="columns"
        :data-source="logs"
        :loading="loading"
        row-key="id"
        :pagination="pagination"
        @change="handleTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'operateType'">
            <a-tag>{{ operateTypeLabel(record.operateType) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'taskNo'">
            <router-link v-if="record.taskId" :to="`/tasks/${record.taskId}`" class="link">
              {{ record.taskNo || record.taskId }}
            </router-link>
          </template>
          <template v-else-if="column.key === 'statusChange'">
            <span class="mono">{{ record.beforeStatus || '-' }} → {{ record.afterStatus || '-' }}</span>
          </template>
          <template v-else-if="column.key === 'createdAt'">
            {{ formatTime(record.createdAt) }}
          </template>
        </template>
      </a-table>
    </template>

    <!-- 配置审计 -->
    <template v-if="activeTab === 'config'">
      <div class="search-bar">
        <a-input v-model:value="configQuery.configKey" placeholder="配置项" allow-clear style="width: 180px" />
        <a-input v-model:value="configQuery.changeReason" placeholder="变更原因" allow-clear style="width: 180px" />
        <a-select
          v-model:value="configQuery.operateType"
          placeholder="操作类型"
          allow-clear
          style="width: 200px"
          :options="configOperateTypeOptions"
        />
        <a-input v-model:value="configQuery.operatorName" placeholder="操作人" allow-clear style="width: 140px" />
        <a-range-picker v-model:value="configTimeRange" show-time style="width: 360px" />
        <a-button type="primary" @click="handleConfigSearch"><SearchOutlined /> 查询</a-button>
        <a-button @click="handleConfigReset">重置</a-button>
      </div>

      <a-table
        :columns="configColumns"
        :data-source="configLogs"
        :loading="configLoading"
        row-key="id"
        :pagination="configPagination"
        @change="handleConfigTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'operateType'">
            <a-tag :color="configOperateTypeColor(record.operateType)">{{ configOperateTypeLabel(record.operateType) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'valueChange'">
            <span class="mono">{{ record.beforeValue || '-' }} → {{ record.afterValue || '-' }}</span>
          </template>
          <template v-else-if="column.key === 'createdAt'">
            {{ formatTime(record.createdAt) }}
          </template>
        </template>
      </a-table>
    </template>
  </PageContainer>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { SearchOutlined, DownloadOutlined } from '@ant-design/icons-vue'
import dayjs, { type Dayjs } from 'dayjs'
import PageContainer from '@/components/common/PageContainer.vue'
import { queryOperateLogs, exportOperateLogs, fetchConfigAuditLogs } from '@/api/operateLog'
import type { OperateLogItem, OperateLogQueryRequest, ConfigAuditLogItem, ConfigAuditQueryRequest } from '@/types/operateLog'
import { DEFAULT_PAGE_SIZE } from '@/config'

const activeTab = ref('operate')

/* ---- 操作日志 ---- */
const loading = ref(false)
const logs = ref<OperateLogItem[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(DEFAULT_PAGE_SIZE)
const timeRange = ref<[Dayjs, Dayjs] | null>(null)

const query = reactive<OperateLogQueryRequest>({
  taskId: undefined,
  vehicleId: undefined,
  operateType: undefined,
  operatorName: '',
})

const operateTypeOptions = [
  'CREATE_TASK', 'AUTO_ASSIGN', 'MANUAL_ASSIGN', 'REASSIGN', 'CANCEL_TASK',
  'ENTER_MANUAL_PENDING', 'START_EXECUTE', 'FINISH_SUCCESS', 'FINISH_FAILED',
  'ISSUE_COMMAND', 'COMMAND_FAILED', 'EXCEPTION_RESOLVE',
].map((v) => ({ label: operateTypeLabel(v), value: v }))

const columns = [
  { title: '时间', key: 'createdAt', width: 170 },
  { title: '任务', key: 'taskNo', width: 140 },
  { title: '操作类型', key: 'operateType', width: 130 },
  { title: '状态变更', key: 'statusChange' },
  { title: '操作人', dataIndex: 'operatorName', key: 'operatorName', width: 120 },
  { title: '备注', dataIndex: 'operateRemark', key: 'operateRemark', ellipsis: true },
]

const pagination = computed(() => ({
  current: pageNo.value,
  pageSize: pageSize.value,
  total: total.value,
  showTotal: (t: number) => `共 ${t} 条`,
}))

function operateTypeLabel(type: string) {
  const map: Record<string, string> = {
    CREATE_TASK: '创建任务',
    AUTO_ASSIGN: '自动派车',
    MANUAL_ASSIGN: '手动派车',
    REASSIGN: '改派',
    CANCEL_TASK: '取消任务',
    ENTER_MANUAL_PENDING: '转人工',
    START_EXECUTE: '开始执行',
    FINISH_SUCCESS: '执行成功',
    FINISH_FAILED: '执行失败',
    ISSUE_COMMAND: '下发指令',
    COMMAND_FAILED: '指令失败',
    EXCEPTION_RESOLVE: '异常处置',
  }
  return map[type] || type
}

function formatTime(value: string) {
  return dayjs(value).format('YYYY-MM-DD HH:mm:ss')
}

function buildQuery(): OperateLogQueryRequest {
  return {
    ...query,
    startTime: timeRange.value?.[0]?.format('YYYY-MM-DD HH:mm:ss'),
    endTime: timeRange.value?.[1]?.format('YYYY-MM-DD HH:mm:ss'),
    pageNo: pageNo.value,
    pageSize: pageSize.value,
  }
}

async function loadData() {
  loading.value = true
  try {
    const res = await queryOperateLogs(buildQuery())
    logs.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pageNo.value = 1
  loadData()
}

function handleReset() {
  query.taskId = undefined
  query.vehicleId = undefined
  query.operateType = undefined
  query.operatorName = ''
  timeRange.value = null
  pageNo.value = 1
  loadData()
}

function handleTableChange(pag: { current: number; pageSize: number }) {
  pageNo.value = pag.current
  pageSize.value = pag.pageSize
  loadData()
}

async function handleExport() {
  await exportOperateLogs({ ...query, pageNo: 1, pageSize: 10000 })
}

/* ---- 配置审计 ---- */
const configLoading = ref(false)
const configLogs = ref<ConfigAuditLogItem[]>([])
const configTotal = ref(0)
const configPageNo = ref(1)
const configPageSize = ref(DEFAULT_PAGE_SIZE)
const configTimeRange = ref<[Dayjs, Dayjs] | null>(null)

const configQuery = reactive<ConfigAuditQueryRequest>({
  configKey: '',
  changeReason: '',
  operateType: undefined,
  operatorName: '',
})

const configOperateTypeOptions = [
  { label: '调度策略变更', value: 'DISPATCH_STRATEGY_CHANGE' },
  { label: '交通管制变更', value: 'TRAFFIC_CONTROL_CHANGE' },
  { label: '站点配置变更', value: 'STATION_CONFIG_CHANGE' },
  { label: '告警设置变更', value: 'ALERT_SETTING_CHANGE' },
]

const configColumns = [
  { title: '时间', key: 'createdAt', width: 170 },
  { title: '配置项', dataIndex: 'configKey', key: 'configKey', width: 180 },
  { title: '操作类型', key: 'operateType', width: 140 },
  { title: '变更详情', key: 'valueChange', minWidth: 200 },
  { title: '变更原因', dataIndex: 'changeReason', key: 'changeReason', ellipsis: true },
  { title: '操作人', dataIndex: 'operatorName', key: 'operatorName', width: 120 },
]

const configPagination = computed(() => ({
  current: configPageNo.value,
  pageSize: configPageSize.value,
  total: configTotal.value,
  showTotal: (t: number) => `共 ${t} 条`,
}))

function configOperateTypeLabel(type: string) {
  const map: Record<string, string> = {
    DISPATCH_STRATEGY_CHANGE: '调度策略变更',
    TRAFFIC_CONTROL_CHANGE: '交通管制变更',
    STATION_CONFIG_CHANGE: '站点配置变更',
    ALERT_SETTING_CHANGE: '告警设置变更',
  }
  return map[type] || type
}

function configOperateTypeColor(type: string) {
  const map: Record<string, string> = {
    DISPATCH_STRATEGY_CHANGE: 'purple',
    TRAFFIC_CONTROL_CHANGE: 'orange',
    STATION_CONFIG_CHANGE: 'cyan',
    ALERT_SETTING_CHANGE: 'red',
  }
  return map[type] || 'default'
}

function buildConfigQuery(): ConfigAuditQueryRequest {
  return {
    ...configQuery,
    startTime: configTimeRange.value?.[0]?.format('YYYY-MM-DD HH:mm:ss'),
    endTime: configTimeRange.value?.[1]?.format('YYYY-MM-DD HH:mm:ss'),
    pageNo: configPageNo.value,
    pageSize: configPageSize.value,
  }
}

async function loadConfigData() {
  configLoading.value = true
  try {
    const res = await fetchConfigAuditLogs(buildConfigQuery())
    configLogs.value = res.data.records
    configTotal.value = res.data.total
  } finally {
    configLoading.value = false
  }
}

function handleConfigSearch() {
  configPageNo.value = 1
  loadConfigData()
}

function handleConfigReset() {
  configQuery.configKey = ''
  configQuery.changeReason = ''
  configQuery.operateType = undefined
  configQuery.operatorName = ''
  configTimeRange.value = null
  configPageNo.value = 1
  loadConfigData()
}

function handleConfigTableChange(pag: { current: number; pageSize: number }) {
  configPageNo.value = pag.current
  configPageSize.value = pag.pageSize
  loadConfigData()
}

function handleTabChange() {
  if (activeTab.value === 'config' && configLogs.value.length === 0) {
    loadConfigData()
  } else if (activeTab.value === 'operate' && logs.value.length === 0) {
    loadData()
  }
}

onMounted(loadData)
</script>

<style scoped lang="less">
.search-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 16px;
}

.link {
  color: var(--fsd-accent);
  font-family: 'JetBrains Mono', monospace;
}

.mono {
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
}

@media (max-width: 768px) {
  .search-bar {
    flex-direction: column;
    gap: 8px;
  }
}
</style>