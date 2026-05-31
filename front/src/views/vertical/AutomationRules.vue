<template>
  <PageContainer title="自动化规则" subtitle="IF-THEN 规则引擎（非大模型）">
    <template #actions>
      <a-button type="primary" @click="openCreate">新建规则</a-button>
    </template>
    <a-table :columns="columns" :data-source="rules" row-key="id" :loading="loading" :pagination="false">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'condition'">{{ record.conditionType }} {{ record.conditionValue }}</template>
        <template v-else-if="column.key === 'enabled'">
          <a-switch :checked="record.enabled" size="small" @change="(v: boolean) => toggleRule(record.id, v)" />
        </template>
        <template v-else-if="column.key === 'actions'">
          <a-button type="link" size="small" @click="openEdit(record)">编辑</a-button>
          <a-button type="link" size="small" @click="openAudit(record)">审计</a-button>
          <a-popconfirm title="确认删除？" @confirm="remove(record.id)"><a-button type="link" size="small" danger>删除</a-button></a-popconfirm>
        </template>
      </template>
    </a-table>

    <a-modal v-model:open="modalOpen" :title="editing ? '编辑规则' : '新建规则'" @ok="save">
      <a-form layout="vertical">
        <a-form-item label="名称"><a-input v-model:value="form.ruleName" /></a-form-item>
        <a-row :gutter="12">
          <a-col :span="12"><a-form-item label="条件"><a-select v-model:value="form.conditionType" :options="conditionOptions" /></a-form-item></a-col>
          <a-col :span="12"><a-form-item label="阈值"><a-input v-model:value="form.conditionValue" /></a-form-item></a-col>
        </a-row>
        <a-form-item label="动作"><a-select v-model:value="form.actionType" :options="actionOptions" /></a-form-item>
        <a-form-item label="动作参数 JSON">
          <a-textarea v-model:value="form.actionParamsJson" placeholder='{"weightDistanceFactor":0.85}' :rows="3" />
        </a-form-item>
        <a-form-item label="启用"><a-switch v-model:checked="form.enabled" /></a-form-item>
      </a-form>
    </a-modal>

    <a-drawer v-model:open="auditOpen" title="规则审计日志" width="480">
      <a-timeline>
        <a-timeline-item v-for="item in auditLogs" :key="item.id">
          <strong>{{ item.action }}</strong> · {{ item.operator || 'system' }}
          <div>{{ item.detail }}</div>
          <div class="audit-time">{{ item.createdAt }}</div>
        </a-timeline-item>
      </a-timeline>
    </a-drawer>
  </PageContainer>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import PageContainer from '@/components/common/PageContainer.vue'
import { useParkOptions } from '@/composables/useParkOptions'
import * as verticalApi from '@/api/vertical'
import type { AutomationRule, AutomationRuleAudit } from '@/api/vertical'

const { parkOptions } = useParkOptions()
const loading = ref(false)
const rules = ref<AutomationRule[]>([])
const modalOpen = ref(false)
const auditOpen = ref(false)
const auditLogs = ref<AutomationRuleAudit[]>([])
const editing = ref<AutomationRule | null>(null)
const form = reactive({
  parkId: 1,
  ruleName: '',
  conditionType: 'SOC_BELOW',
  conditionValue: '20',
  actionType: 'CREATE_CHARGE_TASK',
  actionParamsJson: '',
  enabled: true,
})

const columns = [
  { title: '名称', dataIndex: 'ruleName', key: 'ruleName' },
  { title: '条件', key: 'condition' },
  { title: '动作', dataIndex: 'actionType', key: 'actionType' },
  { title: '启停', key: 'enabled', width: 80 },
  { title: '操作', key: 'actions', width: 180 },
]

const conditionOptions = [
  { label: 'SOC 低于', value: 'SOC_BELOW' },
  { label: '高峰模式', value: 'PEAK_MODE' },
]
const actionOptions = [
  { label: '创建回充任务', value: 'CREATE_CHARGE_TASK' },
  { label: '加强派车', value: 'BOOST_DISPATCH' },
]

async function loadData() {
  loading.value = true
  try {
    rules.value = (await verticalApi.fetchAutomationRules(form.parkId)).data
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editing.value = null
  form.parkId = parkOptions.value[0]?.value ?? 1
  form.ruleName = ''
  form.conditionType = 'SOC_BELOW'
  form.conditionValue = '20'
  form.actionType = 'CREATE_CHARGE_TASK'
  form.actionParamsJson = ''
  form.enabled = true
  modalOpen.value = true
}

function openEdit(record: AutomationRule) {
  editing.value = record
  Object.assign(form, record)
  modalOpen.value = true
}

async function openAudit(record: AutomationRule) {
  auditLogs.value = (await verticalApi.fetchAutomationRuleAudit(record.id)).data
  auditOpen.value = true
}

async function toggleRule(ruleId: number, _enabled: boolean) {
  await verticalApi.toggleAutomationRule(ruleId)
  message.success('已切换')
  await loadData()
}

async function save() {
  if (editing.value) await verticalApi.updateAutomationRule(editing.value.id, form)
  else await verticalApi.createAutomationRule(form)
  message.success('已保存')
  modalOpen.value = false
  await loadData()
}

async function remove(id: number) {
  await verticalApi.deleteAutomationRule(id)
  message.success('已删除')
  await loadData()
}

onMounted(loadData)
</script>

<style scoped>
.audit-time {
  color: rgba(0, 0, 0, 0.45);
  font-size: 12px;
}
</style>
