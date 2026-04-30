<template>
  <div class="dashboard">
    <div class="dashboard-header animate-fade-in-up">
      <div>
        <h2 class="dashboard-title">调度看板</h2>
        <span class="dashboard-subtitle">实时监控调度运行状态</span>
      </div>
      <div class="dashboard-actions">
        <span v-if="store.lastUpdated" class="last-updated">
          <ClockCircleOutlined /> {{ store.lastUpdated }}
        </span>
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
      <div class="recent-exceptions animate-fade-in-up stagger-3">
        <div class="panel-header">
          <h3 class="panel-title">
            <AlertOutlined /> 最近异常任务
          </h3>
          <a-button type="link" size="small" @click="router.push('/exceptions')">
            查看全部 <RightOutlined />
          </a-button>
        </div>
        <div class="exception-list">
          <template v-if="recentExceptions.length > 0">
            <div
              v-for="ex in recentExceptions"
              :key="ex.id"
              class="exception-item"
              @click="router.push(`/tasks/${ex.taskId}`)"
            >
              <div class="exception-type">
                <ExclamationCircleOutlined class="exception-icon" />
                {{ getExceptionLabel(ex.exceptionType) }}
              </div>
              <div class="exception-task">任务#{{ ex.taskId }}</div>
              <div class="exception-time">{{ formatRelativeTime(ex.occurTime) }}</div>
              <a-button size="small" type="primary" ghost @click.stop="router.push(`/exceptions`)">
                处理
              </a-button>
            </div>
          </template>
          <a-empty v-else :image="simpleImage" description="暂无未处理异常 🎉" />
        </div>
      </div>

      <div class="quick-actions animate-fade-in-up stagger-4">
        <div class="panel-header">
          <h3 class="panel-title"><ThunderboltOutlined /> 快捷操作</h3>
        </div>
        <div class="action-list">
          <div class="action-item" @click="router.push('/orders')">
            <div class="action-icon" style="background: rgba(0, 180, 216, 0.1);">
              <PlusOutlined style="color: #00B4D8;" />
            </div>
            <span>创建订单</span>
          </div>
          <div class="action-item" @click="router.push('/tasks?status=PENDING')">
            <div class="action-icon" style="background: rgba(255, 176, 32, 0.1);">
              <SendOutlined style="color: #FFB020;" />
            </div>
            <span>待派单任务</span>
          </div>
          <div class="action-item" @click="router.push('/exceptions?status=OPEN')">
            <div class="action-icon" style="background: rgba(255, 61, 113, 0.1);">
              <WarningOutlined style="color: #FF3D71;" />
            </div>
            <span>全部异常</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, markRaw } from 'vue'
import { useRouter } from 'vue-router'
import { Empty } from 'ant-design-vue'
import {
  ClockCircleOutlined,
  ReloadOutlined,
  RightOutlined,
  AlertOutlined,
  ExclamationCircleOutlined,
  ThunderboltOutlined,
  PlusOutlined,
  SendOutlined,
  WarningOutlined,
  FileTextOutlined,
  CarOutlined,
  ToolOutlined,
  DashboardOutlined,
} from '@ant-design/icons-vue'
import { useDashboardStore } from '@/stores/dashboard'
import { getExceptionList } from '@/api/exception'
import { exceptionTypeMap } from '@/constants/statusMap'
import { DASHBOARD_POLL_INTERVAL } from '@/config'
import type { ExceptionAdminListItem } from '@/types/exception'
import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import 'dayjs/locale/zh-cn'

dayjs.extend(relativeTime)
dayjs.locale('zh-cn')

const router = useRouter()
const store = useDashboardStore()
const simpleImage = Empty.PRESENTED_IMAGE_SIMPLE

const recentExceptions = ref<ExceptionAdminListItem[]>([])

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
      actionText: '查看详情',
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
      actionText: '查看详情',
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
      actionText: '查看详情',
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
      actionText: '立即处理',
      link: '/exceptions?status=OPEN',
      alert: (s?.failedCount ?? 0) > 0,
    },
  ]
})

function getExceptionLabel(type: string) {
  return exceptionTypeMap[type as keyof typeof exceptionTypeMap]?.label || type
}

function formatRelativeTime(time: string) {
  return dayjs(time).fromNow()
}

function handleCardClick(card: any) {
  if (card.link) router.push(card.link)
}

function handleRefresh() {
  store.fetchSummary()
  fetchRecentExceptions()
}

async function fetchRecentExceptions() {
  try {
    const res = await getExceptionList()
    const list = Array.isArray(res.data) ? res.data : (res.data as any)?.records || []
    recentExceptions.value = list.slice(0, 5)
  } catch {
    // silent
  }
}

let pollTimer: ReturnType<typeof setInterval> | null = null

onMounted(() => {
  store.fetchSummary()
  fetchRecentExceptions()
  pollTimer = setInterval(() => {
    store.fetchSummary()
    fetchRecentExceptions()
  }, DASHBOARD_POLL_INTERVAL)
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
  grid-template-columns: 2fr 1fr;
  gap: 16px;
}

.recent-exceptions,
.quick-actions {
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

.exception-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.exception-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-radius: var(--fsd-radius);
  cursor: pointer;
  transition: background 0.2s;

  &:hover {
    background: var(--fsd-bg-hover);
  }
}

.exception-type {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--fsd-text-primary);
  flex: 1;
}

.exception-icon {
  color: var(--fsd-error);
}

.exception-task {
  font-size: 12px;
  color: var(--fsd-text-tertiary);
  font-family: 'JetBrains Mono', monospace;
  margin: 0 16px;
}

.exception-time {
  font-size: 12px;
  color: var(--fsd-text-tertiary);
  margin-right: 12px;
}

.action-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.action-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border-radius: var(--fsd-radius);
  cursor: pointer;
  font-size: 14px;
  color: var(--fsd-text-primary);
  transition: all 0.2s;

  &:hover {
    background: var(--fsd-bg-hover);
  }
}

.action-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
}
</style>
