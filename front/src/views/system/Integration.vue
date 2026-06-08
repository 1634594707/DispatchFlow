<template>
  <PageContainer title="外部集成" subtitle="API 调用统计 · Webhook 管理 · 投递可观测 · 沙箱环境">
    <a-tabs v-model:activeKey="activeTab">
      <!-- V5-I1: API 调用统计看板 -->
      <a-tab-pane key="stats" tab="统计看板">
        <div class="tab-toolbar">
          <a-radio-group v-model:value="statsPeriod" button-style="solid" size="small" @change="loadStats">
            <a-radio-button value="7">近 7 天</a-radio-button>
            <a-radio-button value="30">近 30 天</a-radio-button>
          </a-radio-group>
          <a-button size="small" :loading="statsLoading" @click="loadStats">刷新</a-button>
        </div>

        <a-alert
          v-if="statsBackendMissing"
          type="warning"
          show-icon
          message="统计看板未接入后端"
          description="/api/admin/integration/api-stats 当前不可用，暂不展示模拟统计数据。"
          style="margin-bottom: 12px;"
        />
        <a-spin :spinning="statsLoading">
          <!-- 概览卡片 -->
          <div class="stats-cards">
            <div class="stats-card">
              <span class="stats-card-label">总调用量</span>
              <span class="stats-card-value">{{ statsSummary?.totalCalls ?? '-' }}</span>
              <span class="stats-card-desc">{{ statsPeriod }} 天</span>
            </div>
            <div class="stats-card">
              <span class="stats-card-label">成功率</span>
              <span class="stats-card-value" :class="statsSuccessClass">{{ statsSummary ? `${statsSummary.successRate.toFixed(1)}%` : '-' }}</span>
            </div>
            <div class="stats-card">
              <span class="stats-card-label">错误率</span>
              <span class="stats-card-value" :class="statsErrorClass">{{ statsSummary ? `${statsSummary.errorRate.toFixed(1)}%` : '-' }}</span>
            </div>
            <div class="stats-card">
              <span class="stats-card-label">P99 延迟</span>
              <span class="stats-card-value">{{ statsSummary ? `${statsSummary.p99LatencyMs}ms` : '-' }}</span>
            </div>
            <div class="stats-card">
              <span class="stats-card-label">P50 延迟</span>
              <span class="stats-card-value">{{ statsSummary ? `${statsSummary.p50LatencyMs}ms` : '-' }}</span>
            </div>
            <div class="stats-card">
              <span class="stats-card-label">活跃 Key</span>
              <span class="stats-card-value">{{ statsSummary?.uniqueKeys ?? '-' }}</span>
            </div>
          </div>

          <!-- 调用趋势图 -->
          <section class="panel">
            <h3>调用趋势</h3>
            <div class="trend-chart">
              <div
                v-for="point in statsTrend"
                :key="point.date"
                class="trend-bar-row"
              >
                <span class="trend-label">{{ point.date }}</span>
                <div class="trend-bar-track">
                  <div
                    class="trend-bar-success"
                    :style="{ width: `${trendSuccessWidth(point)}%` }"
                  />
                  <div
                    class="trend-bar-error"
                    :style="{ width: `${trendErrorWidth(point)}%` }"
                  />
                </div>
                <span class="trend-value">{{ point.totalCalls }}</span>
              </div>
            </div>
            <div class="trend-legend">
              <span class="legend-dot legend-success"></span> 成功
              <span class="legend-dot legend-error"></span> 失败
            </div>
          </section>

          <!-- Top 接口排行 -->
          <section class="panel">
            <h3>Top 接口排行</h3>
            <a-table
              size="small"
              row-key="path"
              :pagination="false"
              :data-source="statsTopEndpoints"
              :columns="topEndpointColumns"
              :loading="statsLoading"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'method'">
                  <a-tag :color="methodColor(record.method)">{{ record.method }}</a-tag>
                </template>
                <template v-else-if="column.key === 'errorRate'">
                  <span :class="record.errorRate > 5 ? 'text-danger' : ''">{{ record.errorRate.toFixed(1) }}%</span>
                </template>
                <template v-else-if="column.key === 'avgLatencyMs'">
                  <span :class="record.avgLatencyMs > 500 ? 'text-warning' : ''">{{ record.avgLatencyMs }}ms</span>
                </template>
              </template>
            </a-table>
          </section>
        </a-spin>
      </a-tab-pane>

      <a-tab-pane key="webhooks" tab="Webhook">
        <div class="tab-toolbar">
          <a-button type="primary" size="small" @click="openWebhook()">新增订阅</a-button>
          <a-button size="small" :loading="loading" @click="loadWebhooks">刷新</a-button>
        </div>
        <a-table row-key="id" size="small" :pagination="false" :data-source="webhooks" :columns="webhookColumns">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'enabled'">
              <a-tag :color="record.enabled ? 'green' : 'default'">{{ record.enabled ? '启用' : '停用' }}</a-tag>
            </template>
            <template v-else-if="column.key === 'actions'">
              <a-space>
                <a-button type="link" size="small" :loading="testingId === record.id" @click="handleTestWebhook(record)">测试</a-button>
                <a-button type="link" size="small" @click="openDeliveryLogs(record)">投递日志</a-button>
                <a-button type="link" size="small" @click="openWebhook(record)">编辑</a-button>
                <a-popconfirm title="确定删除？" @confirm="handleDeleteWebhook(record.id)">
                  <a-button type="link" size="small" danger>删除</a-button>
                </a-popconfirm>
              </a-space>
            </template>
          </template>
        </a-table>
      </a-tab-pane>

      <!-- V5-I2: Webhook 投递日志全量列表 -->
      <a-tab-pane key="deliveryLogs" tab="投递日志">
        <div class="tab-toolbar">
          <a-select
            v-model:value="deliveryFilter.subscriptionId"
            placeholder="全部订阅"
            allow-clear
            style="width: 160px"
            size="small"
            :options="webhookFilterOptions"
            @change="loadDeliveryLogs"
          />
          <a-select
            v-model:value="deliveryFilter.success"
            placeholder="全部状态"
            allow-clear
            style="width: 120px"
            size="small"
            @change="loadDeliveryLogs"
          >
            <a-select-option :value="true">成功</a-select-option>
            <a-select-option :value="false">失败</a-select-option>
          </a-select>
          <a-button size="small" :loading="logsLoading" @click="loadDeliveryLogs">刷新</a-button>
        </div>
        <a-table
          size="small"
          row-key="id"
          :pagination="{ pageSize: 20, showSizeChanger: true, showTotal: (t: number) => `共 ${t} 条` }"
          :data-source="deliveryLogs"
          :loading="logsLoading"
          :columns="deliveryLogColumns"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'success'">
              <a-tag :color="record.success ? 'green' : 'red'">{{ record.success ? '成功' : '失败' }}</a-tag>
            </template>
            <template v-else-if="column.key === 'httpStatus'">
              <a-tag :color="httpStatusColor(record.httpStatus)">{{ record.httpStatus ?? '-' }}</a-tag>
            </template>
            <template v-else-if="column.key === 'actions'">
              <a-button
                v-if="!record.success"
                type="link"
                size="small"
                @click="handleRetryDelivery(record.id)"
              >
                重试
              </a-button>
            </template>
          </template>
        </a-table>
      </a-tab-pane>

      <a-tab-pane key="apikeys" tab="API Key">
        <div class="tab-toolbar">
          <a-button type="primary" size="small" @click="handleCreateKey">生成 Key</a-button>
          <a-button size="small" :loading="loading" @click="loadApiKeys">刷新</a-button>
        </div>
        <a-alert
          type="info"
          show-icon
          message="开放接口前缀：/api/open/v1/，请求头携带 X-Api-Key；移动下单使用 X-Mobile-Api-Key"
          style="margin-bottom: 12px;"
        />
        <a-table row-key="id" size="small" :pagination="false" :data-source="apiKeys" :columns="keyColumns">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'apiKey'">
              <span class="mono">{{ record.apiKey }}</span>
            </template>
            <template v-else-if="column.key === 'actions'">
              <a-popconfirm
                v-if="record.status === 'ACTIVE'"
                title="确定禁用？"
                @confirm="handleDisableKey(record.id)"
              >
                <a-button type="link" size="small" danger>禁用</a-button>
              </a-popconfirm>
            </template>
          </template>
        </a-table>
      </a-tab-pane>

      <!-- V5-I3: API 沙箱环境 -->
      <a-tab-pane key="sandbox" tab="沙箱">
        <a-alert
          v-if="sandboxBackendMissing"
          type="warning"
          show-icon
          message="沙箱 Key 未接入后端"
          description="/api/admin/integration/sandbox/keys 当前不可用，已隐藏生成和禁用操作。"
          style="margin-bottom: 12px;"
        />
        <div class="tab-toolbar">
          <a-button v-if="!sandboxBackendMissing" type="primary" size="small" @click="handleCreateSandboxKey">生成沙箱 Key</a-button>
          <a-button size="small" :loading="sandboxLoading" @click="loadSandboxKeys">刷新</a-button>
        </div>
        <a-alert
          type="warning"
          show-icon
          message="沙箱 API 使用隔离的测试数据（测试站点、测试车辆），不会影响真实运营数据。沙箱接口前缀：/api/sandbox/v1/"
          style="margin-bottom: 12px;"
        />
        <a-table
          row-key="id"
          size="small"
          :pagination="false"
          :data-source="sandboxKeys"
          :loading="sandboxLoading"
          :columns="sandboxKeyColumns"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'apiKey'">
              <span class="mono">{{ record.apiKey }}</span>
            </template>
            <template v-else-if="column.key === 'actions'">
              <a-space>
                <a-button
                  type="link"
                  size="small"
                  @click="copySandboxKey(record)"
                >
                  {{ sandboxCopiedId === record.id ? '已复制' : '复制' }}
                </a-button>
                <a-popconfirm
                  v-if="record.status === 'ACTIVE'"
                  title="确定禁用？"
                  @confirm="handleDisableSandboxKey(record.id)"
                >
                  <a-button type="link" size="small" danger>禁用</a-button>
                </a-popconfirm>
              </a-space>
            </template>
          </template>
        </a-table>

        <div class="sandbox-info">
          <h4>沙箱快速接入</h4>
          <pre class="sandbox-code"><code>curl -H "X-Sandbox-Api-Key: YOUR_KEY" \
  https://your-domain/api/sandbox/v1/orders

# 测试站点列表（不影响生产数据）
GET /api/sandbox/v1/stations
# 创建测试订单
POST /api/sandbox/v1/orders
# 查询测试任务
GET /api/sandbox/v1/tasks</code></pre>
        </div>
      </a-tab-pane>
    </a-tabs>

    <a-modal v-model:open="webhookModal" title="Webhook 订阅" :confirm-loading="saving" @ok="handleSaveWebhook" width="600px">
      <a-form layout="vertical">
        <a-form-item label="名称" required><a-input v-model:value="webhookForm.name" /></a-form-item>
        <a-form-item label="渠道类型" required>
          <a-select v-model:value="webhookForm.channelType" style="width: 100%">
            <a-select-option value="GENERIC">通用 HTTP 回调</a-select-option>
            <a-select-option value="WECHAT_BOT">企业微信机器人</a-select-option>
            <a-select-option value="DINGTALK_BOT">钉钉机器人</a-select-option>
            <a-select-option value="FEISHU_BOT">飞书机器人</a-select-option>
          </a-select>
          <p v-if="webhookForm.channelType === 'GENERIC'" class="channel-hint">发送标准 JSON 事件体至回调地址</p>
          <p v-else-if="webhookForm.channelType === 'WECHAT_BOT'" class="channel-hint">填写企业微信群机器人 Webhook URL，消息将以 Markdown 格式推送</p>
          <p v-else-if="webhookForm.channelType === 'DINGTALK_BOT'" class="channel-hint">填写钉钉群机器人 Webhook URL，消息将以 Markdown 格式推送</p>
          <p v-else-if="webhookForm.channelType === 'FEISHU_BOT'" class="channel-hint">填写飞书群机器人 Webhook URL，消息将以卡片格式推送</p>
        </a-form-item>
        <a-form-item label="回调 URL" required><a-input v-model:value="webhookForm.callbackUrl" :placeholder="callbackUrlPlaceholder" /></a-form-item>
        <a-form-item label="事件类型" required>
          <a-select
            v-model:value="eventTypeList"
            mode="multiple"
            :options="DISPATCH_EVENT_OPTIONS"
            placeholder="选择订阅事件"
            style="width: 100%"
          />
        </a-form-item>
        <a-space style="margin-bottom: 12px;">
          <a-button size="small" @click="applyPreset('wms')">WMS 模板</a-button>
          <a-button size="small" @click="applyPreset('mes')">MES 模板</a-button>
        </a-space>
        <a-form-item label="签名密钥"><a-input v-model:value="webhookForm.secretToken" /></a-form-item>
        <a-form-item label="启用"><a-switch v-model:checked="webhookForm.enabled" /></a-form-item>
      </a-form>
    </a-modal>

    <a-drawer v-model:open="logDrawer" title="Webhook 投递日志" width="520">
      <a-table
        size="small"
        row-key="id"
        :pagination="false"
        :data-source="logDrawerData"
        :loading="logsLoading"
        :columns="logColumns"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'success'">
            {{ record.success ? '是' : '否' }}
          </template>
        </template>
      </a-table>
    </a-drawer>
  </PageContainer>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { message, Modal } from 'ant-design-vue'
import PageContainer from '@/components/common/PageContainer.vue'
import * as integrationApi from '@/api/integration'
import {
  DISPATCH_EVENT_OPTIONS,
  WEBHOOK_PRESETS,
  type WebhookDeliveryLog,
} from '@/api/integration'
import type {
  ApiCallStats,
  ApiCallTrendPoint,
  ApiTopEndpoint,
  ExternalApiKey,
  SandboxApiKey,
  WebhookSubscription,
  WebhookUpsertPayload,
} from '@/types/integration'

const activeTab = ref('stats')
const loading = ref(false)
const saving = ref(false)
const webhooks = ref<WebhookSubscription[]>([])
const apiKeys = ref<ExternalApiKey[]>([])
const webhookModal = ref(false)
const eventTypeList = ref<string[]>([])
const logDrawer = ref(false)
const logsLoading = ref(false)
const deliveryLogs = ref<WebhookDeliveryLog[]>([])
const logDrawerData = ref<WebhookDeliveryLog[]>([])
const testingId = ref<number | null>(null)

const webhookForm = reactive<WebhookUpsertPayload>({
  name: '',
  callbackUrl: '',
  channelType: 'GENERIC',
  eventTypes: '',
  secretToken: '',
  enabled: true,
})

// V5-I1: 统计看板
const statsPeriod = ref<7 | 30>(7)
const statsLoading = ref(false)
const statsBackendMissing = ref(false)
const statsSummary = ref<ApiCallStats | null>(null)
const statsTrend = ref<ApiCallTrendPoint[]>([])
const statsTopEndpoints = ref<ApiTopEndpoint[]>([])

// V5-I2: 投递日志筛选
const deliveryFilter = reactive({
  subscriptionId: undefined as number | undefined,
  success: undefined as boolean | undefined,
})

// V5-I3: 沙箱
const sandboxLoading = ref(false)
const sandboxBackendMissing = ref(false)
const sandboxKeys = ref<SandboxApiKey[]>([])
const sandboxCopiedId = ref<number | null>(null)

const channelTypeLabels: Record<string, string> = {
  GENERIC: '通用 HTTP',
  WECHAT_BOT: '企业微信',
  DINGTALK_BOT: '钉钉',
  FEISHU_BOT: '飞书',
}

const callbackUrlPlaceholder = computed(() => {
  switch (webhookForm.channelType) {
    case 'WECHAT_BOT': return 'https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=...'
    case 'DINGTALK_BOT': return 'https://oapi.dingtalk.com/robot/send?access_token=...'
    case 'FEISHU_BOT': return 'https://open.feishu.cn/open-apis/bot/v2/hook/...'
    default: return 'https://example.com/webhook'
  }
})

const webhookColumns = [
  { title: '名称', dataIndex: 'name' },
  { title: '渠道', dataIndex: 'channelType', width: 100, customRender: ({ text }: { text: string }) => channelTypeLabels[text] || text || '通用 HTTP' },
  { title: '回调地址', dataIndex: 'callbackUrl', ellipsis: true },
  { title: '事件', dataIndex: 'eventTypes', width: 200 },
  { title: '状态', key: 'enabled', width: 80 },
  { title: '失败次数', dataIndex: 'failureCount', width: 90 },
  { title: '操作', key: 'actions', width: 180 },
]

const keyColumns = [
  { title: '名称', dataIndex: 'keyName', width: 140 },
  { title: 'API Key', key: 'apiKey' },
  { title: '限流/分', dataIndex: 'rateLimitPerMinute', width: 90 },
  { title: '调用量', dataIndex: 'totalCalls', width: 90 },
  { title: '限流触发', dataIndex: 'rateLimitHits', width: 90 },
  { title: '状态', dataIndex: 'status', width: 80 },
  { title: '操作', key: 'actions', width: 80 },
]

const logColumns = [
  { title: '时间', dataIndex: 'deliveredAt', width: 150 },
  { title: '事件', dataIndex: 'eventType', width: 160 },
  { title: 'HTTP', dataIndex: 'httpStatus', width: 60 },
  { title: '成功', dataIndex: 'success', width: 60 },
  { title: '摘要', dataIndex: 'payloadSummary', ellipsis: true },
]

const deliveryLogColumns = [
  { title: '时间', dataIndex: 'deliveredAt', width: 150 },
  { title: '事件', dataIndex: 'eventType', width: 160 },
  { title: 'HTTP', key: 'httpStatus', width: 70 },
  { title: '状态', key: 'success', width: 70 },
  { title: '摘要', dataIndex: 'payloadSummary', ellipsis: true },
  { title: '错误', dataIndex: 'errorMessage', ellipsis: true, width: 160 },
  { title: '操作', key: 'actions', width: 70 },
]

const topEndpointColumns = [
  { title: '方法', key: 'method', width: 70 },
  { title: '路径', dataIndex: 'path', ellipsis: true },
  { title: '调用量', dataIndex: 'callCount', width: 90 },
  { title: '平均延迟', key: 'avgLatencyMs', width: 100 },
  { title: '错误率', key: 'errorRate', width: 80 },
]

const sandboxKeyColumns = [
  { title: '名称', dataIndex: 'keyName', width: 140 },
  { title: 'API Key', key: 'apiKey' },
  { title: '状态', dataIndex: 'status', width: 80 },
  { title: '调用量', dataIndex: 'totalCalls', width: 90 },
  { title: '创建时间', dataIndex: 'createdAt', width: 150 },
  { title: '操作', key: 'actions', width: 120 },
]

const webhookFilterOptions = computed(() =>
  webhooks.value.map((w) => ({ label: w.name, value: w.id })),
)

const statsSuccessClass = computed(() => {
  if (!statsSummary.value) return ''
  return statsSummary.value.successRate >= 99 ? 'text-success' : statsSummary.value.successRate >= 95 ? 'text-warning' : 'text-danger'
})

const statsErrorClass = computed(() => {
  if (!statsSummary.value) return ''
  return statsSummary.value.errorRate <= 1 ? 'text-success' : statsSummary.value.errorRate <= 5 ? 'text-warning' : 'text-danger'
})

const maxTrendCalls = computed(() =>
  Math.max(1, ...statsTrend.value.map((p) => p.totalCalls)),
)

function trendSuccessWidth(point: ApiCallTrendPoint) {
  return (point.successCount / maxTrendCalls.value) * 100
}

function trendErrorWidth(point: ApiCallTrendPoint) {
  return (point.errorCount / maxTrendCalls.value) * 100
}

function methodColor(method: string) {
  switch (method) {
    case 'GET': return 'blue'
    case 'POST': return 'green'
    case 'PUT': return 'orange'
    case 'DELETE': return 'red'
    default: return 'default'
  }
}

function httpStatusColor(status?: number) {
  if (!status) return 'default'
  if (status < 300) return 'green'
  if (status < 500) return 'orange'
  return 'red'
}

// V5-I1: 加载统计
async function loadStats() {
  statsLoading.value = true
  try {
    const res = await integrationApi.fetchApiCallStats(statsPeriod.value)
    const data = res.data
    statsSummary.value = data.summary ?? null
    statsTrend.value = data.trend ?? []
    statsTopEndpoints.value = data.topEndpoints ?? []
    statsBackendMissing.value = false
  } catch {
    statsSummary.value = null
    statsTrend.value = []
    statsTopEndpoints.value = []
    statsBackendMissing.value = true
  } finally {
    statsLoading.value = false
  }
}

// Webhook
async function loadWebhooks() {
  loading.value = true
  try {
    webhooks.value = (await integrationApi.fetchWebhooks()).data
  } finally {
    loading.value = false
  }
}

async function loadApiKeys() {
  loading.value = true
  try {
    apiKeys.value = (await integrationApi.fetchApiKeys()).data
  } finally {
    loading.value = false
  }
}

function openWebhook(record?: WebhookSubscription) {
  if (record) {
    webhookForm.id = record.id
    webhookForm.name = record.name
    webhookForm.callbackUrl = record.callbackUrl
    webhookForm.eventTypes = record.eventTypes
    eventTypeList.value = record.eventTypes.split(',').map((s) => s.trim()).filter(Boolean)
    webhookForm.enabled = record.enabled
  } else {
    webhookForm.id = undefined
    webhookForm.name = ''
    webhookForm.callbackUrl = ''
    webhookForm.eventTypes = 'dispatch.task.created'
    eventTypeList.value = ['dispatch.task.created']
    webhookForm.secretToken = ''
    webhookForm.enabled = true
  }
  webhookModal.value = true
}

function applyPreset(kind: 'wms' | 'mes') {
  eventTypeList.value = [...WEBHOOK_PRESETS[kind]]
}

async function openDeliveryLogs(record: WebhookSubscription) {
  logDrawer.value = true
  logsLoading.value = true
  try {
    logDrawerData.value = (await integrationApi.fetchWebhookDeliveries(record.id)).data
  } finally {
    logsLoading.value = false
  }
}

async function handleSaveWebhook() {
  webhookForm.eventTypes = eventTypeList.value.join(',')
  saving.value = true
  try {
    await integrationApi.saveWebhook(webhookForm)
    message.success('Webhook 已保存')
    webhookModal.value = false
    await loadWebhooks()
  } finally {
    saving.value = false
  }
}

async function handleDeleteWebhook(id: number) {
  await integrationApi.deleteWebhook(id)
  message.success('已删除')
  await loadWebhooks()
}

async function handleTestWebhook(record: WebhookSubscription) {
  testingId.value = record.id
  try {
    await integrationApi.testWebhook(record.id)
    message.success(`测试成功：${record.name} 连通性正常`)
  } catch (e: any) {
    message.error(`测试失败：${e?.response?.data?.message || e?.message || '连接异常'}`)
  } finally {
    testingId.value = null
  }
}

function handleCreateKey() {
  Modal.confirm({
    title: '生成 API Key',
    content: '新 Key 仅展示一次，请妥善保存。',
    async onOk() {
      const res = await integrationApi.createApiKey('external-client', 120)
      Modal.info({
        title: 'API Key 已生成',
        content: res.data.apiKey,
      })
      await loadApiKeys()
    },
  })
}

async function handleDisableKey(id: number) {
  await integrationApi.disableApiKey(id)
  message.success('已禁用')
  await loadApiKeys()
}

// V5-I2: 投递日志全量查询
async function loadDeliveryLogs() {
  logsLoading.value = true
  try {
    deliveryLogs.value = (
      await integrationApi.fetchAllDeliveryLogs({
        subscriptionId: deliveryFilter.subscriptionId,
        success: deliveryFilter.success,
        limit: 200,
      })
    ).data
  } catch {
    deliveryLogs.value = []
  } finally {
    logsLoading.value = false
  }
}

async function handleRetryDelivery(deliveryId: number) {
  try {
    await integrationApi.retryWebhookDelivery(deliveryId)
    message.success('已触发重试')
    await loadDeliveryLogs()
  } catch (e: any) {
    message.error(`重试失败：${e?.response?.data?.message || e?.message || '请求异常'}`)
  }
}

// V5-I3: 沙箱
async function loadSandboxKeys() {
  sandboxLoading.value = true
  try {
    sandboxKeys.value = (await integrationApi.fetchSandboxKeys()).data
    sandboxBackendMissing.value = false
  } catch {
    sandboxKeys.value = []
    sandboxBackendMissing.value = true
  } finally {
    sandboxLoading.value = false
  }
}

function handleCreateSandboxKey() {
  Modal.confirm({
    title: '生成沙箱 API Key',
    content: '沙箱 Key 仅能访问隔离的测试数据，不影响生产环境。Key 仅展示一次。',
    async onOk() {
      const res = await integrationApi.createSandboxKey('sandbox-client')
      Modal.info({
        title: '沙箱 API Key 已生成',
        content: res.data.apiKey,
      })
      await loadSandboxKeys()
    },
  })
}

async function handleDisableSandboxKey(id: number) {
  await integrationApi.disableSandboxKey(id)
  message.success('已禁用')
  await loadSandboxKeys()
}

async function copySandboxKey(record: SandboxApiKey) {
  try {
    await navigator.clipboard.writeText(record.apiKey)
    sandboxCopiedId.value = record.id
    setTimeout(() => { sandboxCopiedId.value = null }, 2000)
    message.success('已复制到剪贴板')
  } catch {
    message.warning('复制失败，请手动复制')
  }
}

onMounted(() => {
  loadStats()
  loadWebhooks()
  loadApiKeys()
  loadSandboxKeys()
})
</script>

<style scoped lang="less">
.tab-toolbar {
  margin-bottom: 12px;
  display: flex;
  gap: 8px;
  align-items: center;
}
.mono {
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
}

// V5-I1: 统计看板
.stats-cards {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 12px;
  margin-bottom: 20px;
}

.stats-card {
  background: var(--fsd-bg-secondary, rgba(255,255,255,0.04));
  border: 1px solid var(--fsd-border, rgba(255,255,255,0.08));
  border-radius: 8px;
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.stats-card-label {
  font-size: 12px;
  color: var(--fsd-text-tertiary, #8c8c8c);
}

.stats-card-value {
  font-size: 24px;
  font-weight: 600;
  font-family: 'JetBrains Mono', monospace;
  color: var(--fsd-text-primary, #e8e8e8);
}

.stats-card-desc {
  font-size: 11px;
  color: var(--fsd-text-tertiary, #8c8c8c);
}

.text-success { color: #52c41a; }
.text-warning { color: #faad14; }
.text-danger { color: #ff4d4f; }

// 趋势图
.panel {
  background: var(--fsd-bg-secondary, rgba(255,255,255,0.04));
  border: 1px solid var(--fsd-border, rgba(255,255,255,0.08));
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 16px;

  h3 {
    margin: 0 0 12px;
    font-size: 14px;
    font-weight: 600;
  }
}

.trend-chart {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.trend-bar-row {
  display: grid;
  grid-template-columns: 44px 1fr 52px;
  gap: 10px;
  align-items: center;
}

.trend-label {
  font-size: 11px;
  color: var(--fsd-text-tertiary, #8c8c8c);
  font-family: 'JetBrains Mono', monospace;
}

.trend-bar-track {
  height: 14px;
  background: rgba(255, 255, 255, 0.06);
  border-radius: 999px;
  overflow: hidden;
  display: flex;
}

.trend-bar-success {
  height: 100%;
  background: #52c41a;
  transition: width 0.3s ease;
}

.trend-bar-error {
  height: 100%;
  background: #ff4d4f;
  transition: width 0.3s ease;
}

.trend-value {
  font-size: 11px;
  color: var(--fsd-text-secondary, #b0b0b0);
  text-align: right;
  font-family: 'JetBrains Mono', monospace;
}

.trend-legend {
  display: flex;
  gap: 16px;
  margin-top: 8px;
  font-size: 12px;
  color: var(--fsd-text-tertiary, #8c8c8c);
}

.legend-dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  margin-right: 4px;
  vertical-align: middle;
}

.legend-success { background: #52c41a; }
.legend-error { background: #ff4d4f; }

// 沙箱
.sandbox-info {
  margin-top: 20px;
  background: var(--fsd-bg-secondary, rgba(255,255,255,0.04));
  border: 1px solid var(--fsd-border, rgba(255,255,255,0.08));
  border-radius: 8px;
  padding: 16px;

  h4 {
    margin: 0 0 8px;
    font-size: 13px;
  }
}

.sandbox-code {
  background: rgba(0,0,0,0.3);
  border-radius: 6px;
  padding: 12px;
  margin: 0;
  overflow-x: auto;

  code {
    font-size: 12px;
    font-family: 'JetBrains Mono', monospace;
    color: #a8d8a8;
    white-space: pre;
  }
}

// V5-M3: 移动端适配
@media (max-width: 768px) {
  .stats-cards {
    grid-template-columns: repeat(2, 1fr);
  }
  .tab-toolbar {
    flex-wrap: wrap;
  }
}
</style>