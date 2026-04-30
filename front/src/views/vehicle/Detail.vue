<template>
  <PageContainer :title="`车辆详情：${store.detail?.vehicleCode || route.params.vehicleId}`">
    <template #actions>
      <a-button @click="router.back()">返回列表</a-button>
    </template>

    <a-spin :spinning="store.detailLoading">
      <template v-if="store.detail">
        <div class="vehicle-detail-grid">
          <a-card title="基本信息" size="small">
            <a-descriptions :column="2" size="small" bordered>
              <a-descriptions-item label="车辆编号">
                <span class="mono">{{ store.detail.vehicleCode }}</span>
              </a-descriptions-item>
              <a-descriptions-item label="车辆名称">
                {{ store.detail.vehicleName }}
              </a-descriptions-item>
              <a-descriptions-item label="车辆类型">
                <a-tag>{{ store.detail.vehicleType }}</a-tag>
              </a-descriptions-item>
              <a-descriptions-item label="备注">
                {{ store.detail.remark || '-' }}
              </a-descriptions-item>
            </a-descriptions>
          </a-card>

          <a-card title="实时状态" size="small">
            <a-descriptions :column="2" size="small" bordered>
              <a-descriptions-item label="在线状态">
                <StatusBadge :status="store.detail.onlineStatus" type="online" />
              </a-descriptions-item>
              <a-descriptions-item label="调度状态">
                <StatusBadge :status="store.detail.dispatchStatus" type="dispatch" />
              </a-descriptions-item>
              <a-descriptions-item label="电量">
                <a-progress
                  :percent="store.detail.batteryLevel"
                  :stroke-color="store.detail.batteryLevel < 20 ? '#FF3D71' : '#00E676'"
                  size="small"
                />
              </a-descriptions-item>
              <a-descriptions-item label="最后回传">
                <span class="mono">{{ formatTime(store.detail.lastReportTime) }}</span>
              </a-descriptions-item>
              <a-descriptions-item label="当前位置">
                <span class="mono">
                  {{ store.detail.currentLatitude }}, {{ store.detail.currentLongitude }}
                </span>
              </a-descriptions-item>
              <a-descriptions-item label="当前任务">
                <router-link
                  v-if="store.detail.currentTaskId"
                  :to="`/tasks/${store.detail.currentTaskId}`"
                  class="link"
                >
                  任务#{{ store.detail.currentTaskId }}
                </router-link>
                <span v-else class="text-muted">-</span>
              </a-descriptions-item>
            </a-descriptions>
          </a-card>
        </div>

        <a-card title="状态变更历史" size="small" style="margin-top: 16px;">
          <a-empty description="MVP 暂未接入状态变更日志" />
        </a-card>
      </template>
    </a-spin>
  </PageContainer>
</template>

<script setup lang="ts">
import { onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import PageContainer from '@/components/common/PageContainer.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import { useVehicleStore } from '@/stores/vehicle'
import dayjs from 'dayjs'

const router = useRouter()
const route = useRoute()
const store = useVehicleStore()

function formatTime(t: string) {
  return dayjs(t).format('YYYY-MM-DD HH:mm:ss')
}

function fetchData() {
  const id = Number(route.params.vehicleId)
  if (id) store.fetchDetail(id)
}

onMounted(fetchData)
watch(() => route.params.vehicleId, fetchData)
</script>

<style scoped lang="less">
.vehicle-detail-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
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

.text-muted {
  color: var(--fsd-text-tertiary);
}
</style>
