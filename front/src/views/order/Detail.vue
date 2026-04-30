<template>
  <PageContainer :title="`订单详情：${store.detail?.orderNo || route.params.orderId}`">
    <template #actions>
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
              {{ store.detail.externalOrderNo }}
            </a-descriptions-item>
            <a-descriptions-item label="状态">
              <StatusBadge :status="store.detail.status" type="order" />
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
            <a-descriptions-item label="起点ID">
              {{ store.detail.pickupPointId }}
            </a-descriptions-item>
            <a-descriptions-item label="终点ID">
              {{ store.detail.dropoffPointId }}
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

        <a-card title="状态流转" size="small" style="margin-top: 16px;">
          <a-timeline>
            <a-timeline-item color="green">
              <p>订单创建</p>
              <span class="mono text-secondary">{{ formatTime(store.detail.createdAt) }}</span>
            </a-timeline-item>
            <a-timeline-item v-if="store.detail.status !== 'CREATED'" color="blue">
              <p>进入调度</p>
            </a-timeline-item>
            <a-timeline-item
              v-if="['COMPLETED', 'FAILED', 'CANCELLED'].includes(store.detail.status)"
              :color="store.detail.status === 'COMPLETED' ? 'green' : 'red'"
            >
              <p>{{ statusLabel }}</p>
              <span class="mono text-secondary">{{ formatTime(store.detail.updatedAt) }}</span>
            </a-timeline-item>
          </a-timeline>
        </a-card>
      </template>
    </a-spin>
  </PageContainer>
</template>

<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import PageContainer from '@/components/common/PageContainer.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import { useOrderStore } from '@/stores/order'
import { orderStatusMap } from '@/constants/statusMap'
import dayjs from 'dayjs'

const router = useRouter()
const route = useRoute()
const store = useOrderStore()

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
  if (id) store.fetchDetail(id)
}

onMounted(fetchData)
watch(() => route.params.orderId, fetchData)
</script>

<style scoped lang="less">
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

.text-muted {
  color: var(--fsd-text-tertiary);
}

.text-secondary {
  color: var(--fsd-text-secondary);
  font-size: 12px;
}
</style>
