<template>
  <div class="dashboard">
    <div class="dashboard-header animate-fade-in-up">
      <div>
        <h2 class="dashboard-title">调度看板</h2>
        <span class="dashboard-subtitle">运营 KPI 总览 · 派车与异常处置请前往工作台</span>
      </div>
      <div class="dashboard-actions">
        <span v-if="store.lastUpdated" class="last-updated">
          <ClockCircleOutlined /> {{ store.lastUpdated }}
        </span>
        <a-button type="primary" @click="router.push('/workbench')">
          <ControlOutlined /> 进入工作台
        </a-button>
        <a-button :loading="store.loading" @click="handleRefresh">
          <ReloadOutlined /> 刷新
        </a-button>
      </div>
    </div>

    <div class="stat-cards">
      <div
        v-for="(card, idx) in statCards"
        :key="card.key"
        class="stat-card animate-fade-in-up"
        :class="[`stagger-${idx + 1}`, { 'stat-card--alert': card.alert }]"
        @click="handleCardClick(card)"
      >
        <div class="stat-card-icon" :style="{ background: card.iconBg }">
          <component :is="card.icon" :style="{ color: card.iconColor, fontSize: '22px' }" />
        </div>
        <div class="stat-card-info">
          <div class="stat-card-label">{{ card.label }}</div>
          <div class="stat-card-value" :style="{ color: card.valueColor }">{{ card.value }}</div>
        </div>
        <div class="stat-card-action">
          {{ card.actionText }} <RightOutlined />
        </div>
      </div>
    </div>

    <div class="dashboard-bottom">
      <div class="overview-panel animate-fade-in-up stagger-3">
        <div class="panel-header">
          <h3 class="panel-title">
            <LineChartOutlined /> 运营趋势
          </h3>
          <a-tag color="default">Phase 6</a-tag>
        </div>
        <div class="trend-placeholder">
          <BarChartOutlined class="trend-icon" />
          <p class="trend-title">趋势报表即将上线</p>
          <p class="trend-desc">
            订单完成率、任务时长、车辆利用率等运营指标将在此展示。
            当前请使用下方入口进入各业务模块。
          </p>
        </div>
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
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, markRaw } from 'vue'
import { useRouter } from 'vue-router'
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
import { DASHBOARD_POLL_INTERVAL } from '@/config'

const router = useRouter()
const store = useDashboardStore()

const statCards = computed(() => {
  const s = store.summary
  return [
    {
      key: 'pending',
      label: '待调度订单',
      value: s?.pendingCount ?? '-',
      icon: markRaw(FileTextOutlined),
      iconBg: 'rgba(0, 180, 216, 0.1)',
      iconColor: '#00B4D8',
      valueColor: '#00B4D8',
      actionText: '订单列表',
      link: '/orders?status=WAITING_DISPATCH',
      alert: false,
    },
    {
      key: 'executing',
      label: '执行中任务',
      value: s?.executingCount ?? '-',
      icon: markRaw(CarOutlined),
      iconBg: 'rgba(255, 176, 32, 0.1)',
      iconColor: '#FFB020',
      valueColor: '#FFB020',
      actionText: '任务列表',
      link: '/tasks?status=EXECUTING',
      alert: false,
    },
    {
      key: 'vehicles',
      label: '在线车辆',
      value: s?.onlineVehicleCount ?? '-',
      icon: markRaw(ToolOutlined),
      iconBg: 'rgba(0, 230, 118, 0.1)',
      iconColor: '#00E676',
      valueColor: '#00E676',
      actionText: '车辆列表',
      link: '/vehicles?onlineStatus=ONLINE',
      alert: false,
    },
    {
      key: 'exceptions',
      label: '未处理异常',
      value: s?.failedCount ?? '-',
      icon: markRaw(AlertOutlined),
      iconBg: 'rgba(255, 61, 113, 0.1)',
      iconColor: '#FF3D71',
      valueColor: '#FF3D71',
      actionText: '前往工作台',
      link: '/workbench',
      alert: (s?.failedCount ?? 0) > 0,
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
}

let pollTimer: ReturnType<typeof setInterval> | null = null

onMounted(() => {
  store.fetchSummary()
  pollTimer = setInterval(() => store.fetchSummary(), DASHBOARD_POLL_INTERVAL)
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
})
</script>

<style scoped lang="less">
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.dashboard-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.dashboard-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--fsd-text-primary);
  margin: 0;
  letter-spacing: -0.02em;
}

.dashboard-subtitle {
  font-size: 13px;
  color: var(--fsd-text-tertiary);
  margin-top: 4px;
  display: block;
}

.dashboard-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.last-updated {
  font-size: 12px;
  color: var(--fsd-text-tertiary);
  font-family: 'JetBrains Mono', monospace;
  display: flex;
  align-items: center;
  gap: 4px;
}

.stat-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;

  @media (max-width: 1100px) {
    grid-template-columns: repeat(2, 1fr);
  }
}

.stat-card {
  background: var(--fsd-bg-base);
  border: 1px solid var(--fsd-border);
  border-radius: var(--fsd-radius-lg);
  padding: 20px;
  cursor: pointer;
  transition: all 0.25s ease;
  position: relative;
  overflow: hidden;

  &::before {
    content: '';
    position: absolute;
    inset: 0;
    background: var(--fsd-gradient-card);
    pointer-events: none;
  }

  &:hover {
    border-color: var(--fsd-border-active);
    transform: translateY(-2px);
    box-shadow: var(--fsd-shadow-elevated);
  }

  &--alert {
    border-color: rgba(255, 61, 113, 0.3);
    animation: pulse-glow 2s infinite;
  }
}

.stat-card-icon {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 16px;
}

.stat-card-label {
  font-size: 13px;
  color: var(--fsd-text-secondary);
  margin-bottom: 6px;
}

.stat-card-value {
  font-size: 32px;
  font-weight: 800;
  font-family: 'JetBrains Mono', monospace;
  letter-spacing: -0.04em;
  line-height: 1;
}

.stat-card-action {
  margin-top: 14px;
  font-size: 12px;
  color: var(--fsd-text-tertiary);
  display: flex;
  align-items: center;
  gap: 4px;
  transition: color 0.2s;

  .stat-card:hover & {
    color: var(--fsd-accent);
  }
}

.dashboard-bottom {
  display: grid;
  grid-template-columns: 1.4fr 1fr;
  gap: 16px;

  @media (max-width: 900px) {
    grid-template-columns: 1fr;
  }
}

.overview-panel,
.nav-panel {
  background: var(--fsd-bg-base);
  border: 1px solid var(--fsd-border);
  border-radius: var(--fsd-radius-lg);
  padding: 20px;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.panel-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--fsd-text-primary);
  margin: 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.trend-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 200px;
  padding: 24px;
  border: 1px dashed var(--fsd-border);
  border-radius: var(--fsd-radius);
  text-align: center;
}

.trend-icon {
  font-size: 40px;
  color: var(--fsd-text-tertiary);
  margin-bottom: 12px;
}

.trend-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--fsd-text-secondary);
  margin: 0 0 8px;
}

.trend-desc {
  font-size: 13px;
  color: var(--fsd-text-tertiary);
  line-height: 1.6;
  max-width: 360px;
  margin: 0;
}

.nav-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 14px 16px;
  border: 1px solid var(--fsd-border);
  border-radius: var(--fsd-radius);
  background: rgba(22, 27, 34, 0.4);
  cursor: pointer;
  text-align: left;
  transition: border-color 0.2s, background 0.2s;

  &:hover {
    border-color: rgba(0, 180, 216, 0.35);
    background: rgba(0, 180, 216, 0.06);
  }

  &--primary {
    border-color: rgba(0, 180, 216, 0.25);
    background: rgba(0, 180, 216, 0.08);
  }
}

.nav-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.nav-text {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.nav-label {
  font-size: 14px;
  font-weight: 600;
  color: var(--fsd-text-primary);
}

.nav-desc {
  font-size: 12px;
  color: var(--fsd-text-tertiary);
}

.nav-arrow {
  color: var(--fsd-text-tertiary);
  font-size: 12px;
}
</style>
