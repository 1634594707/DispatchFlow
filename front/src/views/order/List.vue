<template>
  <PageContainer title="订单管理" subtitle="管理所有调度订单">
    <template #actions>
      <a-button v-if="authStore.canWrite" type="primary" @click="createModalOpen = true">
        创建短驳订单
      </a-button>
      <a-button @click="handleRefresh">
        <ReloadOutlined /> 刷新
      </a-button>
    </template>

    <QueryFilterCard
      title="筛选条件"
      :result-summary="`共 ${store.total} 条结果`"
      :active-chips="activeFilterChips"
      @remove="removeFilterChip"
      @clear="handleReset"
    >
      <a-select
        v-model:value="queryForm.status"
        placeholder="订单状态"
        allow-clear
        style="width: 160px;"
      >
        <a-select-option v-for="(cfg, key) in orderStatusMap" :key="key" :value="key">
          {{ cfg.label }}
        </a-select-option>
      </a-select>
      <a-select
        v-model:value="queryForm.deliveryZone"
        placeholder="配送区域"
        allow-clear
        style="width: 140px;"
      >
        <a-select-option value="GEO_DELIVERY">地理配送</a-select-option>
        <a-select-option value="SCHEMATIC">园区内部</a-select-option>
      </a-select>
      <a-input
        v-model:value="queryForm.orderNo"
        placeholder="订单编号"
        allow-clear
        style="width: 200px;"
      />
      <a-button type="primary" @click="handleSearch">
        <SearchOutlined /> 查询
      </a-button>
      <a-button @click="handleReset">重置</a-button>
      <template #extra>
        <a-button :disabled="store.total === 0" @click="handleExport">
          <DownloadOutlined /> 导出
        </a-button>
      </template>
    </QueryFilterCard>

    <!-- V9-UI3: Skeleton screen for initial load -->
    <SkeletonLoader v-if="store.loading && store.list.length === 0" variant="table" :rows="6" />
    <a-table
      v-else
      :columns="columns"
      :data-source="store.list"
      :loading="store.loading"
      :pagination="pagination"
      row-key="orderId"
      size="middle"
      :scroll="{ x: 'max-content' }"
      @change="handleTableChange"
    >
      <template #emptyText>
        <EmptyState description="暂无订单，可创建短驳订单或调整筛选条件" />
      </template>
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'orderNo'">
          <router-link :to="`/orders/${record.orderId}`" class="link-cell">
            {{ record.orderNo }}
          </router-link>
        </template>
        <template v-else-if="column.dataIndex === 'status'">
          <StatusBadge :status="record.status" type="order" />
        </template>
        <template v-else-if="column.dataIndex === 'priority'">
          <a-tag :color="priorityColor(record.priority)">
            {{ record.priority }}
          </a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'deliveryZone'">
          <a-tag v-if="record.deliveryZone === 'GEO_DELIVERY'" color="processing">地理配送</a-tag>
          <a-tag v-else-if="record.deliveryZone === 'SCHEMATIC'" color="success">园区内部</a-tag>
          <span v-else class="text-muted">-</span>
        </template>
        <template v-else-if="column.dataIndex === 'dispatchTaskId'">
          <router-link
            v-if="record.dispatchTaskId"
            :to="`/tasks/${record.dispatchTaskId}`"
            class="link-cell"
          >
            {{ record.dispatchTaskId }}
          </router-link>
          <span v-else class="text-muted">-</span>
        </template>
        <template v-else-if="column.dataIndex === 'createdAt'">
          <span class="mono-text">{{ formatTime(record.createdAt) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'updatedAt'">
          <span class="mono-text">{{ formatTime(record.updatedAt) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'action'">
          <div class="action-cell">
            <a-button type="link" size="small" @click="router.push(`/orders/${record.orderId}`)">
              查看
            </a-button>
            <a-popconfirm
              v-if="authStore.canWrite && canCancel(record.status)"
              title="确认取消该订单？"
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

    <ParkDeliveryOrderModal
      v-model:open="createModalOpen"
      :park-id="parkScope.selectedParkId"
      @created="handleRefresh"
    />
  </PageContainer>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import { ReloadOutlined, SearchOutlined, DownloadOutlined } from '@ant-design/icons-vue'
import PageContainer from '@/components/common/PageContainer.vue'
import QueryFilterCard from '@/components/common/QueryFilterCard.vue'
import type { FilterChip } from '@/components/common/QueryFilterCard.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import SkeletonLoader from '@/components/common/SkeletonLoader.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import ParkDeliveryOrderModal from '@/components/park/ParkDeliveryOrderModal.vue'
import { useOrderStore } from '@/stores/order'
import { useParkScopeStore } from '@/stores/parkScope'
import { useAuthStore } from '@/stores/auth'
import { orderStatusMap } from '@/constants/statusMap'
import { OrderStatus } from '@/constants/enums'
import { DEFAULT_PAGE_SIZE } from '@/config'
import { cancelOrder } from '@/api/order'
import dayjs from 'dayjs'
import type { OrderAdminListItem, OrderDeliveryZone } from '@/types/order'

const router = useRouter()
const route = useRoute()
const store = useOrderStore()
const parkScope = useParkScopeStore()
const authStore = useAuthStore()

const queryForm = reactive({
  status: undefined as OrderStatus | undefined,
  deliveryZone: undefined as OrderDeliveryZone | undefined,
  orderNo: '',
})

const pageNo = ref(1)
const pageSize = ref(DEFAULT_PAGE_SIZE)
const createModalOpen = ref(false)
let silentRefreshTimer: ReturnType<typeof setInterval> | null = null

const ACTIVE_ORDER_STATUSES: OrderStatus[] = [
  OrderStatus.WAITING_DISPATCH,
  OrderStatus.DISPATCHED,
  OrderStatus.IN_PROGRESS,
]

const columns = [
  { title: '订单编号', dataIndex: 'orderNo', width: 220 },
  { title: '状态', dataIndex: 'status', width: 120 },
  { title: '优先级', dataIndex: 'priority', width: 80 },
  { title: '配送区域', dataIndex: 'deliveryZone', width: 110 },
  { title: '关联任务', dataIndex: 'dispatchTaskId', width: 100 },
  { title: '创建时间', dataIndex: 'createdAt', width: 180 },
  { title: '更新时间', dataIndex: 'updatedAt', width: 180 },
  { title: '操作', dataIndex: 'action', width: 140, fixed: 'right' as const },
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
      label: `状态：${orderStatusMap[queryForm.status]?.label || queryForm.status}`,
    })
  }
  if (queryForm.deliveryZone) {
    const zoneLabel = queryForm.deliveryZone === 'GEO_DELIVERY' ? '地理配送' : '园区内部'
    chips.push({ key: 'deliveryZone', label: `配送区域：${zoneLabel}` })
  }
  if (queryForm.orderNo.trim()) {
    chips.push({ key: 'orderNo', label: `编号：${queryForm.orderNo.trim()}` })
  }
  return chips
})

function removeFilterChip(key: string) {
  if (key === 'status') queryForm.status = undefined
  if (key === 'deliveryZone') queryForm.deliveryZone = undefined
  if (key === 'orderNo') queryForm.orderNo = ''
  handleSearch()
}

function priorityColor(p: string) {
  const map: Record<string, string> = { P0: 'red', P1: 'orange', P2: 'blue', P3: 'default' }
  return map[p] || 'default'
}

function canCancel(status: OrderStatus) {
  return [OrderStatus.WAITING_DISPATCH, OrderStatus.DISPATCHED].includes(status)
}

function formatTime(t: string) {
  return dayjs(t).format('YYYY-MM-DD HH:mm:ss')
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
  queryForm.deliveryZone = undefined
  queryForm.orderNo = ''
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

async function handleCancel(record: OrderAdminListItem) {
  try {
    await cancelOrder(record.orderId, '订单列表取消')
    message.success('订单已取消')
    fetchData()
  } catch {
    // handled by interceptor
  }
}

function handleExport() {
  const base = import.meta.env.VITE_API_BASE_URL || ''
  const params = new URLSearchParams({ dataset: 'orders', period: 'week' })
  if (parkScope.selectedParkId) params.set('parkId', String(parkScope.selectedParkId))
  window.open(`${base}/api/admin/analytics/export/csv?${params.toString()}`, '_blank')
}

function hasActiveOrdersInList() {
  return store.list.some(item => ACTIVE_ORDER_STATUSES.includes(item.status))
}

function syncSilentRefresh() {
  if (silentRefreshTimer) {
    clearInterval(silentRefreshTimer)
    silentRefreshTimer = null
  }
  if (hasActiveOrdersInList()) {
    silentRefreshTimer = setInterval(() => {
      if (!store.loading) fetchData()
    }, 10_000)
  }
}

onMounted(() => {
  const statusParam = route.query.status as string
  if (statusParam && orderStatusMap[statusParam as OrderStatus]) {
    queryForm.status = statusParam as OrderStatus
  }
  fetchData()
})

onUnmounted(() => {
  if (silentRefreshTimer) clearInterval(silentRefreshTimer)
})

watch(
  () => parkScope.scopeVersion,
  () => {
    pageNo.value = 1
    fetchData()
  },
)

watch(
  () => store.list,
  () => syncSilentRefresh(),
  { deep: true },
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
