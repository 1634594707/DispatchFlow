<template>
  <PageContainer title="充电管理报表" subtitle="充电会话监控 · 桩位占用 · 充电历史">
    <div class="toolbar">
      <a-button @click="router.push('/analytics')">返回运营分析</a-button>
      <a-button :loading="loading" @click="loadData">刷新</a-button>
    </div>

    <a-spin :spinning="loading">
      <div class="overview-cards">
        <div class="card">
          <span>充电中车辆</span>
          <strong>{{ overview?.activeSessionCount ?? 0 }}</strong>
        </div>
        <div class="card">
          <span>桩位占用</span>
          <strong>{{ overview?.occupiedPileCount ?? 0 }}/{{ overview?.totalPileCount ?? 0 }}</strong>
        </div>
        <div class="card">
          <span>换电中</span>
          <strong>{{ overview?.activeSwapSessionCount ?? 0 }}</strong>
        </div>
        <div class="card">
          <span>充电总时长(分)</span>
          <strong>{{ overview?.totalChargeDurationMinutes ?? 0 }}</strong>
        </div>
        <div class="card">
          <span>换电总时长(分)</span>
          <strong>{{ overview?.totalSwapDurationMinutes ?? 0 }}</strong>
        </div>
        <div class="card">
          <span>平均充电速度</span>
          <strong>{{ overview?.avgChargeSpeedPerHour ?? 0 }}%/h</strong>
        </div>
      </div>

      <section class="panel">
        <h3>充电中车辆</h3>
        <a-table
          size="small"
          row-key="sessionId"
          :pagination="false"
          :data-source="overview?.activeSessions || []"
          :columns="activeColumns"
        />
      </section>

      <section class="panel">
        <h3>充电历史</h3>
        <a-table
          size="small"
          row-key="sessionId"
          :pagination="{ pageSize: 10 }"
          :data-source="overview?.recentHistory || []"
          :columns="historyColumns"
        />
      </section>
    </a-spin>
  </PageContainer>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import PageContainer from '@/components/common/PageContainer.vue'
import { getAnalyticsChargingOverview } from '@/api/analytics'
import type { AnalyticsChargingOverview } from '@/types/analytics'

const router = useRouter()
const loading = ref(false)
const overview = ref<AnalyticsChargingOverview | null>(null)

const activeColumns = [
  { title: '车辆', dataIndex: 'vehicleCode', key: 'vehicleCode' },
  { title: '充电桩', dataIndex: 'pileCode', key: 'pileCode' },
  { title: '开始 SOC', dataIndex: 'startSoc', key: 'startSoc' },
  { title: '当前 SOC', dataIndex: 'currentSoc', key: 'currentSoc' },
  { title: '已充时长(分)', dataIndex: 'elapsedMinutes', key: 'elapsedMinutes' },
  { title: '开始时间', dataIndex: 'startTime', key: 'startTime' },
]

const historyColumns = [
  { title: '车辆', dataIndex: 'vehicleCode', key: 'vehicleCode' },
  { title: '充电桩', dataIndex: 'pileCode', key: 'pileCode' },
  { title: '起始 SOC', dataIndex: 'startSoc', key: 'startSoc' },
  { title: '结束 SOC', dataIndex: 'endSoc', key: 'endSoc' },
  { title: '时长(分)', dataIndex: 'durationMinutes', key: 'durationMinutes' },
  { title: '速度(%/h)', dataIndex: 'chargeSpeedPerHour', key: 'chargeSpeedPerHour' },
  { title: '结束时间', dataIndex: 'endTime', key: 'endTime' },
]

async function loadData() {
  loading.value = true
  try {
    const res = await getAnalyticsChargingOverview()
    overview.value = res.data
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<style scoped lang="less">
.toolbar {
  display: flex;
  justify-content: space-between;
  margin-bottom: 16px;
}

.overview-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-bottom: 16px;
}

.card {
  padding: 18px;
  border-radius: var(--fsd-radius-lg);
  border: 1px solid var(--fsd-border);
  background: var(--fsd-bg-base);

  span {
    display: block;
    font-size: 12px;
    color: var(--fsd-text-tertiary);
  }

  strong {
    display: block;
    margin-top: 8px;
    font-size: 28px;
    color: var(--fsd-accent);
    font-family: 'JetBrains Mono', monospace;
  }
}

.panel {
  background: var(--fsd-bg-base);
  border: 1px solid var(--fsd-border);
  border-radius: var(--fsd-radius-lg);
  padding: 20px;
  margin-bottom: 16px;

  h3 {
    margin: 0 0 16px;
    font-size: 15px;
  }
}
</style>
