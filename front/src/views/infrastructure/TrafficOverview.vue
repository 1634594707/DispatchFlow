<template>
  <PageContainer title="交通态势" subtitle="路段拥堵 · 附近车辆密度">
    <template #actions>
      <a-select
        v-model:value="parkId"
        :options="parkOptions"
        placeholder="选择园区"
        style="width: 200px; margin-right: 8px;"
        allow-clear
      />
      <a-button :loading="loading" @click="loadData">刷新</a-button>
      <a-button type="primary" :loading="refreshing" @click="handleRefreshCongestion">重算拥堵</a-button>
    </template>

    <a-spin :spinning="loading">
      <a-table
        row-key="segmentId"
        size="middle"
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
        </template>
      </a-table>
    </a-spin>
  </PageContainer>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import PageContainer from '@/components/common/PageContainer.vue'
import { useParkOptions } from '@/composables/useParkOptions'
import * as trafficApi from '@/api/traffic'
import type { TrafficSegment } from '@/types/traffic'

const { parkOptions } = useParkOptions()
const parkId = ref<number | undefined>()
const loading = ref(false)
const refreshing = ref(false)
const segments = ref<TrafficSegment[]>([])

const columns = [
  { title: '路段', key: 'route' },
  { title: '状态', dataIndex: 'status', width: 100 },
  { title: '限速 km/h', dataIndex: 'speedLimitKmh', width: 100 },
  { title: '拥堵', key: 'congestion', width: 90 },
  { title: '附近车辆', dataIndex: 'nearbyVehicleCount', width: 100 },
]

function congestionColor(level: number) {
  if (level >= 3) return 'red'
  if (level === 2) return 'orange'
  if (level === 1) return 'gold'
  return 'green'
}

async function loadData() {
  loading.value = true
  try {
    segments.value = (await trafficApi.fetchTrafficOverview(parkId.value)).data
  } finally {
    loading.value = false
  }
}

async function handleRefreshCongestion() {
  refreshing.value = true
  try {
    await trafficApi.refreshCongestion(parkId.value)
    message.success('拥堵等级已更新')
    await loadData()
  } finally {
    refreshing.value = false
  }
}

watch(parkId, loadData)
onMounted(loadData)
</script>

<style scoped lang="less">
.mono {
  font-family: 'JetBrains Mono', monospace;
}
</style>
