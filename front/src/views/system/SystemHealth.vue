<template>
  <PageContainer title="系统健康监控" subtitle="MySQL · Redis · RabbitMQ · Outbox · API 延迟 · JVM">
    <template #actions>
      <a-button :loading="loading" @click="load">
        <ReloadOutlined /> 刷新
      </a-button>
    </template>

    <a-spin :spinning="loading">
      <a-alert
        v-if="health"
        :type="statusType(health.overallStatus)"
        show-icon
        :message="`整体状态：${health.overallStatus}`"
        :description="`检测时间 ${formatTime(health.checkedAt)}`"
        class="overall-alert"
      />
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
    </a-spin>
  </PageContainer>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ReloadOutlined } from '@ant-design/icons-vue'
import dayjs from 'dayjs'
import PageContainer from '@/components/common/PageContainer.vue'
import { getSystemHealth } from '@/api/systemHealth'
import type { SystemHealthResponse } from '@/types/phase10'

const loading = ref(false)
const health = ref<SystemHealthResponse | null>(null)

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
  if (status === 'UP') return 'success'
  if (status === 'DEGRADED') return 'warning'
  return 'error'
}

function formatTime(value: string) {
  return dayjs(value).format('YYYY-MM-DD HH:mm:ss')
}

async function load() {
  loading.value = true
  try {
    const res = await getSystemHealth()
    health.value = res.data
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped lang="less">
.overall-alert {
  margin-bottom: 4px;
}

.component-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 14px;
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
</style>
