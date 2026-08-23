<template>
  <PageContainer title="母港分流" subtitle="枢纽容量 · 排队任务 · 改派建议">
    <template #actions>
      <a-select v-model:value="filterParkId" :options="parkOptions" placeholder="筛选园区" allow-clear style="width: 180px" @change="loadData" />
      <a-button @click="loadData">刷新</a-button>
    </template>

    <div class="hub-overview-container">
      <a-row :gutter="16">
        <a-col :xs="24" :lg="12">
          <a-card title="枢纽/缓冲/母港容量" size="small" class="hub-card">
            <a-table :columns="hubColumns" :data-source="overview?.hubs || []" row-key="stationId" :pagination="false" size="small">
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'stationName'">
                  <span class="hub-name">{{ record.stationName }}</span>
                </template>
                <template v-else-if="column.key === 'type'">
                  <a-tag :color="record.stationType === 'HUB' ? 'cyan' : 'blue'">{{ record.stationType }}</a-tag>
                </template>
                <template v-else-if="column.key === 'capacity'">
                  <div class="capacity-cell">
                    <div class="capacity-bar-wrap">
                      <div
                        class="capacity-bar-fill"
                        :style="{
                          width: `${calcPercent(record.occupancy, record.capacityLimit)}%`,
                          background: record.full ? '#F87171' : '#22D3EE'
                        }"
                      />
                    </div>
                    <span class="capacity-text mono">{{ record.occupancy }} / {{ record.capacityLimit ?? '∞' }}</span>
                    <a-tag v-if="record.full" color="error" size="small">已满</a-tag>
                  </div>
                </template>
              </template>
            </a-table>
          </a-card>
        </a-col>

        <a-col :xs="24" :lg="12">
          <a-card title="枢纽相关待派任务" size="small" class="hub-card">
            <a-table :columns="taskColumns" :data-source="overview?.queuedTasks || []" row-key="taskId" :pagination="false" size="small">
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'taskNo'">
                  <span class="mono">{{ record.taskNo }}</span>
                </template>
                <template v-else-if="column.key === 'suggestion'">
                  <span class="hub-suggestion">{{ record.suggestion || '-' }}</span>
                </template>
                <template v-else-if="column.key === 'actions'">
                  <router-link :to="`/tasks/${record.taskId}`" class="hub-link">详情 →</router-link>
                </template>
              </template>
            </a-table>
          </a-card>
        </a-col>
      </a-row>
    </div>
  </PageContainer>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import PageContainer from '@/components/common/PageContainer.vue'
import { useParkOptions } from '@/composables/useParkOptions'
import * as verticalApi from '@/api/vertical'
import type { HubOverview } from '@/api/vertical'

const { parkOptions } = useParkOptions()
const filterParkId = ref<number>()
const overview = ref<HubOverview | null>(null)

const hubColumns = [
  { title: '站点', dataIndex: 'stationName', key: 'stationName' },
  { title: '类型', key: 'type', width: 90 },
  { title: '占用负荷', key: 'capacity', width: 190 },
]

const taskColumns = [
  { title: '任务编号', dataIndex: 'taskNo', key: 'taskNo', width: 140 },
  { title: '关联枢纽', dataIndex: 'hubStationName', key: 'hubStationName', width: 120 },
  { title: '改派/分流建议', dataIndex: 'suggestion', key: 'suggestion' },
  { title: '', key: 'actions', width: 70 },
]

function calcPercent(occupancy: number, limit: number | null | undefined): number {
  if (!limit || limit <= 0) return Math.min(100, occupancy * 10)
  return Math.min(100, Math.round((occupancy / limit) * 100))
}

async function loadData() {
  overview.value = (await verticalApi.fetchHubOverview(filterParkId.value)).data
}

onMounted(loadData)
</script>

<style scoped lang="less">
.hub-overview-container {
  margin-top: 8px;
}

.hub-card {
  background: var(--fsd-bg-base);
  border: 1px solid var(--fsd-border);
  border-radius: 12px;
}

.hub-name {
  font-weight: 500;
  color: var(--fsd-text-primary);
}

.capacity-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.capacity-bar-wrap {
  flex: 1;
  height: 6px;
  background: rgba(255, 255, 255, 0.08);
  border-radius: 999px;
  overflow: hidden;
  min-width: 40px;
}

.capacity-bar-fill {
  height: 100%;
  border-radius: 999px;
  transition: width 0.3s ease;
}

.capacity-text {
  font-size: 11px;
  color: var(--fsd-text-secondary);
}

.mono {
  font-family: var(--fsd-font-mono);
}

.hub-suggestion {
  font-size: 12px;
  color: var(--fsd-text-secondary);
}

.hub-link {
  font-size: 12px;
  color: var(--fsd-accent);
  text-decoration: none;
  &:hover {
    text-decoration: underline;
  }
}
</style>
