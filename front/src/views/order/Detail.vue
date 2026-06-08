<template>
  <PageContainer :title="`订单详情：${store.detail?.orderNo || route.params.orderId}`">
    <template #actions>
      <a-button v-if="canReorder" type="primary" ghost @click="handleReorder">
        <ReloadOutlined /> 再来一单
      </a-button>
      <router-link
        v-if="store.detail?.vehicleId"
        :to="trackingLink"
      >
        <a-button type="primary">地图追踪</a-button>
      </router-link>
      <a-button @click="router.back()">返回列表</a-button>
    </template>

    <a-spin :spinning="store.detailLoading">
      <template v-if="store.detail">
        <a-card title="基本信息" size="small">
          <a-descriptions :column="3" size="small" bordered>
            <a-descriptions-item label="订单编号">
              <span class="mono">{{ store.detail.orderNo }}</span>
            </a-descriptions-item>
            <a-descriptions-item label="外部单号">
              {{ store.detail.externalOrderNo || '-' }}
            </a-descriptions-item>
            <a-descriptions-item label="状态">
              <StatusBadge :status="store.detail.status" type="order" />
            </a-descriptions-item>
            <a-descriptions-item label="配送阶段" v-if="store.detail.runtimeStage">
              {{ parkDeliveryStageLabel(store.detail.runtimeStage) }}
            </a-descriptions-item>
            <a-descriptions-item label="来源类型">
              <a-tag>{{ store.detail.sourceType }}</a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="业务类型">
              {{ store.detail.bizType }}
            </a-descriptions-item>
            <a-descriptions-item label="优先级">
              <a-tag :color="priorityColor(store.detail.priority)">
                {{ store.detail.priority }}
              </a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="取货点">
              <span v-if="store.detail.pickupStationCode">
                {{ store.detail.pickupStationCode }} · {{ store.detail.pickupPointName }}
              </span>
              <span v-else class="text-muted">ID {{ store.detail.pickupPointId }}</span>
            </a-descriptions-item>
            <a-descriptions-item label="送货点">
              <span v-if="store.detail.dropoffStationCode">
                {{ store.detail.dropoffStationCode }} · {{ store.detail.dropoffPointName }}
              </span>
              <span v-else class="text-muted">ID {{ store.detail.dropoffPointId }}</span>
            </a-descriptions-item>
            <a-descriptions-item label="配送车辆">
              <router-link
                v-if="store.detail.vehicleId"
                :to="`/vehicles/${store.detail.vehicleId}`"
                class="link"
              >
                {{ store.detail.vehicleCode || store.detail.vehicleId }}
              </router-link>
              <span v-else class="text-muted">待分配</span>
            </a-descriptions-item>
            <a-descriptions-item label="关联任务">
              <router-link
                v-if="store.detail.dispatchTaskId"
                :to="`/tasks/${store.detail.dispatchTaskId}`"
                class="link"
              >
                {{ store.detail.dispatchTaskId }}
              </router-link>
              <span v-else class="text-muted">-</span>
            </a-descriptions-item>
            <a-descriptions-item label="备注" :span="3">
              {{ store.detail.remark || '-' }}
            </a-descriptions-item>
            <a-descriptions-item label="创建时间">
              <span class="mono">{{ formatTime(store.detail.createdAt) }}</span>
            </a-descriptions-item>
            <a-descriptions-item label="更新时间">
              <span class="mono">{{ formatTime(store.detail.updatedAt) }}</span>
            </a-descriptions-item>
          </a-descriptions>
        </a-card>

        <a-card title="配送进度" size="small" style="margin-top: 16px;">
          <a-spin :spinning="timelineLoading">
            <OrderTimeline :events="timelineEvents" />
          </a-spin>
        </a-card>
      </template>
    </a-spin>
  </PageContainer>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ReloadOutlined } from '@ant-design/icons-vue'
import PageContainer from '@/components/common/PageContainer.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import OrderTimeline from '@/components/analytics/OrderTimeline.vue'
import { useOrderStore } from '@/stores/order'
import { orderStatusMap } from '@/constants/statusMap'
import { buildGeoTrackingLink, parkDeliveryStageLabel } from '@/constants/parkDelivery'
import { getOrderTimeline, type TimelineEvent } from '@/api/analytics'
import dayjs from 'dayjs'

const router = useRouter()
const route = useRoute()
const store = useOrderStore()
const timelineLoading = ref(false)
const timelineEvents = ref<TimelineEvent[]>([])

const trackingLink = computed(() =>
  buildGeoTrackingLink(store.detail?.orderId, store.detail?.vehicleId ?? undefined),
)

const canReorder = computed(() => Boolean(store.detail?.orderId))

const REORDER_KEY = 'fsd_reorder_source'

function handleReorder() {
  if (!store.detail) return
  sessionStorage.setItem(REORDER_KEY, 'order-detail')
  router.push('/workbench?reorder=1')
}

function priorityColor(p: string) {
  const map: Record<string, string> = { P0: 'red', P1: 'orange', P2: 'blue', P3: 'default' }
  return map[p] || 'default'
}

function formatTime(t: string) {
  return dayjs(t).format('YYYY-MM-DD HH:mm:ss')
}

const statusLabel = computed(() => {
  const s = store.detail?.status
  return s ? orderStatusMap[s]?.label || s : ''
})

function fetchData() {
  const id = Number(route.params.orderId)
  if (id) {
    store.fetchDetail(id)
    loadTimeline(id)
  }
}

async function loadTimeline(orderId: number) {
  timelineLoading.value = true
  try {
    const res = await getOrderTimeline(orderId)
    timelineEvents.value = res.data?.events ?? []
  } finally {
    timelineLoading.value = false
  }
}

onMounted(fetchData)
watch(() => route.params.orderId, fetchData)
</script>

<style scoped lang="less">
.link {
  color: var(--fsd-accent);
}
.text-muted {
  color: var(--fsd-text-tertiary);
}
.mono {
  font-family: 'JetBrains Mono', monospace;
}
.text-secondary {
  color: var(--fsd-text-secondary);
  font-size: 12px;
}
</style>
