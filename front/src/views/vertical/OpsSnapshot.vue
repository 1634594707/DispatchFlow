<template>
  <PageContainer title="运维快照" subtitle="低电簇 · 离线车 · 枢纽排队">
    <template #actions>
      <a-space>
        <a-select
          v-model:value="parkId"
          :options="parkOptions"
          placeholder="全部园区"
          allow-clear
          style="width: 180px"
          @change="loadData"
        />
        <a-button @click="loadData"><ReloadOutlined /> 刷新</a-button>
      </a-space>
    </template>

    <a-spin :spinning="loading">
      <a-row :gutter="16">
        <a-col :span="8">
          <a-card title="低电簇" size="small">
            <a-empty v-if="!snapshot?.lowBatteryClusters?.length" description="暂无低电聚集" />
            <a-list v-else size="small" :data-source="snapshot!.lowBatteryClusters">
              <template #renderItem="{ item }">
                <a-list-item>{{ item.gridKey }} · {{ item.vehicleCount }} 台 · 最低 {{ item.minSoc }}%</a-list-item>
              </template>
            </a-list>
          </a-card>
        </a-col>
        <a-col :span="8">
          <a-card title="离线 &gt;5min" size="small">
            <a-empty v-if="!snapshot?.offlineVehicles?.length" description="暂无长期离线车辆" />
            <a-list v-else size="small" :data-source="snapshot!.offlineVehicles">
              <template #renderItem="{ item }">
                <a-list-item>{{ item.vehicleCode }} · {{ item.offlineMinutes }} 分 · SOC {{ item.soc ?? '-' }}%</a-list-item>
              </template>
            </a-list>
          </a-card>
        </a-col>
        <a-col :span="8">
          <a-card title="枢纽排队" size="small">
            <a-empty v-if="!snapshot?.hubQueuedTasks?.length" description="暂无枢纽排队任务" />
            <a-list v-else size="small" :data-source="snapshot!.hubQueuedTasks">
              <template #renderItem="{ item }">
                <a-list-item>{{ item.hubStationName }} · {{ item.taskNo }} · {{ item.suggestion }}</a-list-item>
              </template>
            </a-list>
          </a-card>
        </a-col>
      </a-row>
    </a-spin>
  </PageContainer>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ReloadOutlined } from '@ant-design/icons-vue'
import PageContainer from '@/components/common/PageContainer.vue'
import { useParkOptions } from '@/composables/useParkOptions'
import { fetchOpsSnapshot, type OpsSnapshot } from '@/api/vertical'

const { parkOptions } = useParkOptions()
const parkId = ref<number>()
const loading = ref(false)
const snapshot = ref<OpsSnapshot | null>(null)

async function loadData() {
  loading.value = true
  try {
    snapshot.value = (await fetchOpsSnapshot(parkId.value)).data
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  parkId.value = parkOptions.value[0]?.value
  loadData()
})
</script>
