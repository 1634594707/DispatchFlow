<template>
  <PageContainer title="充电管理报表" subtitle="充电会话监控 · 桩位占用 · 充电历史">
    <div class="toolbar">
      <a-button @click="router.push('/analytics')">返回运营分析</a-button>
      <a-button :loading="loading" @click="loadData">刷新</a-button>
    </div>

    <a-spin :spinning="loading">
      <!-- V5-D6: 充电站统计信息（V5-E5 增强） -->
      <div class="station-section">
        <div class="station-section-title">📊 充电站概览</div>
        <div v-if="stationInfo" class="station-info-banner">
          <div class="station-info-item">
            <span class="station-info-icon">📍</span>
            <span class="station-info-label">充电站</span>
            <strong class="station-info-value">{{ stationInfo.stationCount }}</strong>
            <span class="station-info-unit">座</span>
          </div>
          <div class="station-info-divider"></div>
          <div class="station-info-item">
            <span class="station-info-icon">⚡</span>
            <span class="station-info-label">快充桩</span>
            <strong class="station-info-value fast">{{ stationInfo.fastPileCount }}</strong>
            <span class="station-info-unit">桩</span>
          </div>
          <div class="station-info-divider"></div>
          <div class="station-info-item">
            <span class="station-info-icon">🔋</span>
            <span class="station-info-label">慢充桩</span>
            <strong class="station-info-value slow">{{ stationInfo.slowPileCount }}</strong>
            <span class="station-info-unit">桩</span>
          </div>
          <div class="station-info-divider"></div>
          <div class="station-info-item">
            <span class="station-info-icon">🟢</span>
            <span class="station-info-label">空闲桩</span>
            <strong class="station-info-value free">{{ totalPileCount - (overview?.occupiedPileCount ?? 0) }}</strong>
            <span class="station-info-unit">/ {{ totalPileCount }} 空闲</span>
          </div>
        </div>
      </div>

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
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import PageContainer from '@/components/common/PageContainer.vue'
import { getAnalyticsChargingOverview } from '@/api/analytics'
import { fetchChargingPiles } from '@/api/infrastructure'
import type { AnalyticsChargingOverview } from '@/types/analytics'

interface StationInfo {
  stationCount: number
  fastPileCount: number
  slowPileCount: number
}

const FAST_POWER_THRESHOLD_KW = 60

const router = useRouter()
const loading = ref(false)
const overview = ref<AnalyticsChargingOverview | null>(null)
const stationInfo = ref<StationInfo | null>(null)

const totalPileCount = computed(() => overview.value?.totalPileCount ?? 0)

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
    const [overviewRes, pilesRes] = await Promise.all([
      getAnalyticsChargingOverview(),
      fetchChargingPiles(),
    ])
    overview.value = overviewRes.data

    const piles = pilesRes.data ?? []
    const parkIds = new Set(piles.map((p) => p.parkId))
    const fastPiles = piles.filter((p) => (p.maxPowerKw ?? 0) >= FAST_POWER_THRESHOLD_KW)
    const slowPiles = piles.filter((p) => (p.maxPowerKw ?? 0) < FAST_POWER_THRESHOLD_KW)
    stationInfo.value = {
      stationCount: parkIds.size,
      fastPileCount: fastPiles.length,
      slowPileCount: slowPiles.length,
    }
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

.station-section {
  margin-bottom: 20px;
}

.station-section-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--fsd-text-primary);
  margin-bottom: 10px;
}

.station-info-banner {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px 20px;
  border-radius: var(--fsd-radius-lg);
  border: 1px solid var(--fsd-border);
  background: linear-gradient(135deg, rgba(0, 119, 182, 0.08) 0%, rgba(13, 17, 23, 0.6) 100%);
}

.station-info-item {
  display: flex;
  align-items: center;
  gap: 6px;
}

.station-info-icon {
  font-size: 16px;
  line-height: 1;
}

.station-info-label {
  font-size: 13px;
  color: var(--fsd-text-tertiary);
}

.station-info-value {
  font-size: 22px;
  font-weight: 700;
  font-family: 'JetBrains Mono', monospace;
  color: var(--fsd-accent);

  &.fast {
    color: #ffb020;
  }

  &.slow {
    color: #3ea6ff;
  }

  &.free {
    color: #00e676;
  }
}

.station-info-unit {
  font-size: 12px;
  color: var(--fsd-text-tertiary);
}

.station-info-divider {
  width: 1px;
  height: 32px;
  background: var(--fsd-border);
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
