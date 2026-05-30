<template>
  <PageContainer title="交通态势" subtitle="路段拥堵 · 区域管制 · 可行动处置">
    <template #actions>
      <a-button :loading="loading" @click="loadAll">刷新</a-button>
      <a-button type="primary" :loading="refreshing" @click="handleRefreshCongestion">重算拥堵</a-button>
      <a-button v-if="authStore.isAdmin" danger @click="handleClearZones">清除管制区</a-button>
    </template>

    <div v-if="summary" class="summary-strip">
      <span>最高拥堵 L{{ summary.maxCongestionLevel }}</span>
      <span>高拥堵路段 {{ summary.highCongestionSegmentCount }}</span>
      <span>禁用路段 {{ summary.disabledSegmentCount }}</span>
      <span>管制区 {{ summary.pausedZoneCount }}</span>
    </div>

    <a-spin :spinning="loading">
      <section v-if="authStore.isAdmin && parkLayout" class="zone-panel">
        <h3>区域暂停派车</h3>
        <TrafficZoneMap :layout="parkLayout" :zones="pauseZones" @select="handleZoneSelect" />
      </section>

      <a-table
        row-key="segmentId"
        size="middle"
        class="segment-table"
        :pagination="false"
        :data-source="segments"
        :columns="columns"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'congestion'">
            <a-tag :color="congestionColor(record.congestionLevel)">
              L{{ record.congestionLevel }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'route'">
            <span class="mono">{{ record.fromNodeCode }} → {{ record.toNodeCode }}</span>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="record.status === 'DISABLED' ? 'red' : 'green'">{{ record.status }}</a-tag>
          </template>
          <template v-else-if="column.key === 'actions'">
            <a-space v-if="authStore.isAdmin">
              <a-button size="small" @click="handleDowngrade(record.segmentId)">降权</a-button>
              <a-button size="small" :danger="record.status !== 'DISABLED'" @click="handleDisable(record.segmentId)">
                {{ record.status === 'DISABLED' ? '启用' : '禁用' }}
              </a-button>
            </a-space>
            <span v-else class="text-muted">只读</span>
          </template>
        </template>
      </a-table>
    </a-spin>
  </PageContainer>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import PageContainer from '@/components/common/PageContainer.vue'
import TrafficZoneMap from '@/components/infrastructure/TrafficZoneMap.vue'
import { useParkScopeStore } from '@/stores/parkScope'
import { useAuthStore } from '@/stores/auth'
import { getParkLayout } from '@/api/park'
import * as trafficApi from '@/api/traffic'
import type { TrafficPauseZone, TrafficSegment, TrafficSummary } from '@/types/traffic'
import type { ParkLayout } from '@/types/park'

const parkScope = useParkScopeStore()
const authStore = useAuthStore()
const loading = ref(false)
const refreshing = ref(false)
const segments = ref<TrafficSegment[]>([])
const summary = ref<TrafficSummary | null>(null)
const pauseZones = ref<TrafficPauseZone[]>([])
const parkLayout = ref<ParkLayout | null>(null)

const columns = [
  { title: '路段', key: 'route' },
  { title: '状态', key: 'status', width: 100 },
  { title: '限速 km/h', dataIndex: 'speedLimitKmh', width: 100 },
  { title: '拥堵', key: 'congestion', width: 90 },
  { title: '附近车辆', dataIndex: 'nearbyVehicleCount', width: 100 },
  { title: '影响任务', dataIndex: 'affectedTaskCount', width: 100 },
  { title: '操作', key: 'actions', width: 160 },
]

function congestionColor(level: number) {
  if (level >= 3) return 'red'
  if (level === 2) return 'orange'
  if (level === 1) return 'gold'
  return 'green'
}

function effectiveParkId() {
  return parkScope.resolveLayoutParkId()
}

async function loadAll() {
  const parkId = effectiveParkId()
  if (!parkId) return
  loading.value = true
  try {
    const [overviewRes, summaryRes, zonesRes, layoutRes] = await Promise.all([
      trafficApi.fetchTrafficOverview(parkId),
      trafficApi.fetchTrafficSummary(parkId),
      trafficApi.listTrafficPauseZones(parkId),
      getParkLayout(parkId),
    ])
    segments.value = overviewRes.data
    summary.value = summaryRes.data
    pauseZones.value = zonesRes.data
    parkLayout.value = layoutRes.data
  } finally {
    loading.value = false
  }
}

async function handleRefreshCongestion() {
  refreshing.value = true
  try {
    await trafficApi.refreshCongestion(effectiveParkId())
    message.success('拥堵等级已更新')
    await loadAll()
  } finally {
    refreshing.value = false
  }
}

async function handleDisable(segmentId: number) {
  await trafficApi.disableTrafficSegment(segmentId)
  message.success('路段状态已更新')
  await loadAll()
}

async function handleDowngrade(segmentId: number) {
  await trafficApi.downgradeTrafficSegment(segmentId)
  message.success('拥堵等级已降低')
  await loadAll()
}

async function handleZoneSelect(zone: { minX: number; minY: number; maxX: number; maxY: number }) {
  const parkId = effectiveParkId()
  if (!parkId) return
  await trafficApi.addTrafficPauseZone({
    parkId,
    ...zone,
    label: '框选管制区',
  })
  message.success('已添加区域暂停派车')
  await loadAll()
}

async function handleClearZones() {
  await trafficApi.clearTrafficPauseZones(effectiveParkId())
  message.success('已清除全部管制区')
  await loadAll()
}

watch(() => parkScope.scopeVersion, loadAll)
onMounted(loadAll)
</script>

<style scoped lang="less">
.summary-strip {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 16px;
  font-size: 13px;
  color: var(--fsd-text-secondary);
}

.zone-panel {
  margin-bottom: 16px;

  h3 {
    margin: 0 0 8px;
    font-size: 14px;
  }
}

.segment-table {
  margin-top: 8px;
}

.mono {
  font-family: 'JetBrains Mono', monospace;
}

.text-muted {
  color: var(--fsd-text-tertiary);
  font-size: 12px;
}
</style>
