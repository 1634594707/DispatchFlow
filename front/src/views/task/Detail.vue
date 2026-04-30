<template>
  <PageContainer :title="`任务详情：${store.detail?.taskNo || route.params.taskId}`">
    <template #actions>
      <a-button @click="router.back()">返回列表</a-button>
    </template>

    <a-spin :spinning="store.detailLoading">
      <template v-if="store.detail">
        <div class="detail-grid">
          <a-card title="任务信息" size="small">
            <a-descriptions :column="2" size="small" bordered>
              <a-descriptions-item label="任务编号">
                <span class="mono">{{ store.detail.taskNo }}</span>
              </a-descriptions-item>
              <a-descriptions-item label="状态">
                <StatusBadge :status="store.detail.status" type="task" />
              </a-descriptions-item>
              <a-descriptions-item label="关联订单">
                <router-link :to="`/orders/${store.detail.orderId}`" class="link">
                  {{ store.detail.orderId }}
                </router-link>
              </a-descriptions-item>
              <a-descriptions-item label="派单类型">
                <a-tag :color="store.detail.dispatchType === 'AUTO' ? 'cyan' : 'orange'">
                  {{ store.detail.dispatchType === 'AUTO' ? '自动' : '手动' }}
                </a-tag>
              </a-descriptions-item>
              <a-descriptions-item label="人工介入">
                {{ store.detail.manualFlag === 1 ? '是' : '否' }}
              </a-descriptions-item>
              <a-descriptions-item label="重试次数">
                {{ store.detail.retryCount }}
              </a-descriptions-item>
              <a-descriptions-item label="失败原因" :span="2">
                {{ store.detail.failReasonMsg || '-' }}
              </a-descriptions-item>
              <a-descriptions-item label="备注" :span="2">
                {{ store.detail.remark || '-' }}
              </a-descriptions-item>
            </a-descriptions>
          </a-card>

          <a-card title="车辆信息" size="small">
            <template v-if="vehicleDetail">
              <a-descriptions :column="2" size="small" bordered>
                <a-descriptions-item label="车辆编号">
                  <router-link :to="`/vehicles/${vehicleDetail.vehicleId}`" class="link">
                    {{ vehicleDetail.vehicleCode }}
                  </router-link>
                </a-descriptions-item>
                <a-descriptions-item label="车辆名称">
                  {{ vehicleDetail.vehicleName }}
                </a-descriptions-item>
                <a-descriptions-item label="在线状态">
                  <StatusBadge :status="vehicleDetail.onlineStatus" type="online" />
                </a-descriptions-item>
                <a-descriptions-item label="调度状态">
                  <StatusBadge :status="vehicleDetail.dispatchStatus" type="dispatch" />
                </a-descriptions-item>
                <a-descriptions-item label="电量">
                  <a-progress
                    :percent="vehicleDetail.batteryLevel"
                    :stroke-color="vehicleDetail.batteryLevel < 20 ? '#FF3D71' : '#00E676'"
                    size="small"
                  />
                </a-descriptions-item>
                <a-descriptions-item label="最后回传">
                  <span class="mono">{{ formatTime(vehicleDetail.lastReportTime) }}</span>
                </a-descriptions-item>
              </a-descriptions>
            </template>
            <a-empty v-else-if="!store.detail.vehicleId" description="暂无关联车辆" />
            <a-spin v-else size="small" />
          </a-card>

          <a-card title="操作" size="small">
            <div class="detail-actions">
              <a-button
                v-if="canDispatch"
                type="primary"
                @click="openDispatch"
              >
                派单
              </a-button>
              <a-button
                v-if="canReassign"
                type="primary"
                ghost
                @click="openReassign"
              >
                改派
              </a-button>
              <a-popconfirm
                v-if="canCancel"
                title="确认取消该任务？"
                @confirm="handleCancel"
              >
                <a-button danger>取消任务</a-button>
              </a-popconfirm>
            </div>
          </a-card>
        </div>

        <a-card title="任务状态时间线" size="small">
          <a-timeline>
            <a-timeline-item color="green">
              <p>任务创建</p>
              <span class="mono text-secondary">{{ formatTime(store.detail.createdAt) }}</span>
            </a-timeline-item>
            <a-timeline-item v-if="store.detail.assignTime" color="blue">
              <p>已派单</p>
              <span class="mono text-secondary">{{ formatTime(store.detail.assignTime) }}</span>
            </a-timeline-item>
            <a-timeline-item v-if="store.detail.startTime" color="cyan">
              <p>开始执行</p>
              <span class="mono text-secondary">{{ formatTime(store.detail.startTime) }}</span>
            </a-timeline-item>
            <a-timeline-item v-if="store.detail.finishTime" :color="finishColor">
              <p>{{ store.detail.status === 'SUCCESS' ? '执行完成' : '执行结束' }}</p>
              <span class="mono text-secondary">{{ formatTime(store.detail.finishTime) }}</span>
            </a-timeline-item>
          </a-timeline>
        </a-card>
      </template>
    </a-spin>
  </PageContainer>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import PageContainer from '@/components/common/PageContainer.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import { useTaskStore } from '@/stores/task'
import { getVehicleDetail } from '@/api/vehicle'
import { TaskStatus } from '@/constants/enums'
import dayjs from 'dayjs'
import type { VehicleDetailResponse } from '@/types/vehicle'

const router = useRouter()
const route = useRoute()
const store = useTaskStore()

const vehicleDetail = ref<VehicleDetailResponse | null>(null)

const canDispatch = computed(() => {
  const s = store.detail?.status
  return s === TaskStatus.PENDING || s === TaskStatus.MANUAL_PENDING
})

const canReassign = computed(() => store.detail?.status === TaskStatus.ASSIGNED)
const canCancel = computed(() => store.detail?.status !== TaskStatus.EXECUTING)

const finishColor = computed(() =>
  store.detail?.status === TaskStatus.SUCCESS ? 'green' : 'red'
)

function formatTime(t: string | null) {
  return t ? dayjs(t).format('YYYY-MM-DD HH:mm:ss') : '-'
}

async function loadVehicle(vehicleId: number) {
  try {
    const res = await getVehicleDetail(vehicleId)
    vehicleDetail.value = res.data
  } catch {
    // silent
  }
}

function openDispatch() {
  message.info('派单功能开发中')
}

function openReassign() {
  message.info('改派功能开发中')
}

function handleCancel() {
  message.success('任务已取消')
  fetchData()
}

function fetchData() {
  const id = Number(route.params.taskId)
  if (id) {
    store.fetchDetail(id).then(() => {
      if (store.detail?.vehicleId) {
        loadVehicle(store.detail.vehicleId)
      }
    })
  }
}

onMounted(fetchData)

watch(() => route.params.taskId, fetchData)
</script>

<style scoped lang="less">
.detail-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 16px;

  > :last-child {
    grid-column: 1 / -1;
  }
}

.detail-actions {
  display: flex;
  gap: 12px;
}

.link {
  color: var(--fsd-accent);
  font-family: 'JetBrains Mono', monospace;

  &:hover {
    text-decoration: underline;
  }
}

.mono {
  font-family: 'JetBrains Mono', monospace;
  font-size: 13px;
}

.text-secondary {
  color: var(--fsd-text-secondary);
  font-size: 12px;
}
</style>
