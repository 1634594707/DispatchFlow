<template>
  <PageContainer title="异常任务" subtitle="管理所有调度异常">
    <template #actions>
      <a-button @click="handleRefresh">
        <ReloadOutlined /> 刷新
      </a-button>
      <a-button @click="handleExport">
        <DownloadOutlined /> 导出
      </a-button>
      <a-divider type="vertical" />
      <a-button
        v-if="selectedRowKeys.length > 0"
        type="primary"
        @click="handleBatchClose"
      >
        批量关闭 ({{ selectedRowKeys.length }})
      </a-button>
      <a-button
        v-if="selectedRowKeys.length > 0"
        @click="handleBatchReassign"
      >
        批量重新派车 ({{ selectedRowKeys.length }})
      </a-button>
    </template>

    <div class="search-bar">
      <a-select
        v-model:value="queryForm.exceptionType"
        placeholder="异常类型"
        allow-clear
        style="width: 180px;"
      >
        <a-select-option v-for="(cfg, key) in exceptionTypeMap" :key="key" :value="key">
          {{ cfg.label }}
        </a-select-option>
      </a-select>
      <a-select
        v-model:value="queryForm.exceptionStatus"
        placeholder="处理状态"
        allow-clear
        style="width: 140px;"
      >
        <a-select-option v-for="(cfg, key) in exceptionStatusMap" :key="key" :value="key">
          {{ cfg.label }}
        </a-select-option>
      </a-select>
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

    <!-- V9-UI3: Skeleton screen for initial load -->
    <SkeletonLoader v-if="store.loading && store.list.length === 0" variant="table" :rows="6" />
    <a-table
      v-else
      :columns="columns"
      :data-source="store.list"
      :loading="store.loading"
      :pagination="pagination"
      row-key="id"
      size="middle"
      :row-selection="{
        selectedRowKeys,
        onChange: onSelectionChange,
        hideDefaultSelections: true,
        selections: [
          { key: 'all', text: '全选' },
          { key: 'none', text: '取消全选' },
        ],
      }"
      :scroll="{ x: 'max-content' }"
      @change="handleTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'exceptionType'">
          <span class="exception-type-cell">
            <ExclamationCircleOutlined style="color: var(--fsd-error);" />
            {{ getExceptionLabel(record.exceptionType) }}
          </span>
        </template>
        <template v-else-if="column.dataIndex === 'taskId'">
          <router-link v-if="record.taskId" :to="`/tasks/${record.taskId}`" class="link-cell">
            {{ record.taskId }}
          </router-link>
          <span v-else class="text-secondary">无</span>
        </template>
        <template v-else-if="column.dataIndex === 'exceptionMsg'">
          <a-tooltip :title="record.exceptionMsg">
            <span class="msg-truncate">{{ record.exceptionMsg }}</span>
          </a-tooltip>
        </template>
        <template v-else-if="column.dataIndex === 'aggCount'">
          <a-tag v-if="record.aggCount && record.aggCount > 1" color="orange">x{{ record.aggCount }}</a-tag>
          <span v-else>-</span>
        </template>
        <template v-else-if="column.dataIndex === 'occurTime'">
          <span class="mono-text">{{ formatTime(record.occurTime) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'exceptionStatus'">
          <StatusBadge :status="record.exceptionStatus" type="exception" />
        </template>
        <template v-else-if="column.dataIndex === 'resolverId'">
          {{ record.resolverId || '-' }}
        </template>
        <template v-else-if="column.dataIndex === 'action'">
          <div class="action-cell">
            <a-button
              v-if="authStore.canWrite && record.exceptionStatus === 'OPEN'"
              type="link"
              size="small"
              @click="openResolveDrawer(record)"
            >
              处理
            </a-button>
            <a-button v-if="record.taskId" type="link" size="small" @click="router.push(`/tasks/${record.taskId}`)">
              查看任务
            </a-button>
            <span v-else size="small" class="text-secondary">无关联任务</span>
          </div>
        </template>
      </template>
    </a-table>

    <a-drawer
      v-model:open="drawerVisible"
      title="处理异常"
      :width="600"
      placement="right"
    >
      <template v-if="currentException">
        <div class="drawer-section">
          <h4 class="section-title">异常信息</h4>
          <a-descriptions :column="1" size="small" bordered>
            <a-descriptions-item label="异常类型">
              <ExclamationCircleOutlined style="color: var(--fsd-error); margin-right: 4px;" />
              {{ getExceptionLabel(currentException.exceptionType) }}
            </a-descriptions-item>
            <a-descriptions-item label="关联任务">
              <router-link
                v-if="currentException.taskId"
                :to="`/tasks/${currentException.taskId}`"
                class="link"
                @click="drawerVisible = false"
              >
                任务#{{ currentException.taskId }}
              </router-link>
              <span v-else class="text-secondary">无关联任务</span>
            </a-descriptions-item>
            <a-descriptions-item label="发生时间">
              {{ formatTime(currentException.occurTime) }}
            </a-descriptions-item>
            <a-descriptions-item label="异常详情">
              {{ currentException.exceptionMsg }}
            </a-descriptions-item>
          </a-descriptions>
        </div>

        <a-divider />

        <div class="drawer-section">
          <h4 class="section-title">处理操作</h4>
          <a-form layout="vertical">
            <a-form-item label="处理结果" required>
              <a-radio-group v-model:value="resolveForm.action">
                <a-radio :disabled="!currentException?.taskId" value="REASSIGN">
                  <template #label>
                    <span>重新派单</span>
                    <a-tooltip v-if="!currentException?.taskId" title="无关联任务，无法重新派单">
                      <InfoCircleOutlined style="margin-left: 4px; color: #999;" />
                    </a-tooltip>
                  </template>
                </a-radio>
                <a-radio value="IGNORE">忽略异常</a-radio>
                <a-radio value="CONTACT">联系现场</a-radio>
                <a-radio :disabled="!currentException?.taskId" value="MARK_FAILED">
                  <template #label>
                    <span>标记失败</span>
                    <a-tooltip v-if="!currentException?.taskId" title="无关联任务">
                      <InfoCircleOutlined style="margin-left: 4px; color: #999;" />
                    </a-tooltip>
                  </template>
                </a-radio>
              </a-radio-group>
            </a-form-item>

            <a-form-item v-if="resolveForm.action === 'REASSIGN'" label="选择车辆">
              <a-select
                v-model:value="resolveForm.vehicleId"
                placeholder="请选择在线空闲车辆"
              >
                <a-select-option :value="100">VH-001 (在线·空闲)</a-select-option>
                <a-select-option :value="101">VH-002 (在线·空闲)</a-select-option>
              </a-select>
            </a-form-item>

            <a-form-item label="处理说明" required>
              <a-textarea
                v-model:value="resolveForm.remark"
                placeholder="请描述处理方案（至少10个字符）"
                :rows="4"
              />
            </a-form-item>

            <a-form-item>
              <a-button type="primary" :loading="resolveLoading" @click="handleResolve">
                提交处理
              </a-button>
            </a-form-item>
          </a-form>
        </div>
      </template>
    </a-drawer>
  </PageContainer>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import { ReloadOutlined, SearchOutlined, ExclamationCircleOutlined, DownloadOutlined, InfoCircleOutlined } from '@ant-design/icons-vue'
import PageContainer from '@/components/common/PageContainer.vue'
import SkeletonLoader from '@/components/common/SkeletonLoader.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import { useExceptionStore } from '@/stores/exception'
import { useParkScopeStore } from '@/stores/parkScope'
import { useAuthStore } from '@/stores/auth'
import { exceptionTypeMap, exceptionStatusMap } from '@/constants/statusMap'
import type { ExceptionStatus, ExceptionType } from '@/constants/enums'
import { DEFAULT_PAGE_SIZE } from '@/config'
import dayjs from 'dayjs'
import type { ExceptionAdminListItem } from '@/types/exception'

const router = useRouter()
const route = useRoute()
const store = useExceptionStore()
const parkScope = useParkScopeStore()
const authStore = useAuthStore()

const queryForm = reactive({
  exceptionType: undefined as string | undefined,
  exceptionStatus: undefined as ExceptionStatus | undefined,
  orderId: undefined as number | undefined,
  vehicleId: undefined as number | undefined,
})

const pageNo = ref(1)
const pageSize = ref(DEFAULT_PAGE_SIZE)

const columns = [
  { title: '异常ID', dataIndex: 'id', width: 80 },
  { title: '异常类型', dataIndex: 'exceptionType', width: 160 },
  { title: '关联任务', dataIndex: 'taskId', width: 100 },
  { title: '异常信息', dataIndex: 'exceptionMsg', width: 250, ellipsis: true },
  { title: '聚合次数', dataIndex: 'aggCount', width: 80 },
  { title: '发生时间', dataIndex: 'occurTime', width: 180 },
  { title: '处理状态', dataIndex: 'exceptionStatus', width: 100 },
  { title: '处理人', dataIndex: 'resolverId', width: 100 },
  { title: '操作', dataIndex: 'action', width: 160, fixed: 'right' as const },
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

function getExceptionLabel(type: string) {
  return exceptionTypeMap[type as keyof typeof exceptionTypeMap]?.label || type
}

function formatTime(t: string) {
  return dayjs(t).format('YYYY-MM-DD HH:mm:ss')
}

function fetchData() {
  store.fetchList({
    ...queryForm,
    exceptionType: queryForm.exceptionType as ExceptionType | undefined,
    parkId: parkScope.selectedParkId,
    pageNo: pageNo.value,
    pageSize: pageSize.value,
  }).then(() => openRouteException())
}

function handleSearch() {
  pageNo.value = 1
  fetchData()
}

function handleReset() {
  queryForm.exceptionType = undefined
  queryForm.exceptionStatus = undefined
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
  const params = new URLSearchParams({ dataset: 'exceptions', period: 'week' })
  if (parkScope.selectedParkId) params.set('parkId', String(parkScope.selectedParkId))
  window.open(`${base}/api/admin/analytics/export/csv?${params.toString()}`, '_blank')
}

function handleTableChange(pag: any) {
  pageNo.value = pag.current
  pageSize.value = pag.pageSize
  fetchData()
}

const drawerVisible = ref(false)
const resolveLoading = ref(false)
const currentException = ref<ExceptionAdminListItem | null>(null)
const resolveForm = reactive({
  action: 'REASSIGN',
  remark: '',
  vehicleId: undefined as number | undefined,
})

const selectedRowKeys = ref<number[]>([])
const batchResolveLoading = ref(false)

function onSelectionChange(keys: number[]) {
  selectedRowKeys.value = keys
}

async function handleBatchClose() {
  if (selectedRowKeys.value.length === 0) return
  const confirmed = await window.confirm(`确定要批量关闭 ${selectedRowKeys.value.length} 个异常吗？`)
  if (!confirmed) return
  batchResolveLoading.value = true
  try {
    await store.handleBatchResolve(selectedRowKeys.value, {
      resolverId: 'u1001',
      resolverName: '管理员',
      action: 'IGNORE',
      remark: '批量关闭异常',
    })
    message.success(`已批量关闭 ${selectedRowKeys.value.length} 个异常`)
    selectedRowKeys.value = []
    fetchData()
    store.fetchOpenCount()
  } catch {
    // handled by interceptor
  } finally {
    batchResolveLoading.value = false
  }
}

async function handleBatchReassign() {
  if (selectedRowKeys.value.length === 0) return
  const confirmed = await window.confirm(`确定要对 ${selectedRowKeys.value.length} 个异常执行批量重新派车吗？`)
  if (!confirmed) return
  batchResolveLoading.value = true
  try {
    await store.handleBatchResolve(selectedRowKeys.value, {
      resolverId: 'u1001',
      resolverName: '管理员',
      action: 'REASSIGN',
      remark: '批量重新派车',
    })
    message.success(`已批量处理 ${selectedRowKeys.value.length} 个异常`)
    selectedRowKeys.value = []
    fetchData()
    store.fetchOpenCount()
  } catch {
    // handled by interceptor
  } finally {
    batchResolveLoading.value = false
  }
}

function openResolveDrawer(record: ExceptionAdminListItem) {
  currentException.value = record
  resolveForm.action = 'REASSIGN'
  resolveForm.remark = ''
  resolveForm.vehicleId = undefined
  drawerVisible.value = true
}

function openRouteException() {
  const exceptionId = Number(route.query.exceptionId)
  if (!exceptionId || currentException.value?.id === exceptionId) return
  const item = store.list.find((record) => record.id === exceptionId)
  if (item) openResolveDrawer(item)
}

async function handleResolve() {
  if (!currentException.value) return
  if (resolveForm.action === 'REASSIGN' && !resolveForm.vehicleId) {
    message.warning('请选择车辆')
    return
  }
  if (resolveForm.remark.length < 10) {
    message.warning('处理说明至少10个字符')
    return
  }
  resolveLoading.value = true
  try {
    await store.handleResolve(currentException.value.id, {
      resolverId: 'u1001',
      resolverName: '管理员',
      action: resolveForm.action,
      remark: resolveForm.remark,
      vehicleId: resolveForm.action === 'REASSIGN' ? resolveForm.vehicleId : undefined,
    })
    message.success('异常已处理')
    drawerVisible.value = false
    fetchData()
    store.fetchOpenCount()
  } catch {
    // handled by interceptor
  } finally {
    resolveLoading.value = false
  }
}

onMounted(() => {
  const statusParam = route.query.status as string
  if (statusParam === 'OPEN') {
    queryForm.exceptionStatus = 'OPEN' as ExceptionStatus
  }
  fetchData()
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
.search-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.exception-type-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}

.link-cell {
  color: var(--fsd-accent);
  font-family: 'JetBrains Mono', monospace;
  font-size: 13px;

  &:hover {
    text-decoration: underline;
  }
}

.link {
  color: var(--fsd-accent);
  font-family: 'JetBrains Mono', monospace;
}

.msg-truncate {
  display: inline-block;
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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

.drawer-section {
  .section-title {
    font-size: 14px;
    font-weight: 600;
    color: var(--fsd-text-primary);
    margin: 0 0 12px;
  }
}
</style>
