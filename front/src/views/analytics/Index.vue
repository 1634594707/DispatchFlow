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
        <a-button @click="router.push('/analytics/custom-report')">自定义报表</a-button>
        <a-button @click="router.push('/analytics/report-history')">历史报表</a-button>
        <a-button :loading="loading" @click="loadAll">刷新</a-button>
        <a-button @click="exportPdf">导出 PDF</a-button>
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
          <TrendBarChart
            v-if="efficiency"
            :points="efficiency.orderCompletionTrend"
            clickable
            @bar-click="drillDownOrders"
          />
        </section>

        <section class="panel">
          <h3>异常趋势</h3>
          <TrendBarChart
            v-if="exceptionAnalysis"
            :points="exceptionAnalysis.exceptionTrend"
            metric="totalCount"
            color="var(--fsd-error)"
            clickable
            @bar-click="drillDownException"
          />
        </section>

        <section v-if="chainKpi" class="panel metrics-panel">
          <h3>
            链路 KPI
            <a-button type="link" size="small" @click="drillDownTasks(chainKpi)"
              >查看任务 →</a-button
            >
          </h3>
          <MetricStrip
            :items="chainKpiItems"
            aria-label="链路 KPI"
            @activate="drillDownTasks(chainKpi)"
          />
        </section>

        <section v-if="peakCompare" class="panel metrics-panel">
          <h3>高峰 vs 平日对比</h3>
          <div class="peak-compare-grid">
            <div class="clickable" @click="drillDownTasks(peakCompare.normalMode)">
              <h4>平日 NORMAL</h4>
              <p>完成均时 {{ peakCompare.normalMode.avgCompletionMinutes }} 分</p>
              <p>单车日均 {{ peakCompare.normalMode.tasksPerVehiclePerDay }}</p>
            </div>
            <div class="clickable" @click="drillDownTasks(peakCompare.peakMode)">
              <h4>高峰 PEAK</h4>
              <p>完成均时 {{ peakCompare.peakMode.avgCompletionMinutes }} 分</p>
              <p>单车日均 {{ peakCompare.peakMode.tasksPerVehiclePerDay }}</p>
            </div>
          </div>
          <a-button type="link" @click="router.push('/system/report-schedule')"
            >定时邮件报表配置 →</a-button
          >
        </section>

        <section class="panel metrics-panel">
          <h3>效率指标</h3>
          <MetricStrip
            :items="efficiencyItems"
            aria-label="效率指标"
            @activate="handleEfficiencyMetricActivate"
          />
        </section>

        <section class="panel">
          <h3>异常类型分布</h3>
          <div class="type-list">
            <div
              v-for="item in exceptionAnalysis?.typeDistribution || []"
              :key="item.type"
              class="type-item"
            >
              <span>{{ item.type }}</span>
              <span>{{ item.count }} · {{ item.ratio }}%</span>
            </div>
          </div>
        </section>

        <section class="panel summary-panel">
          <h3>每日运营摘要 · {{ dailySummary?.date }}</h3>
          <div class="summary-grid">
            <div>
              <span>订单</span
              ><strong>{{ dailySummary?.orderCompleted }}/{{ dailySummary?.orderTotal }}</strong>
            </div>
            <div>
              <span>完成率</span><strong>{{ dailySummary?.orderCompletionRate }}%</strong>
            </div>
            <div>
              <span>日环比</span
              ><strong :class="rateClass(dailySummary?.dayOverDayOrderRate)">{{
                formatRate(dailySummary?.dayOverDayOrderRate)
              }}</strong>
            </div>
            <div>
              <span>周同比</span
              ><strong :class="rateClass(dailySummary?.weekOverWeekOrderRate)">{{
                formatRate(dailySummary?.weekOverWeekOrderRate)
              }}</strong>
            </div>
          </div>
          <ul class="highlight-list">
            <li v-for="(line, idx) in dailySummary?.highlightEvents || []" :key="idx">
              {{ line }}
            </li>
          </ul>
        </section>

        <section v-if="parkScope.selectedParkId == null" class="panel">
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
            <div v-for="point in topPeakHours" :key="point.hour" class="peak-item">
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
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import PageContainer from '@/components/common/PageContainer.vue'
import TrendBarChart from '@/components/analytics/TrendBarChart.vue'
import MetricStrip from '@/components/common/MetricStrip.vue'
import type { MetricStripItem } from '@/components/common/MetricStrip.vue'
import {
  getAnalyticsDailySummary,
  getAnalyticsEfficiency,
  getAnalyticsExceptions,
  getAnalyticsChainKpi,
  getAnalyticsPeakCompare,
  type AnalyticsPeakCompare,
  getAnalyticsExportUrl,
  getAnalyticsPdfUrl,
  downloadAnalyticsFile,
  getAnalyticsParkComparison,
} from '@/api/analytics'
import type { AnalyticsChainKpi } from '@/api/analytics'
import type {
  AnalyticsDailySummary,
  AnalyticsEfficiency,
  AnalyticsExceptionAnalysis,
  AnalyticsParkCompareItem,
  AnalyticsTrendPoint,
} from '@/types/analytics'
import { useParkScopeStore } from '@/stores/parkScope'

const router = useRouter()
const parkScope = useParkScopeStore()
const period = ref<'day' | 'week' | 'month'>('week')
const loading = ref(false)
const efficiency = ref<AnalyticsEfficiency | null>(null)
const exceptionAnalysis = ref<AnalyticsExceptionAnalysis | null>(null)
const dailySummary = ref<AnalyticsDailySummary | null>(null)
const parkCompare = ref<AnalyticsParkCompareItem[]>([])
const chainKpi = ref<AnalyticsChainKpi | null>(null)
const peakCompare = ref<AnalyticsPeakCompare | null>(null)

const parkCompareColumns = [
  { title: '园区', dataIndex: 'parkName' },
  { title: '订单量', dataIndex: 'orderCount', width: 90 },
  { title: '成功任务', dataIndex: 'taskSuccessCount', width: 100 },
  { title: 'OPEN 异常', dataIndex: 'openExceptionCount', width: 100 },
]

const chainKpiItems = computed<MetricStripItem[]>(() => {
  if (!chainKpi.value) return []
  return [
    {
      key: 'completion',
      label: '完成均时',
      value: chainKpi.value.avgCompletionMinutes,
      unit: '分',
      tone: 'accent',
      actionable: true,
    },
    {
      key: 'wait-p50',
      label: '等待 P50',
      value: chainKpi.value.waitP50Minutes,
      unit: '分',
      tone: 'warning',
      actionable: true,
    },
    {
      key: 'wait-p90',
      label: '等待 P90',
      value: chainKpi.value.waitP90Minutes,
      unit: '分',
      tone: 'error',
      actionable: true,
    },
    {
      key: 'tasks-per-vehicle',
      label: '单车日均任务',
      value: chainKpi.value.tasksPerVehiclePerDay,
      tone: 'success',
      actionable: true,
    },
  ]
})

const efficiencyItems = computed<MetricStripItem[]>(() => [
  {
    key: 'duration',
    label: '平均任务时长',
    value: efficiency.value?.avgTaskDurationMinutes ?? '-',
    unit: '分',
    tone: 'accent',
    actionable: true,
  },
  {
    key: 'utilization',
    label: '车辆利用率',
    value: efficiency.value?.vehicleUtilizationRate ?? '-',
    unit: '%',
    tone: 'success',
    actionable: true,
  },
  {
    key: 'exceptions',
    label: '异常处理时长',
    value: exceptionAnalysis.value?.avgResolutionMinutes ?? '-',
    unit: '分',
    tone: 'error',
    actionable: true,
  },
])

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
    const parkId = parkScope.selectedParkId
    const requests = [
      getAnalyticsEfficiency(period.value, parkId),
      getAnalyticsExceptions(period.value, parkId),
      getAnalyticsDailySummary(undefined, parkId),
      getAnalyticsChainKpi(period.value, parkId),
      getAnalyticsPeakCompare(period.value, parkId),
    ] as const
    if (parkId == null) {
      const [effRes, excRes, summaryRes, chainRes, peakRes, parkRes] = await Promise.all([
        ...requests,
        getAnalyticsParkComparison(period.value),
      ])
      efficiency.value = effRes.data
      exceptionAnalysis.value = excRes.data
      dailySummary.value = summaryRes.data
      chainKpi.value = chainRes.data
      peakCompare.value = peakRes.data
      parkCompare.value = parkRes.data
    } else {
      const [effRes, excRes, summaryRes, chainRes, peakRes] = await Promise.all(requests)
      efficiency.value = effRes.data
      exceptionAnalysis.value = excRes.data
      dailySummary.value = summaryRes.data
      chainKpi.value = chainRes.data
      peakCompare.value = peakRes.data
      parkCompare.value = []
    }
  } finally {
    loading.value = false
  }
}

function drillDownException(point: AnalyticsTrendPoint) {
  void router.push({ path: '/exceptions', query: { status: 'OPEN', trendLabel: point.label } })
}

function drillDownOrders(point: AnalyticsTrendPoint) {
  void router.push({ path: '/orders', query: { status: 'COMPLETED', dateLabel: point.label } })
}

function handleEfficiencyMetricActivate(item: MetricStripItem) {
  if (item.key === 'duration') {
    drillDownEfficiency('duration')
    return
  }
  if (item.key === 'utilization') {
    drillDownEfficiency('utilization')
    return
  }
  void router.push('/exceptions')
}

function drillDownEfficiency(type: 'duration' | 'utilization') {
  const query: Record<string, string> = {}
  if (type === 'utilization') {
    query['status'] = 'EXECUTING'
  } else {
    query['status'] = 'SUCCESS'
  }
  void router.push({ path: '/tasks', query })
}

function drillDownTasks(_kpi?: AnalyticsChainKpi) {
  void router.push({ path: '/tasks', query: { status: 'SUCCESS' } })
}

async function exportPdf() {
  await downloadAnalyticsFile(
    getAnalyticsPdfUrl(dailySummary.value?.date, parkScope.selectedParkId),
  )
}

async function handleExport({ key }: { key: string }) {
  await downloadAnalyticsFile(getAnalyticsExportUrl(key, period.value, parkScope.selectedParkId))
}

onMounted(loadAll)

watch(
  () => parkScope.scopeVersion,
  () => loadAll(),
)
</script>

<style scoped lang="less">
.analytics-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--fsd-space-3);
  margin-bottom: var(--fsd-space-4);
  flex-wrap: wrap;
}

.analytics-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--fsd-space-4);

  @media (max-width: 1100px) {
    grid-template-columns: 1fr;
  }
}

.panel {
  border: 1px solid var(--fsd-border);
  border-radius: var(--fsd-radius-md);
  background: var(--fsd-surface-raised);
  padding: var(--fsd-space-4);

  h3 {
    margin: 0 0 var(--fsd-space-3);
    color: var(--fsd-text-primary);
    font-size: var(--fsd-text-md);
    font-weight: var(--fsd-font-semibold);
  }
}

.metrics-panel {
  border: 0;
  border-radius: 0;
  background: transparent;
  padding: 0;
}

.type-list,
.peak-list,
.highlight-list {
  display: flex;
  flex-direction: column;
}

.type-item,
.peak-item {
  display: flex;
  justify-content: space-between;
  gap: var(--fsd-space-3);
  padding: var(--fsd-space-2) 0;
  border-bottom: 1px solid var(--fsd-border-split);
  color: var(--fsd-text-secondary);
  font-size: var(--fsd-text-sm);
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin-bottom: var(--fsd-space-4);
  border-block: 1px solid var(--fsd-border-split);

  div {
    min-width: 0;
    padding: var(--fsd-space-3);
    border-right: 1px solid var(--fsd-border-split);

    span {
      display: block;
      font-size: 12px;
      color: var(--fsd-text-tertiary);
    }

    strong {
      display: block;
      margin-top: var(--fsd-space-1);
      font-family: var(--fsd-font-mono);
      font-size: var(--fsd-text-lg);
      color: var(--fsd-text-primary);

      &.positive {
        color: var(--fsd-success);
      }
      &.negative {
        color: var(--fsd-error);
      }
    }
  }
}

.highlight-list {
  gap: var(--fsd-space-2);
  margin: 0;
  padding-left: var(--fsd-space-4);
  color: var(--fsd-text-secondary);
  line-height: 1.6;
}

.peak-compare-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  border-block: 1px solid var(--fsd-border-split);
}

.peak-compare-grid .clickable {
  min-height: var(--fsd-touch-target-min);
  padding: var(--fsd-space-3);
  border-right: 1px solid var(--fsd-border-split);
  cursor: pointer;
}

.peak-compare-grid .clickable:hover {
  background: var(--fsd-bg-hover);
}

.peak-compare-grid h4,
.peak-compare-grid p {
  margin: 0;
}

.peak-compare-grid p {
  margin-top: var(--fsd-space-1);
  color: var(--fsd-text-secondary);
  font-size: var(--fsd-text-sm);
}

@media (max-width: 560px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .summary-grid div:nth-child(2n) {
    border-right: 0;
  }
}
</style>
