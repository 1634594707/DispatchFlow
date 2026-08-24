<template>
  <PageContainer :title="`订单详情：${store.detail?.orderNo || route.params.orderId}`">
    <template #actions>
      <a-button v-if="canReorder" type="primary" ghost @click="handleReorder">
        <ReloadOutlined /> 再来一单
      </a-button>
      <router-link v-if="store.detail?.vehicleId" :to="trackingLink">
        <a-button type="primary">地图追踪</a-button>
      </router-link>
      <a-button @click="router.back()">返回列表</a-button>
    </template>

    <a-spin :spinning="store.detailLoading">
      <template v-if="store.detail">
        <section class="detail-summary" aria-label="订单概要">
          <div class="detail-summary-primary">
            <span class="detail-summary-label">订单编号</span>
            <span class="detail-summary-code mono">{{ store.detail.orderNo }}</span>
          </div>
          <dl class="detail-summary-metrics">
            <div class="detail-summary-metric">
              <dt>当前状态</dt>
              <dd><StatusBadge :status="store.detail.status" type="order" /></dd>
            </div>
            <div v-if="store.detail.runtimeStage" class="detail-summary-metric">
              <dt>配送阶段</dt>
              <dd>{{ parkDeliveryStageLabel(store.detail.runtimeStage) }}</dd>
            </div>
            <div class="detail-summary-metric">
              <dt>配送车辆</dt>
              <dd>
                <router-link
                  v-if="store.detail.vehicleId"
                  :to="`/vehicles/${store.detail.vehicleId}`"
                  class="link"
                >
                  {{ store.detail.vehicleCode || store.detail.vehicleId }}
                </router-link>
                <span v-else class="text-muted">待分配</span>
              </dd>
            </div>
            <div class="detail-summary-metric">
              <dt>关联任务</dt>
              <dd>
                <router-link
                  v-if="store.detail.dispatchTaskId"
                  :to="`/tasks/${store.detail.dispatchTaskId}`"
                  class="link"
                >
                  {{ store.detail.dispatchTaskId }}
                </router-link>
                <span v-else class="text-muted">-</span>
              </dd>
            </div>
          </dl>
        </section>

        <div class="detail-sections">
          <section class="detail-section" aria-labelledby="order-information-heading">
            <div class="detail-section-heading">
              <div>
                <h2 id="order-information-heading">订单信息</h2>
                <p>来源、站点和执行记录</p>
              </div>
            </div>
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
              <a-descriptions-item v-if="store.detail.runtimeStage" label="配送阶段">
                {{ parkDeliveryStageLabel(store.detail.runtimeStage) }}
              </a-descriptions-item>
              <a-descriptions-item label="来源类型">
                <a-tag>{{ store.detail.sourceType }}</a-tag>
              </a-descriptions-item>
              <a-descriptions-item label="业务类型">
                {{ store.detail.bizType }}
              </a-descriptions-item>
              <a-descriptions-item label="优先级">
                <a-tag :class="priorityClass(store.detail.priority)">
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
          </section>

          <section class="detail-section" aria-labelledby="delivery-progress-heading">
            <div class="detail-section-heading">
              <div>
                <h2 id="delivery-progress-heading">配送进度</h2>
                <p>阶段状态与事件时间线</p>
              </div>
            </div>
            <div v-if="store.detail.runtimeStage" class="delivery-progress">
              <a-steps
                :current="deliveryStepIndex"
                :status="deliveryStepStatus"
                size="small"
                :direction="stepsDirection"
              >
                <a-step title="待派车" />
                <a-step title="已派车" />
                <a-step title="前往取货" />
                <a-step title="装货" />
                <a-step title="配送中" />
                <a-step title="卸货" />
                <a-step title="已完成" />
              </a-steps>
            </div>
            <a-spin :spinning="timelineLoading">
              <OrderTimeline :events="timelineEvents" />
            </a-spin>
          </section>
        </div>
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
import { useResponsive } from '@/composables/useResponsive'
import { buildGeoTrackingLink, parkDeliveryStageLabel } from '@/constants/parkDelivery'
import { getOrderTimeline, type TimelineEvent } from '@/api/analytics'
import dayjs from 'dayjs'

const router = useRouter()
const route = useRoute()
const store = useOrderStore()
const resp = useResponsive()
const timelineLoading = ref(false)
const timelineEvents = ref<TimelineEvent[]>([])

// V9-UI2: Delivery stage → step index mapping
const STAGE_STEP_MAP: Record<string, number> = {
  PENDING_ASSIGNMENT: 0,
  WAITING_DISPATCH: 0,
  DISPATCHED: 1,
  HEADING_TO_PICKUP: 2,
  TO_PICKUP: 2,
  LOADING: 3,
  HEADING_TO_DROPOFF: 4,
  TO_DROPOFF: 4,
  UNLOADING: 5,
  COMPLETED: 6,
}

const ABNORMAL_STAGES = new Set(['FAILED', 'MANUAL_PENDING', 'EMERGENCY_PARKING'])

const deliveryStepIndex = computed(() => {
  const stage = store.detail?.runtimeStage
  if (!stage) return 0
  return STAGE_STEP_MAP[stage] ?? 0
})

const deliveryStepStatus = computed<'process' | 'finish' | 'error' | 'wait'>(() => {
  const stage = store.detail?.runtimeStage
  if (!stage) return 'wait'
  if (stage === 'COMPLETED') return 'finish'
  if (ABNORMAL_STAGES.has(stage)) return 'error'
  return 'process'
})

const stepsDirection = computed<'horizontal' | 'vertical'>(() =>
  resp.isPhone.value ? 'vertical' : 'horizontal',
)

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

function priorityClass(priority: string) {
  if (priority === 'P0') return 'priority-tag priority-tag--error'
  if (priority === 'P1') return 'priority-tag priority-tag--warning'
  return 'priority-tag priority-tag--neutral'
}

function formatTime(t: string) {
  return dayjs(t).format('YYYY-MM-DD HH:mm:ss')
}

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
.detail-summary {
  display: flex;
  min-width: 0;
  align-items: stretch;
  gap: var(--fsd-space-6);
  padding: var(--fsd-space-4) var(--fsd-space-5);
  border: 1px solid var(--fsd-border);
  border-radius: var(--fsd-radius-md);
  background: var(--fsd-surface-status);
}

.detail-summary-primary {
  display: flex;
  min-width: 160px;
  flex-direction: column;
  justify-content: center;
  gap: var(--fsd-space-1);
}

.detail-summary-label,
.detail-summary-metric dt {
  color: var(--fsd-text-tertiary);
  font-size: var(--fsd-text-xs);
  line-height: var(--fsd-leading-normal);
}

.detail-summary-code {
  color: var(--fsd-text-heading);
  font-size: var(--fsd-text-lg);
  font-weight: var(--fsd-font-semibold);
  font-variant-numeric: tabular-nums;
}

.detail-summary-metrics {
  display: grid;
  min-width: 0;
  flex: 1;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin: 0;
}

.detail-summary-metric {
  min-width: 0;
  padding: 0 var(--fsd-space-4);
  border-left: 1px solid var(--fsd-border-split);
}

.detail-summary-metric dt,
.detail-summary-metric dd {
  margin: 0;
}

.detail-summary-metric dd {
  min-height: 22px;
  margin-top: var(--fsd-space-1);
  overflow: hidden;
  color: var(--fsd-text-primary);
  font-size: var(--fsd-text-sm);
  line-height: var(--fsd-leading-normal);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.detail-sections {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.detail-section {
  min-width: 0;
  padding: var(--fsd-space-5) 0;
  border-top: 1px solid var(--fsd-border);
}

.detail-section:first-child {
  border-top: 0;
  padding-top: 0;
}

.detail-section-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--fsd-space-3);
  margin-bottom: var(--fsd-space-3);
}

.detail-section-heading h2 {
  margin: 0;
  color: var(--fsd-text-primary);
  font-size: var(--fsd-text-md);
  font-weight: var(--fsd-font-semibold);
  line-height: var(--fsd-leading-snug);
}

.detail-section-heading p {
  margin: var(--fsd-space-1) 0 0;
  color: var(--fsd-text-tertiary);
  font-size: var(--fsd-text-xs);
  line-height: var(--fsd-leading-normal);
}

.link {
  color: var(--fsd-accent);
  font-family: var(--fsd-font-mono);

  &:hover {
    color: var(--fsd-accent-strong);
    text-decoration: underline;
  }
}

.text-muted {
  color: var(--fsd-text-tertiary);
}

.mono {
  font-family: var(--fsd-font-mono);
}

.priority-tag {
  border: 1px solid var(--fsd-border);
  border-radius: var(--fsd-radius-sm);
  background: var(--fsd-neutral-bg);
  color: var(--fsd-text-secondary);
}

.priority-tag--error {
  border-color: var(--fsd-error);
  background: var(--fsd-error-bg);
  color: var(--fsd-error);
}

.priority-tag--warning {
  border-color: var(--fsd-warning);
  background: var(--fsd-warning-bg);
  color: var(--fsd-warning);
}

.priority-tag--neutral {
  border-color: var(--fsd-border);
  background: var(--fsd-neutral-bg);
  color: var(--fsd-text-secondary);
}

.delivery-progress {
  margin-bottom: var(--fsd-space-5);
  padding-bottom: var(--fsd-space-4);
  border-bottom: 1px solid var(--fsd-border-split);
}

@media (max-width: 1023px) {
  .detail-summary {
    flex-direction: column;
    gap: var(--fsd-space-4);
  }

  .detail-summary-primary {
    min-width: 0;
  }
}

@media (max-width: 767px) {
  .detail-summary {
    padding: var(--fsd-space-4);
  }

  .detail-summary-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    border-top: 1px solid var(--fsd-border-split);
  }

  .detail-summary-metric {
    padding: var(--fsd-space-3) 0 0;
    border-left: 0;
  }

  .detail-summary-metric:nth-child(even) {
    padding-left: var(--fsd-space-3);
    border-left: 1px solid var(--fsd-border-split);
  }

  .detail-section {
    padding: var(--fsd-space-4) 0;
  }

  :deep(.ant-descriptions) {
    .ant-descriptions-item {
      display: flex;
      flex-direction: column;
    }

    .ant-descriptions-item-label,
    .ant-descriptions-item-content {
      width: 100% !important;
    }

    .ant-descriptions-item-label {
      padding-bottom: 0;
    }
  }
}
</style>
