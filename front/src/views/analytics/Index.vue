<template>
  <PageContainer title="运营分析" subtitle="效率趋势 · 异常洞察 · 每日摘要">
    <div class="analytics-toolbar">
      <a-radio-group v-model:value="period" button-style="solid" @change="loadAll">
        <a-radio-button value="day">日</a-radio-button>
        <a-radio-button value="week">周</a-radio-button>
        <a-radio-button value="month">月</a-radio-button>
      </a-radio-group>
      <a-space>
        <a-button @click="router.push('/analytics/charging')">充电报表</a-button>
        <a-button :loading="loading" @click="loadAll">刷新</a-button>
        <a-dropdown>
          <a-button>导出 CSV</a-button>
          <template #overlay>
            <a-menu @click="handleExport">
              <a-menu-item key="orders">订单</a-menu-item>
              <a-menu-item key="tasks">任务</a-menu-item>
              <a-menu-item key="exceptions">异常</a-menu-item>
              <a-menu-item key="vehicles">车辆</a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>
      </a-space>
    </div>

    <a-spin :spinning="loading">
      <div class="analytics-grid">
        <section class="panel">
          <h3>订单完成率趋势</h3>
          <TrendBarChart v-if="efficiency" :points="efficiency.orderCompletionTrend" />
        </section>

        <section class="panel">
          <h3>异常趋势</h3>
          <TrendBarChart
            v-if="exceptionAnalysis"
            :points="exceptionAnalysis.exceptionTrend"
            metric="totalCount"
            color="#FF3D71"
          />
        </section>

        <section class="panel metrics-panel">
          <h3>效率指标</h3>
          <div class="metric-cards">
            <div class="metric-card">
              <span class="metric-label">平均任务时长</span>
              <span class="metric-value">{{ efficiency?.avgTaskDurationMinutes ?? '-' }}<small>分</small></span>
            </div>
            <div class="metric-card">
              <span class="metric-label">车辆利用率</span>
              <span class="metric-value">{{ efficiency?.vehicleUtilizationRate ?? '-' }}<small>%</small></span>
            </div>
            <div class="metric-card">
              <span class="metric-label">异常处理时长</span>
              <span class="metric-value">{{ exceptionAnalysis?.avgResolutionMinutes ?? '-' }}<small>分</small></span>
            </div>
          </div>
        </section>

        <section class="panel">
          <h3>异常类型分布</h3>
          <div class="type-list">
            <div v-for="item in exceptionAnalysis?.typeDistribution || []" :key="item.type" class="type-item">
              <span>{{ item.type }}</span>
              <span>{{ item.count }} · {{ item.ratio }}%</span>
            </div>
          </div>
        </section>

        <section class="panel summary-panel">
          <h3>每日运营摘要 · {{ dailySummary?.date }}</h3>
          <div class="summary-grid">
            <div><span>订单</span><strong>{{ dailySummary?.orderCompleted }}/{{ dailySummary?.orderTotal }}</strong></div>
            <div><span>完成率</span><strong>{{ dailySummary?.orderCompletionRate }}%</strong></div>
            <div><span>日环比</span><strong :class="rateClass(dailySummary?.dayOverDayOrderRate)">{{ formatRate(dailySummary?.dayOverDayOrderRate) }}</strong></div>
            <div><span>周同比</span><strong :class="rateClass(dailySummary?.weekOverWeekOrderRate)">{{ formatRate(dailySummary?.weekOverWeekOrderRate) }}</strong></div>
          </div>
          <ul class="highlight-list">
            <li v-for="(line, idx) in dailySummary?.highlightEvents || []" :key="idx">{{ line }}</li>
          </ul>
        </section>

        <section class="panel">
          <h3>跨园区对比</h3>
          <a-table
            v-if="parkCompare.length > 0"
            size="small"
            row-key="parkId"
            :pagination="false"
            :data-source="parkCompare"
            :columns="parkCompareColumns"
          />
          <a-empty v-else description="暂无园区对比数据" />
        </section>

        <section class="panel">
          <h3>高峰时段（订单/任务）</h3>
          <div class="peak-list">
            <div
              v-for="point in topPeakHours"
              :key="point.hour"
              class="peak-item"
            >
              <span>{{ String(point.hour).padStart(2, '0') }}:00</span>
              <span>订单 {{ point.orderCount }} · 任务 {{ point.taskCount }}</span>
            </div>
          </div>
        </section>
      </div>
    </a-spin>
  </PageContainer>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import PageContainer from '@/components/common/PageContainer.vue'
import TrendBarChart from '@/components/analytics/TrendBarChart.vue'
import {
  getAnalyticsDailySummary,
  getAnalyticsEfficiency,
  getAnalyticsExceptions,
  getAnalyticsExportUrl,
  getAnalyticsParkComparison,
} from '@/api/analytics'
import type {
  AnalyticsDailySummary,
  AnalyticsEfficiency,
  AnalyticsExceptionAnalysis,
  AnalyticsParkCompareItem,
} from '@/types/analytics'
import { useAuthStore } from '@/stores/auth'
import { ADMIN_AUTH_ENABLED } from '@/config'

const router = useRouter()
const authStore = useAuthStore()
const period = ref<'day' | 'week' | 'month'>('week')
const loading = ref(false)
const efficiency = ref<AnalyticsEfficiency | null>(null)
const exceptionAnalysis = ref<AnalyticsExceptionAnalysis | null>(null)
const dailySummary = ref<AnalyticsDailySummary | null>(null)
const parkCompare = ref<AnalyticsParkCompareItem[]>([])

const parkCompareColumns = [
  { title: '园区', dataIndex: 'parkName' },
  { title: '订单量', dataIndex: 'orderCount', width: 90 },
  { title: '成功任务', dataIndex: 'taskSuccessCount', width: 100 },
  { title: 'OPEN 异常', dataIndex: 'openExceptionCount', width: 100 },
]

const topPeakHours = computed(() => {
  const hours = efficiency.value?.peakHours || []
  return [...hours]
    .sort((a, b) => b.orderCount + b.taskCount - (a.orderCount + a.taskCount))
    .slice(0, 6)
})

function formatRate(value?: number) {
  if (value == null) return '-'
  return `${value > 0 ? '+' : ''}${value.toFixed(1)}%`
}

function rateClass(value?: number) {
  if (value == null) return ''
  return value >= 0 ? 'positive' : 'negative'
}

async function loadAll() {
  loading.value = true
  try {
    const [effRes, excRes, summaryRes, parkRes] = await Promise.all([
      getAnalyticsEfficiency(period.value),
      getAnalyticsExceptions(period.value),
      getAnalyticsDailySummary(),
      getAnalyticsParkComparison(period.value),
    ])
    efficiency.value = effRes.data
    exceptionAnalysis.value = excRes.data
    dailySummary.value = summaryRes.data
    parkCompare.value = parkRes.data
  } finally {
    loading.value = false
  }
}

function handleExport({ key }: { key: string }) {
  const url = getAnalyticsExportUrl(key, period.value)
  const tokenQuery =
    ADMIN_AUTH_ENABLED && authStore.token ? `${url.includes('?') ? '&' : '?'}token=${encodeURIComponent(authStore.token)}` : ''
  window.open(`${url}${tokenQuery}`, '_blank')
}

onMounted(loadAll)
</script>

<style scoped lang="less">
.analytics-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  gap: 12px;
  flex-wrap: wrap;
}

.analytics-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;

  @media (max-width: 1100px) {
    grid-template-columns: 1fr;
  }
}

.panel {
  background: var(--fsd-bg-base);
  border: 1px solid var(--fsd-border);
  border-radius: var(--fsd-radius-lg);
  padding: 20px;

  h3 {
    margin: 0 0 16px;
    font-size: 15px;
    color: var(--fsd-text-primary);
  }
}

.metric-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.metric-card {
  padding: 14px;
  border: 1px solid var(--fsd-border);
  border-radius: var(--fsd-radius);
  background: rgba(22, 27, 34, 0.45);
}

.metric-label {
  display: block;
  font-size: 12px;
  color: var(--fsd-text-tertiary);
}

.metric-value {
  display: block;
  margin-top: 8px;
  font-size: 28px;
  font-weight: 800;
  color: var(--fsd-accent);
  font-family: 'JetBrains Mono', monospace;

  small {
    font-size: 14px;
    margin-left: 4px;
  }
}

.type-list, .peak-list, .highlight-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.type-item, .peak-item {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: var(--fsd-text-secondary);
  padding: 8px 10px;
  border: 1px solid var(--fsd-border);
  border-radius: 8px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 16px;

  div {
    padding: 10px;
    border-radius: 8px;
    background: rgba(22, 27, 34, 0.45);
    border: 1px solid var(--fsd-border);

    span {
      display: block;
      font-size: 12px;
      color: var(--fsd-text-tertiary);
    }

    strong {
      display: block;
      margin-top: 6px;
      font-size: 18px;
      color: var(--fsd-text-primary);

      &.positive { color: #00e676; }
      &.negative { color: #ff3d71; }
    }
  }
}

.highlight-list {
  margin: 0;
  padding-left: 18px;
  color: var(--fsd-text-secondary);
  line-height: 1.7;
}
</style>
