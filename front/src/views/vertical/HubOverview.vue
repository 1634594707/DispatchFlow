<template>
  <PageContainer title="母港分流" subtitle="枢纽容量 · 排队任务 · 改派建议">
    <template #actions>
      <a-select v-model:value="filterParkId" :options="parkOptions" placeholder="筛选园区" allow-clear style="width: 180px" @change="loadData" />
      <a-button @click="loadData">刷新</a-button>
    </template>

    <a-row :gutter="16">
      <a-col :span="12">
        <a-card title="枢纽/缓冲/母港" size="small">
          <a-table :columns="hubColumns" :data-source="overview?.hubs || []" row-key="stationId" :pagination="false" size="small">
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'capacity'">
                {{ record.occupancy }} / {{ record.capacityLimit ?? '∞' }}
                <a-tag v-if="record.full" color="error" style="margin-left: 6px">已满</a-tag>
              </template>
              <template v-else-if="column.key === 'type'">
                <a-tag>{{ record.stationType }}</a-tag>
              </template>
            </template>
          </a-table>
        </a-card>
      </a-col>
      <a-col :span="12">
        <a-card title="枢纽相关待派任务" size="small">
          <a-table :columns="taskColumns" :data-source="overview?.queuedTasks || []" row-key="taskId" :pagination="false" size="small">
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'actions'">
                <router-link :to="`/tasks/${record.taskId}`">详情</router-link>
              </template>
            </template>
          </a-table>
        </a-card>
      </a-col>
    </a-row>
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
  { title: '类型', key: 'type', width: 100 },
  { title: '占用', key: 'capacity', width: 140 },
]

const taskColumns = [
  { title: '任务', dataIndex: 'taskNo', key: 'taskNo', width: 120 },
  { title: '枢纽', dataIndex: 'hubStationName', key: 'hubStationName' },
  { title: '建议', dataIndex: 'suggestion', key: 'suggestion' },
  { title: '', key: 'actions', width: 60 },
]

async function loadData() {
  overview.value = (await verticalApi.fetchHubOverview(filterParkId.value)).data
}

onMounted(loadData)
</script>
