<template>
  <PageContainer title="系统健康监控" subtitle="MySQL · Redis · RabbitMQ · Outbox · API 延迟 · JVM">
    <template #actions>
      <a-space>
        <a-tag v-if="autoRefresh" color="processing">自动刷新中 (30s)</a-tag>
        <a-button :loading="loading" @click="load">
          <ReloadOutlined /> 刷新
        </a-button>
      </a-space>
    </template>

    <a-spin :spinning="loading">
      <!-- 整体状态 -->
      <a-alert
        v-if="health"
        :type="statusType(health.overallStatus)"
        show-icon
        :message="`整体状态：${health.overallStatus}`"
        :description="`检测时间 ${formatTime(health.checkedAt)}`"
        class="overall-alert"
      />

      <!-- 组件状态卡片 -->
      <div v-if="health" class="component-grid">
        <article
          v-for="item in health.components"
          :key="item.name"
          class="component-card"
          :class="statusClass(item.status)"
        >
          <div class="card-head">
            <h3>{{ componentLabel(item.name) }}</h3>
            <a-tag :color="tagColor(item.status)">{{ item.status }}</a-tag>
          </div>
          <p class="card-message">{{ item.message }}</p>
          <ul v-if="item.details && Object.keys(item.details).length" class="detail-list">
            <li v-for="(value, key) in item.details" :key="key">
              <span>{{ detailLabel(key) }}</span>
              <strong>{{ formatDetailValue(key, value) }}</strong>
            </li>
          </ul>
        </article>
      </div>

      <!-- V5-S3: 扩展指标卡片 -->
      <a-alert
        v-if="metricsError"
        type="warning"
        show-icon
        :message="metricsError"
        class="section-alert"
      />
      <div v-if="metrics" class="metrics-section">
        <h3 class="section-title">详细指标</h3>
        <div class="metrics-grid">
          <!-- MQ 堆积量 -->
          <div class="metric-card" :class="metricCardClass(mqStatus)">
            <div class="metric-header">
              <span class="metric-icon">📨</span>
              <span class="metric-label">MQ 堆积量</span>
            </div>
            <div class="metric-value">{{ totalMqBacklog }}</div>
            <div class="metric-detail">
              <div v-for="q in metrics.mqBacklogs" :key="q.queueName" class="metric-row">
                <span class="metric-row-label">{{ q.queueName }}</span>
                <a-tag :color="metricTagColor(q.status)" size="small">{{ q.backlog }}</a-tag>
              </div>
            </div>
          </div>

          <!-- 数据库连接池 -->
          <div class="metric-card" :class="metricCardClass(metrics.dbConnectionPool.status)">
            <div class="metric-header">
              <span class="metric-icon">🗄️</span>
              <span class="metric-label">数据库连接池</span>
            </div>
            <div class="metric-value">{{ metrics.dbConnectionPool.usagePercent.toFixed(1) }}%</div>
            <div class="metric-detail">
              <div class="metric-row"><span class="metric-row-label">活跃</span><strong>{{ metrics.dbConnectionPool.active }}</strong></div>
              <div class="metric-row"><span class="metric-row-label">空闲</span><strong>{{ metrics.dbConnectionPool.idle }}</strong></div>
              <div class="metric-row"><span class="metric-row-label">最大</span><strong>{{ metrics.dbConnectionPool.max }}</strong></div>
            </div>
          </div>

          <!-- Redis 内存 -->
          <div class="metric-card" :class="metricCardClass(metrics.redisMemory.status)">
            <div class="metric-header">
              <span class="metric-icon">💾</span>
              <span class="metric-label">Redis 内存占用</span>
            </div>
            <div class="metric-value">{{ metrics.redisMemory.usagePercent.toFixed(1) }}%</div>
            <div class="metric-detail">
              <div class="metric-row"><span class="metric-row-label">已用</span><strong>{{ formatBytes(metrics.redisMemory.usedBytes) }}</strong></div>
              <div class="metric-row"><span class="metric-row-label">最大</span><strong>{{ formatBytes(metrics.redisMemory.maxBytes) }}</strong></div>
            </div>
          </div>

          <!-- SSE 连接 -->
          <div class="metric-card" :class="metricCardClass(metrics.sseConnections.status)">
            <div class="metric-header">
              <span class="metric-icon">🔗</span>
              <span class="metric-label">SSE 连接数</span>
            </div>
            <div class="metric-value">{{ metrics.sseConnections.activeConnections }}</div>
            <div class="metric-detail">
              <a-tag :color="sseDotColor">{{ sseStatusLabel }}</a-tag>
            </div>
          </div>

          <!-- API P99 响应时间 -->
          <div class="metric-card metric-card-wide" :class="metricCardClass(metrics.apiP99Latency.status)">
            <div class="metric-header">
              <span class="metric-icon">⏱️</span>
              <span class="metric-label">API P99 响应时间</span>
            </div>
            <div class="metric-value">{{ metrics.apiP99Latency.currentMs }} ms</div>
            <div class="metric-detail">
              <div class="metric-row"><span class="metric-row-label">P50</span><strong>{{ metrics.apiP99Latency.p50Ms }} ms</strong></div>
              <div class="metric-row"><span class="metric-row-label">P95</span><strong>{{ metrics.apiP99Latency.p95Ms }} ms</strong></div>
              <div class="metric-row"><span class="metric-row-label">P99</span><strong>{{ metrics.apiP99Latency.p99Ms }} ms</strong></div>
            </div>
            <!-- 趋势图 -->
            <div v-if="latencyHistory.length" class="trend-chart">
              <div class="trend-chart-title">过去 24 小时 P99 趋势</div>
              <div class="trend-bars">
                <div
                  v-for="(point, idx) in latencyHistory"
                  :key="idx"
                  class="trend-bar-wrapper"
                  :title="`${formatTimeShort(point.time)}: ${point.value}ms`"
                >
                  <div
                    class="trend-bar"
                    :style="{ height: trendBarHeight(point.value) + '%' }"
                    :class="trendBarColor(point.value)"
                  />
                </div>
              </div>
              <div class="trend-labels">
                <span>24h 前</span>
                <span>现在</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- V5-S3: 健康时间线 -->
      <a-alert
        v-if="timelineError"
        type="warning"
        show-icon
        :message="timelineError"
        class="section-alert"
      />
      <div v-if="timelineItems.length" class="timeline-section">
        <h3 class="section-title">健康时间线</h3>
        <div class="timeline-list">
          <div v-for="(item, idx) in timelineItems" :key="idx" class="timeline-item">
            <div class="timeline-dot" :class="'dot-' + item.status.toLowerCase()" />
            <div class="timeline-content">
              <div class="timeline-header">
                <a-tag :color="tagColor(item.status)" size="small">{{ item.status }}</a-tag>
                <span class="timeline-component">{{ componentLabel(item.component) }}</span>
                <span class="timeline-time">{{ formatTime(item.time) }}</span>
              </div>
              <p class="timeline-message">{{ item.message }}</p>
            </div>
          </div>
        </div>
      </div>
    </a-spin>
  </PageContainer>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref, computed } from 'vue'
import { ReloadOutlined } from '@ant-design/icons-vue'
import dayjs from 'dayjs'
import PageContainer from '@/components/common/PageContainer.vue'
import { getSystemHealth, getDetailedMetrics, getHealthTimeline } from '@/api/systemHealth'
import type { SystemHealthResponse, DetailedMetricsResponse, HealthTimelineItem } from '@/types/phase10'

const loading = ref(false)
const health = ref<SystemHealthResponse | null>(null)
const metrics = ref<DetailedMetricsResponse | null>(null)
const timelineItems = ref<HealthTimelineItem[]>([])
const metricsError = ref('')
const timelineError = ref('')
const autoRefresh = ref(true)
let refreshTimer: ReturnType<typeof setInterval> | null = null

const labelMap: Record<string, string> = {
  mysql: 'MySQL 数据库',
  redis: 'Redis 缓存',
  rabbitmq: 'RabbitMQ 消息队列',
  'event-outbox': '事件 Outbox',
  'api-latency': 'API 响应时间',
  jvm: 'JVM 资源',
}

const detailLabelMap: Record<string, string> = {
  catalog: '数据库',
  ping: 'Ping',
  pending: '待发布',
  failed: '失败',
  requestCount: '请求数',
  averageMs: '平均耗时 (ms)',
  maxMs: '最大耗时 (ms)',
  recentAverageMs: '近期平均 (ms)',
  heapUsagePercent: '堆内存 (%)',
  processors: 'CPU 核数',
  systemLoadAverage: '系统负载',
  totalBacklog: '队列积压',
}

/* 计算属性 */
const totalMqBacklog = computed(() => {
  if (!metrics.value) return 0
  return metrics.value.mqBacklogs.reduce((sum, q) => sum + q.backlog, 0)
})

const mqStatus = computed(() => {
  if (!metrics.value) return 'OK'
  const statuses = metrics.value.mqBacklogs.map((q) => q.status)
  if (statuses.includes('CRITICAL')) return 'CRITICAL'
  if (statuses.includes('WARNING')) return 'WARNING'
  return 'OK'
})

const sseDotColor = computed(() => {
  if (!metrics.value) return 'default'
  const s = metrics.value.sseConnections.status
  if (s === 'OK') return 'success'
  if (s === 'WARNING') return 'warning'
  return 'error'
})

const sseStatusLabel = computed(() => {
  if (!metrics.value) return '未知'
  const s = metrics.value.sseConnections.status
  if (s === 'OK') return '连接正常'
  if (s === 'WARNING') return '连接预警'
  return '连接异常'
})

const latencyHistory = computed(() => {
  return metrics.value?.apiP99Latency.history ?? []
})

/* 工具函数 */
function componentLabel(name: string) {
  return labelMap[name] || name
}

function detailLabel(key: string) {
  return detailLabelMap[key] || key
}

function formatDetailValue(key: string, value: unknown) {
  if (key.includes('queue') && typeof value === 'number') {
    return value
  }
  if (typeof value === 'number' && key === 'systemLoadAverage' && value < 0) {
    return '—'
  }
  return String(value)
}

function statusType(status: string) {
  if (status === 'DOWN') return 'error'
  if (status === 'DEGRADED') return 'warning'
  return 'success'
}

function statusClass(status: string) {
  return `status-${status.toLowerCase()}`
}

function tagColor(status: string) {
  if (status === 'UP' || status === 'OK') return 'success'
  if (status === 'DEGRADED' || status === 'WARNING') return 'warning'
  return 'error'
}

function metricCardClass(status: string) {
  if (status === 'CRITICAL') return 'card-critical'
  if (status === 'WARNING') return 'card-warning'
  return 'card-ok'
}

function metricTagColor(status: string) {
  if (status === 'OK') return 'success'
  if (status === 'WARNING') return 'warning'
  return 'error'
}

function formatTime(value: string) {
  return dayjs(value).format('YYYY-MM-DD HH:mm:ss')
}

function formatTimeShort(value: string) {
  return dayjs(value).format('HH:mm')
}

function formatBytes(bytes: number) {
  if (bytes >= 1_073_741_824) return (bytes / 1_073_741_824).toFixed(1) + ' GB'
  if (bytes >= 1_048_576) return (bytes / 1_048_576).toFixed(1) + ' MB'
  if (bytes >= 1_024) return (bytes / 1_024).toFixed(1) + ' KB'
  return bytes + ' B'
}

/** 趋势图 - 计算柱状图高度 (相对最高值) */
function trendBarHeight(value: number): number {
  const maxVal = Math.max(...latencyHistory.value.map((p) => p.value), 1)
  return (value / maxVal) * 100
}

function trendBarColor(value: number): string {
  if (value > 200) return 'bar-critical'
  if (value > 100) return 'bar-warning'
  return 'bar-ok'
}

/* 数据加载 */
async function load() {
  loading.value = true
  metricsError.value = ''
  timelineError.value = ''
  try {
    const healthRes = await getSystemHealth()
    health.value = healthRes.data
  } finally {
    loading.value = false
  }

  try {
    const metricsRes = await getDetailedMetrics()
    metrics.value = metricsRes.data
  } catch {
    metrics.value = null
    metricsError.value = '详细指标未接入后端或暂时不可用'
  }

  try {
    const timelineRes = await getHealthTimeline()
    timelineItems.value = timelineRes.data.items
  } catch {
    timelineItems.value = []
    timelineError.value = '健康时间线未接入后端或暂时不可用'
  }
}

/* 自动刷新 */
function startAutoRefresh() {
  stopAutoRefresh()
  refreshTimer = setInterval(load, 30_000)
}

function stopAutoRefresh() {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
}

onMounted(() => {
  load()
  startAutoRefresh()
})

onUnmounted(() => {
  stopAutoRefresh()
})
</script>

<style scoped lang="less">
.overall-alert,
.section-alert {
  margin-bottom: 4px;
}

.component-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 14px;
  margin-bottom: 24px;
}

.component-card {
  background: var(--fsd-bg-elevated);
  border: 1px solid var(--fsd-border);
  border-radius: var(--fsd-radius-lg);
  padding: 16px;
  box-shadow: var(--fsd-shadow-card);
}

.component-card.status-down {
  border-color: rgba(255, 61, 113, 0.45);
  background: linear-gradient(135deg, rgba(255, 61, 113, 0.06) 0%, var(--fsd-bg-elevated) 100%);
}

.component-card.status-degraded {
  border-color: rgba(255, 176, 32, 0.45);
  background: linear-gradient(135deg, rgba(255, 176, 32, 0.06) 0%, var(--fsd-bg-elevated) 100%);
}

.component-card.status-up {
  background: var(--fsd-gradient-card), var(--fsd-bg-elevated);
}

.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.card-head h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--fsd-text-primary);
}

.card-message {
  margin: 0;
  color: var(--fsd-text-secondary);
  font-size: 13px;
  line-height: 1.5;
}

.detail-list {
  list-style: none;
  padding: 0;
  margin: 12px 0 0;
  border-top: 1px solid var(--fsd-border);
  padding-top: 10px;
}

.detail-list li {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-size: 13px;
  padding: 4px 0;
  color: var(--fsd-text-tertiary);

  strong {
    color: var(--fsd-text-primary);
    font-weight: 500;
    text-align: right;
    word-break: break-all;
  }
}

/* ---- V5-S3: 扩展指标 ---- */
.section-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--fsd-text-primary);
  margin: 0 0 12px;
}

.metrics-section {
  margin-bottom: 24px;
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 14px;
}

.metric-card {
  background: var(--fsd-bg-elevated);
  border: 1px solid var(--fsd-border);
  border-radius: var(--fsd-radius-lg);
  padding: 16px;
  box-shadow: var(--fsd-shadow-card);
  transition: border-color 0.3s;
}

.metric-card-wide {
  grid-column: span 2;
}

.metric-card.card-ok {
  border-left: 4px solid var(--fsd-success);
}

.metric-card.card-warning {
  border-left: 4px solid var(--fsd-warning);
}

.metric-card.card-critical {
  border-left: 4px solid var(--fsd-error);
}

.metric-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.metric-icon {
  font-size: 18px;
}

.metric-label {
  font-size: 13px;
  color: var(--fsd-text-secondary);
  font-weight: 500;
}

.metric-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--fsd-text-primary);
  margin-bottom: 8px;
  font-family: 'JetBrains Mono', monospace;
}

.metric-detail {
  border-top: 1px solid var(--fsd-border);
  padding-top: 8px;
}

.metric-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  padding: 3px 0;
  color: var(--fsd-text-secondary);
}

.metric-row-label {
  color: var(--fsd-text-tertiary);
}

.metric-row strong {
  color: var(--fsd-text-primary);
  font-weight: 500;
}

/* ---- 趋势图 ---- */
.trend-chart {
  margin-top: 12px;
  border-top: 1px solid var(--fsd-border);
  padding-top: 10px;
}

.trend-chart-title {
  font-size: 12px;
  color: var(--fsd-text-tertiary);
  margin-bottom: 6px;
}

.trend-bars {
  display: flex;
  align-items: flex-end;
  gap: 2px;
  height: 60px;
}

.trend-bar-wrapper {
  flex: 1;
  display: flex;
  align-items: flex-end;
  height: 100%;
  cursor: pointer;
}

.trend-bar {
  width: 100%;
  min-height: 2px;
  border-radius: 2px 2px 0 0;
  transition: height 0.3s;
}

.trend-bar.bar-ok {
  background: var(--fsd-success);
}

.trend-bar.bar-warning {
  background: var(--fsd-warning);
}

.trend-bar.bar-critical {
  background: var(--fsd-error);
}

.trend-labels {
  display: flex;
  justify-content: space-between;
  font-size: 10px;
  color: var(--fsd-text-tertiary);
  margin-top: 4px;
}

/* ---- 时间线 ---- */
.timeline-section {
  margin-top: 8px;
}

.timeline-list {
  position: relative;
  padding-left: 20px;
}

.timeline-item {
  position: relative;
  display: flex;
  gap: 12px;
  padding-bottom: 16px;
}

.timeline-item::before {
  content: '';
  position: absolute;
  left: 5px;
  top: 12px;
  bottom: 0;
  width: 1px;
  background: var(--fsd-border);
}

.timeline-item:last-child::before {
  display: none;
}

.timeline-dot {
  position: relative;
  z-index: 1;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  flex-shrink: 0;
  margin-top: 3px;
}

.timeline-dot.dot-up,
.timeline-dot.dot-ok {
  background: var(--fsd-success);
}

.timeline-dot.dot-degraded,
.timeline-dot.dot-warning {
  background: var(--fsd-warning);
}

.timeline-dot.dot-down,
.timeline-dot.dot-critical {
  background: var(--fsd-error);
}

.timeline-content {
  flex: 1;
  min-width: 0;
}

.timeline-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 2px;
}

.timeline-component {
  font-size: 13px;
  font-weight: 500;
  color: var(--fsd-text-primary);
}

.timeline-time {
  font-size: 11px;
  color: var(--fsd-text-tertiary);
  margin-left: auto;
}

.timeline-message {
  margin: 2px 0 0;
  font-size: 12px;
  color: var(--fsd-text-secondary);
}

@media (max-width: 768px) {
  .component-grid {
    grid-template-columns: 1fr;
  }

  .metrics-grid {
    grid-template-columns: 1fr;
  }

  .metric-card-wide {
    grid-column: span 1;
  }
}
</style>