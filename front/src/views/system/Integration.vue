<template>
  <PageContainer title="外部集成" subtitle="Webhook 回调 · Open API Key · 投递可观测">
    <a-tabs v-model:activeKey="activeTab">
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
    </a-tabs>

    <a-modal v-model:open="webhookModal" title="Webhook 订阅" :confirm-loading="saving" @ok="handleSaveWebhook">
      <a-form layout="vertical">
        <a-form-item label="名称" required><a-input v-model:value="webhookForm.name" /></a-form-item>
        <a-form-item label="回调 URL" required><a-input v-model:value="webhookForm.callbackUrl" /></a-form-item>
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
        :data-source="deliveryLogs"
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
import { ref, reactive, onMounted } from 'vue'
import { message, Modal } from 'ant-design-vue'
import PageContainer from '@/components/common/PageContainer.vue'
import * as integrationApi from '@/api/integration'
import {
  DISPATCH_EVENT_OPTIONS,
  WEBHOOK_PRESETS,
  type WebhookDeliveryLog,
} from '@/api/integration'
import type { ExternalApiKey, WebhookSubscription, WebhookUpsertPayload } from '@/types/integration'

const activeTab = ref('webhooks')
const loading = ref(false)
const saving = ref(false)
const webhooks = ref<WebhookSubscription[]>([])
const apiKeys = ref<ExternalApiKey[]>([])
const webhookModal = ref(false)
const eventTypeList = ref<string[]>([])
const logDrawer = ref(false)
const logsLoading = ref(false)
const deliveryLogs = ref<WebhookDeliveryLog[]>([])

const webhookForm = reactive<WebhookUpsertPayload>({
  name: '',
  callbackUrl: '',
  eventTypes: '',
  secretToken: '',
  enabled: true,
})

const webhookColumns = [
  { title: '名称', dataIndex: 'name' },
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
    deliveryLogs.value = (await integrationApi.fetchWebhookDeliveries(record.id)).data
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

onMounted(() => {
  loadWebhooks()
  loadApiKeys()
})
</script>

<style scoped lang="less">
.tab-toolbar {
  margin-bottom: 12px;
  display: flex;
  gap: 8px;
}
.mono {
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
}
</style>
