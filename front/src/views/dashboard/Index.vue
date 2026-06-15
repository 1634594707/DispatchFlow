<template>
  <div class="dashboard">
    <!-- Header -->
    <div class="dashboard-header animate-fade-in-up">
      <div>
        <h2 class="dashboard-title">调度看板</h2>
        <span class="dashboard-subtitle">运营 KPI 总览 · 派车与异常处置请前往工作台</span>
      </div>
      <div class="dashboard-actions">
        <span v-if="store.lastUpdated && !resp.isPhone.value" class="last-updated">
          <ClockCircleOutlined /> {{ store.lastUpdated }}
        </span>
        <a-button type="primary" size="large" class="dashboard-btn" @click="router.push('/workbench')">
          <ControlOutlined /> {{ resp.isPhone.value ? '工作台' : '进入工作台' }}
        </a-button>
        <a-button v-if="!resp.isPhone.value" :loading="store.loading" size="large" @click="handleRefresh">
          <ReloadOutlined /> 刷新
        </a-button>
      </div>
    </div>

    <!-- Stat Cards -->
    <div v-if="store.loading && !store.summary" class="dashboard-skeleton">
      <SkeletonLoader variant="stats" :count="resp.statGridCols.value" />
    </div>
    <div v-else class="stat-cards" :style="{ gridTemplateColumns: `repeat(${resp.statGridCols.value}, 1fr)` }">
      <MetricCard
        v-for="(card, idx) in statCards"
        :key="card.key"
        :class="['animate-fade-in-up', `stagger-${idx + 1}`]"
        variant="stat"
        :label="card.label"
        :value="card.value"
        :icon="card.icon"
        :icon-bg="card.iconBg"
        :icon-color="card.iconColor"
        :value-color="card.valueColor"
        :color-theme="card.colorTheme"
        :action-text="resp.isPhone.value ? undefined : card.actionText"
        :alert="card.alert"
        :clickable="true"
        :loading="store.loading"
        @click="handleCardClick(card)"
      />
    </div>

    <!-- Bottom Section: Trend + Quick Nav -->
    <div class="dashboard-bottom">
      <div class="overview-panel animate-fade-in-up stagger-3">
        <div class="panel-header">
          <h3 class="panel-title">
            <LineChartOutlined /> 运营趋势
          </h3>
          <a-button v-if="!resp.isPhone.value" type="link" size="small" @click="router.push('/analytics')">
            查看完整报表
          </a-button>
        </div>
        <SkeletonLoader v-if="trendLoading && trendPoints.length === 0" variant="chart" />
        <template v-else>
          <TrendBarChart v-if="trendPoints.length > 0" :points="trendPoints" />
          <div v-else class="trend-placeholder">
            <BarChartOutlined class="trend-icon" />
            <p class="trend-text-primary">暂无趋势数据</p>
            <p class="trend-text-secondary">运营数据将在系统运行后自动生成</p>
          </div>
        </template>
      </div>

      <div class="nav-panel animate-fade-in-up stagger-4">
        <div class="panel-header">
          <h3 class="panel-title"><CompassOutlined /> 业务入口</h3>
        </div>
        <div class="nav-list">
          <button
            v-for="item in navItems"
            :key="item.key"
            type="button"
            class="nav-item"
            :class="{ 'nav-item--primary': item.primary }"
            @click="router.push(item.link)"
          >
            <div class="nav-icon" :style="{ background: item.iconBg }">
              <component :is="item.icon" :style="{ color: item.iconColor, fontSize: '18px' }" />
            </div>
            <div class="nav-text">
              <span class="nav-label">{{ item.label }}</span>
              <span class="nav-desc">{{ item.desc }}</span>
            </div>
            <RightOutlined class="nav-arrow" />
          </button>
        </div>

        <!-- Mobile-only: analytics link -->
        <a-button
          v-if="resp.isPhone.value"
          type="link"
          block
          class="mobile-analytics-link"
          @click="router.push('/analytics')"
        >
          <BarChartOutlined /> 查看完整分析报表
        </a-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, markRaw, ref } from 'vue'
import { useRouter } from 'vue-router'
import TrendBarChart from '@/components/analytics/TrendBarChart.vue'
import MetricCard from '@/components/common/MetricCard.vue'
import SkeletonLoader from '@/components/common/SkeletonLoader.vue'
import { getAnalyticsEfficiency } from '@/api/analytics'
import { useResponsive } from '@/composables/useResponsive'
import type { AnalyticsTrendPoint } from '@/types/analytics'
import {
  ClockCircleOutlined,
  ReloadOutlined,
  RightOutlined,
  AlertOutlined,
  FileTextOutlined,
  CarOutlined,
  ToolOutlined,
  ControlOutlined,
  HeatMapOutlined,
  LineChartOutlined,
  BarChartOutlined,
  CompassOutlined,
  UnorderedListOutlined,
} from '@ant-design/icons-vue'
import { useDashboardStore } from '@/stores/dashboard'

const router = useRouter()
const store = useDashboardStore()
const resp = useResponsive()
const trendLoading = ref(false)
const trendPoints = ref<AnalyticsTrendPoint[]>([])

async function loadTrend() {
  trendLoading.value = true
  try {
    const res = await getAnalyticsEfficiency('week')
    trendPoints.value = res.data.orderCompletionTrend || []
  } catch {
    trendPoints.value = []
  } finally {
    trendLoading.value = false
  }
}

const statCards = computed(() => {
  const s = store.summary
  return [
    {
      key: 'pending',
      label: '待调度订单',
      value: s?.pendingCount ?? '-',
      icon: markRaw(FileTextOutlined),
      iconBg: 'rgba(34, 199, 230, 0.10)',
      iconColor: '#22C7E6',
      colorTheme: 'cyan' as const,
      valueColor: '#22C7E6',
      actionText: '订单列表',
      link: '/orders?status=WAITING_DISPATCH',
      alert: false,
    },
    {
      key: 'executing',
      label: '执行中任务',
      value: s?.executingCount ?? '-',
      icon: markRaw(CarOutlined),
      iconBg: 'rgba(255, 192, 77, 0.10)',
      iconColor: '#FFC04D',
      colorTheme: 'amber' as const,
      valueColor: '#FFC04D',
      actionText: '任务列表',
      link: '/tasks?status=EXECUTING',
      alert: false,
    },
    {
      key: 'vehicles',
      label: '在线车辆',
      value: s?.onlineVehicleCount ?? '-',
      icon: markRaw(ToolOutlined),
      iconBg: 'rgba(45, 224, 138, 0.10)',
      iconColor: '#2DE08A',
      colorTheme: 'green' as const,
      valueColor: '#2DE08A',
      actionText: '车辆列表',
      link: '/vehicles?onlineStatus=ONLINE',
      alert: false,
    },
    {
      key: 'exceptions',
      label: '未处理异常',
      value: s?.openExceptionCount ?? s?.failedCount ?? '-',
      icon: markRaw(AlertOutlined),
      iconBg: 'rgba(255, 92, 124, 0.10)',
      iconColor: '#FF5C7C',
      colorTheme: 'red' as const,
      valueColor: '#FF5C7C',
      actionText: '前往工作台',
      link: '/workbench',
      alert: (s?.openExceptionCount ?? s?.failedCount ?? 0) > 0,
    },
  ]
})

const navItems = [
  {
    key: 'workbench',
    label: '调度工作台',
    desc: '任务池 · 派车 · 异常处置',
    link: '/workbench',
    icon: markRaw(ControlOutlined),
    iconBg: 'rgba(0, 180, 216, 0.12)',
    iconColor: '#00B4D8',
    primary: true,
  },
  {
    key: 'tracking',
    label: '车辆监控大屏',
    desc: '实时地图 · 车队态势',
    link: '/vehicle-tracking',
    icon: markRaw(HeatMapOutlined),
    iconBg: 'rgba(0, 230, 118, 0.1)',
    iconColor: '#00E676',
    primary: false,
  },
  {
    key: 'orders',
    label: '订单管理',
    desc: '创建与查询运输订单',
    link: '/orders',
    icon: markRaw(FileTextOutlined),
    iconBg: 'rgba(0, 180, 216, 0.08)',
    iconColor: '#00B4D8',
    primary: false,
  },
  {
    key: 'exceptions',
    label: '异常记录',
    desc: '历史异常查询与归档',
    link: '/exceptions',
    icon: markRaw(UnorderedListOutlined),
    iconBg: 'rgba(255, 61, 113, 0.08)',
    iconColor: '#FF3D71',
    primary: false,
  },
]

function handleCardClick(card: { link?: string }) {
  if (card.link) router.push(card.link)
}

function handleRefresh() {
  store.fetchSummary()
  loadTrend()
}

onMounted(() => {
  store.fetchSummary()
  loadTrend()
})
</script>

<style scoped lang="less">
.dashboard {
  display: flex;
  flex-direction: column;
  gap: var(--fsd-space-5);
}

/* ── Header ─────────────────────────────────────────────── */
.dashboard-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: var(--fsd-space-4);
  flex-wrap: wrap;
}

.dashboard-title {
  font-size: var(--fsd-text-2xl);
  font-weight: var(--fsd-font-bold);
  color: var(--fsd-text-primary);
  margin: 0;
  letter-spacing: var(--fsd-tracking-tight);
  line-height: var(--fsd-leading-tight);
}

.dashboard-subtitle {
  font-size: var(--fsd-text-sm);
  color: var(--fsd-text-tertiary);
  margin-top: 4px;
  display: block;
}

.dashboard-actions {
  display: flex;
  align-items: center;
  gap: var(--fsd-space-3);
  flex-shrink: 0;
}

.dashboard-btn {
  white-space: nowrap;
}

.last-updated {
  font-size: var(--fsd-text-xs);
  color: var(--fsd-text-tertiary);
  font-family: var(--fsd-font-mono);
  display: flex;
  align-items: center;
  gap: 4px;
}

/* ── Skeleton ───────────────────────────────────────────── */
.dashboard-skeleton {
  min-height: 120px;
}

/* ── Stat Cards Grid ────────────────────────────────────── */
.stat-cards {
  display: grid;
  gap: var(--fsd-space-4);
}

/* ── Bottom section ─────────────────────────────────────── */
.dashboard-bottom {
  display: grid;
  grid-template-columns: 1.4fr 1fr;
  gap: var(--fsd-space-4);

  @media (max-width: 991px) {
    grid-template-columns: 1fr;
  }
}

/* ── Panels ─────────────────────────────────────────────── */
.overview-panel,
.nav-panel {
  background: var(--fsd-bg-base);
  border: 1px solid var(--fsd-border);
  border-radius: var(--fsd-radius-lg);
  padding: var(--fsd-space-5);
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--fsd-space-4);
}

.panel-title {
  font-size: var(--fsd-text-md);
  font-weight: var(--fsd-font-semibold);
  color: var(--fsd-text-primary);
  margin: 0;
  display: flex;
  align-items: center;
  gap: var(--fsd-space-2);
}

/* ── Trend ──────────────────────────────────────────────── */
.trend-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 200px;
  padding: var(--fsd-space-6);
  border: 1px dashed var(--fsd-border);
  border-radius: var(--fsd-radius);
  text-align: center;
}

.trend-icon {
  font-size: 40px;
  color: var(--fsd-text-tertiary);
  margin-bottom: var(--fsd-space-3);
}

.trend-text-primary {
  font-size: var(--fsd-text-md);
  font-weight: var(--fsd-font-semibold);
  color: var(--fsd-text-secondary);
  margin: 0 0 var(--fsd-space-2);
}

.trend-text-secondary {
  font-size: var(--fsd-text-sm);
  color: var(--fsd-text-tertiary);
  line-height: var(--fsd-leading-relaxed);
  max-width: 360px;
  margin: 0;
}

/* ── Nav List ───────────────────────────────────────────── */
.nav-list {
  display: flex;
  flex-direction: column;
  gap: var(--fsd-space-2);
}

.nav-item {
  display: flex;
  align-items: center;
  gap: var(--fsd-space-3);
  width: 100%;
  padding: 14px var(--fsd-space-4);
  border: 1px solid var(--fsd-border);
  border-radius: var(--fsd-radius);
  background: rgba(22, 27, 34, 0.4);
  cursor: pointer;
  text-align: left;
  transition: border-color 0.2s var(--fsd-ease), background 0.2s var(--fsd-ease);
  min-height: var(--fsd-touch-target-min);

  &:hover {
    border-color: rgba(0, 180, 216, 0.35);
    background: rgba(0, 180, 216, 0.06);
  }

  &:active {
    transform: scale(0.985);
  }

  &--primary {
    border-color: rgba(0, 180, 216, 0.25);
    background: rgba(0, 180, 216, 0.08);
  }
}

.nav-icon {
  width: 40px;
  height: 40px;
  border-radius: var(--fsd-radius);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;

  @media (max-width: 575px) {
    width: 44px;
    height: 44px;
  }
}

.nav-text {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.nav-label {
  font-size: var(--fsd-text-base);
  font-weight: var(--fsd-font-semibold);
  color: var(--fsd-text-primary);
}

.nav-desc {
  font-size: var(--fsd-text-xs);
  color: var(--fsd-text-tertiary);
}

.nav-arrow {
  color: var(--fsd-text-tertiary);
  font-size: var(--fsd-text-xs);
  flex-shrink: 0;
}

/* ── Mobile analytics link ──────────────────────────────── */
.mobile-analytics-link {
  margin-top: var(--fsd-space-3);
  justify-content: center;
  font-size: var(--fsd-text-base);
}

/* ── Responsive: Tablet ─────────────────────────────────── */
@media (max-width: 991px) {
  .dashboard-bottom {
    grid-template-columns: 1fr;
  }
}

/* ── Responsive: Phone ──────────────────────────────────── */
@media (max-width: 575px) {
  .dashboard {
    gap: var(--fsd-space-4);
  }

  .dashboard-header {
    flex-direction: column;
  }

  .dashboard-title {
    font-size: var(--fsd-text-xl);
  }

  .dashboard-actions {
    width: 100%;
  }

  .dashboard-actions .ant-btn {
    flex: 1;
    justify-content: center;
  }

  .stat-cards {
    gap: var(--fsd-space-3);
  }

  .overview-panel,
  .nav-panel {
    padding: var(--fsd-space-4);
  }

  .nav-item {
    padding: 16px var(--fsd-space-4);
  }
}
</style>
