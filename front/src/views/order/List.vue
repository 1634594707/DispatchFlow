<template>
  <PageContainer title="订单管理" subtitle="管理所有调度订单">
    <template #actions>
      <a-button @click="handleRefresh">
        <ReloadOutlined /> 刷新
      </a-button>
    </template>

    <div class="search-bar">
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
    </div>

    <a-table
      :columns="columns"
      :data-source="store.list"
      :loading="store.loading"
      :pagination="pagination"
      row-key="orderId"
      size="middle"
      :scroll="{ x: 'max-content' }"
      @change="handleTableChange"
    >
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
              v-if="canCancel(record.status)"
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
  </PageContainer>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import { ReloadOutlined, SearchOutlined } from '@ant-design/icons-vue'
import PageContainer from '@/components/common/PageContainer.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import { useOrderStore } from '@/stores/order'
import { orderStatusMap } from '@/constants/statusMap'
import { OrderStatus } from '@/constants/enums'
import { DEFAULT_PAGE_SIZE } from '@/config'
import dayjs from 'dayjs'
import type { OrderAdminListItem } from '@/types/order'

const router = useRouter()
const route = useRoute()
const store = useOrderStore()

const queryForm = reactive({
  status: undefined as OrderStatus | undefined,
  orderNo: '',
})

const pageNo = ref(1)
const pageSize = ref(DEFAULT_PAGE_SIZE)

const columns = [
  { title: '订单编号', dataIndex: 'orderNo', width: 220 },
  { title: '状态', dataIndex: 'status', width: 120 },
  { title: '优先级', dataIndex: 'priority', width: 80 },
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

function handleCancel(record: OrderAdminListItem) {
  message.success('订单已取消')
  fetchData()
}

onMounted(() => {
  const statusParam = route.query.status as string
  if (statusParam && orderStatusMap[statusParam as OrderStatus]) {
    queryForm.status = statusParam as OrderStatus
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
