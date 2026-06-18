<template>
  <PageContainer title="告警设置" subtitle="声音告警 · 浏览器通知 · 短信通知 · 告警升级 · 静默规则">
    <a-form layout="vertical" class="settings-form">
      <!-- 原有：声音告警 -->
      <a-divider>声音告警</a-divider>
      <a-form-item label="声音告警">
        <a-switch v-model:checked="form.soundEnabled" />
      </a-form-item>
      <a-form-item label="音量">
        <a-slider v-model:value="form.soundVolume" :min="0" :max="1" :step="0.1" :disabled="!form.soundEnabled" />
      </a-form-item>
      <a-form-item label="严重异常特殊告警音">
        <a-switch v-model:checked="form.criticalSoundEnabled" :disabled="!form.soundEnabled" />
      </a-form-item>
      <a-form-item label="浏览器通知">
        <a-switch v-model:checked="form.browserNotifyEnabled" />
        <a-button size="small" style="margin-left: 12px" @click="requestPermission">申请通知权限</a-button>
      </a-form-item>

      <!-- V5-N2: 短信通知 -->
      <a-divider>短信通知</a-divider>
      <a-empty v-if="!smsBackendReady" description="短信通知未接入后端" />
      <template v-else>
        <a-form-item label="启用短信通知">
          <a-switch v-model:checked="smsForm.enabled" />
        </a-form-item>
      <a-form-item label="接收手机号">
        <a-select
          v-model:value="smsForm.phoneNumbers"
          mode="tags"
          placeholder="输入手机号后回车添加"
          :open="false"
          style="width: 100%"
        />
        <div v-if="smsForm.phoneNumbers.length > 0" class="phone-validation">
          <a-tag v-for="(phone, idx) in smsForm.phoneNumbers" :key="idx" :color="validatePhone(phone) ? 'success' : 'error'">
            {{ phone }}
            <template #close-icon>
              <CloseOutlined @click="removePhone(idx)" />
            </template>
          </a-tag>
          <span v-if="smsForm.phoneNumbers.some(p => !validatePhone(p))" class="validation-hint">部分手机号格式不正确（需11位数字）</span>
        </div>
      </a-form-item>
      <a-form-item label="严重级别阈值">
        <a-select v-model:value="smsForm.severityThreshold">
          <a-select-option value="CRITICAL">仅 CRITICAL</a-select-option>
          <a-select-option value="HIGH+">HIGH 及以上</a-select-option>
          <a-select-option value="ALL">全部</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="事件类型">
        <a-select v-model:value="smsForm.eventTypes" mode="multiple" placeholder="选择事件类型">
          <a-select-option value="VEHICLE_OFFLINE">车辆失联</a-select-option>
          <a-select-option value="CHARGING_PILE_FAULT">充电桩故障</a-select-option>
          <a-select-option value="CRITICAL_SOC">危急 SOC</a-select-option>
          <a-select-option value="TASK_EXECUTE_FAILED">任务执行失败</a-select-option>
          <a-select-option value="EXECUTE_TIMEOUT">执行超时</a-select-option>
        </a-select>
      </a-form-item>
      <a-space>
        <a-button type="primary" :disabled="!smsForm.enabled" @click="saveSmsConfig">保存短信配置</a-button>
        <a-button :disabled="!smsForm.enabled || smsForm.phoneNumbers.length === 0" @click="testSms">测试短信</a-button>
      </a-space>

      <!-- SMS 历史 -->
      <a-collapse ghost style="margin-top: 12px">
        <a-collapse-panel key="sms-history" header="短信发送历史">
          <a-timeline v-if="smsHistory.length > 0">
            <a-timeline-item v-for="item in smsHistory" :key="item.id" :color="item.status === 'success' ? 'green' : 'red'">
              <template #dot>
                <CheckCircleOutlined v-if="item.status === 'success'" style="color: #2DE08A" />
                <CloseCircleOutlined v-else style="color: #FF5C7C" />
              </template>
              <div class="sms-history-item">
                <span class="sms-history-phone">{{ item.phone }}</span>
                <span class="sms-history-msg">{{ item.message }}</span>
                <span class="sms-history-time">{{ formatTime(item.sentAt) }}</span>
              </div>
            </a-timeline-item>
          </a-timeline>
          <div v-else class="empty-hint">暂无短信发送记录</div>
        </a-collapse-panel>
      </a-collapse>
      </template>

      <!-- 原有：级别配置 & 静默时段 -->
      <a-divider>按级别配置</a-divider>
      <a-form-item label="按级别配置">
        <div class="level-rules">
          <div v-for="level in levels" :key="level" class="level-row">
            <span>{{ level }}</span>
            <a-checkbox v-model:checked="form.levelRules[level].sound">声音</a-checkbox>
            <a-checkbox v-model:checked="form.levelRules[level].notify">通知</a-checkbox>
          </div>
        </div>
      </a-form-item>

      <!-- V5-N7: 静默规则（替换原有简单静默时段） -->
      <a-divider>静默规则</a-divider>
      <a-empty v-if="!silenceBackendReady" description="静默规则未接入后端" />
      <template v-else>
      <a-form-item label="全局静默时段（向后兼容）">
        <a-space>
          <a-time-picker v-model:value="legacySilentStart" format="HH:mm" minute-step="30" placeholder="开始时间" />
          <span>至</span>
          <a-time-picker v-model:value="legacySilentEnd" format="HH:mm" minute-step="30" placeholder="结束时间" />
        </a-space>
      </a-form-item>

      <div class="section-toolbar">
        <span class="section-title">精细静默规则</span>
        <a-button size="small" type="primary" @click="openSilenceRuleModal">添加规则</a-button>
      </div>
      <a-table
        :data-source="silenceRules"
        :columns="silenceRuleColumns"
        row-key="id"
        size="small"
        :pagination="false"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'enabled'">
            <a-switch :checked="record.enabled" size="small" @change="(v: boolean) => toggleSilenceRule(record.id, v)" />
          </template>
          <template v-else-if="column.key === 'scope'">
            <span>{{ record.vehicleCode || record.vehicleId || '所有车辆' }}</span>
            <span v-if="record.exceptionType"> · {{ record.exceptionType }}</span>
          </template>
          <template v-else-if="column.key === 'schedule'">
            <span>{{ record.startTime || '--' }} - {{ record.endTime || '--' }}</span>
            <span v-if="record.weekDays.length" style="margin-left: 6px">
              {{ record.weekDays.map((d: number) => weekDayLabel(d)).join(' ') }}
            </span>
          </template>
          <template v-else-if="column.key === 'note'">
            <span class="text-muted">{{ record.note || '-' }}</span>
          </template>
          <template v-else-if="column.key === 'actions'">
            <a-space>
              <a-button size="small" type="link" @click="editSilenceRule(record)">编辑</a-button>
              <a-button size="small" type="link" danger @click="removeSilenceRule(record.id)">删除</a-button>
            </a-space>
          </template>
        </template>
      </a-table>
      </template>

      <!-- V5-N6: 告警升级 -->
      <a-divider>告警升级</a-divider>
      <a-empty v-if="!escalationBackendReady" description="告警升级未接入后端" />
      <template v-else>
      <div class="section-toolbar">
        <span class="section-title">升级规则</span>
        <a-button size="small" type="primary" @click="openEscalationRuleModal">添加规则</a-button>
      </div>
      <a-table
        :data-source="escalationRules"
        :columns="escalationRuleColumns"
        row-key="id"
        size="small"
        :pagination="false"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'actions'">
            <a-space>
              <a-button size="small" type="link" @click="editEscalationRule(record)">编辑</a-button>
              <a-button size="small" type="link" danger @click="removeEscalationRule(record.id)">删除</a-button>
            </a-space>
          </template>
        </template>
      </a-table>

      <!-- 升级历史 -->
      <a-collapse ghost style="margin-top: 12px">
        <a-collapse-panel key="escalation-history" header="升级记录">
          <a-timeline v-if="escalationHistory.length > 0">
            <a-timeline-item v-for="item in escalationHistory" :key="item.id" color="orange">
              <div class="esc-history-item">
                <span>{{ item.fromSeverity }} → {{ item.escalatedTo }}</span>
                <span class="text-muted">{{ formatTime(item.escalatedAt) }}</span>
                <a-tag :color="item.status === 'pending' ? 'warning' : item.status === 'acknowledged' ? 'processing' : 'success'">
                  {{ item.status === 'pending' ? '待确认' : item.status === 'acknowledged' ? '已确认' : '已解决' }}
                </a-tag>
              </div>
            </a-timeline-item>
          </a-timeline>
          <div v-else class="empty-hint">暂无升级记录</div>
        </a-collapse-panel>
      </a-collapse>
      </template>

      <!-- 底部操作 -->
      <a-divider />
      <a-space>
        <a-button type="primary" @click="save">保存</a-button>
        <a-button @click="reset">恢复默认</a-button>
        <a-button @click="testSound">测试提示音</a-button>
      </a-space>
    </a-form>

    <!-- 静默规则 Modal -->
    <a-modal
      v-model:open="silenceRuleModalVisible"
      :title="editingSilenceRule ? '编辑静默规则' : '添加静默规则'"
      ok-text="保存"
      @ok="saveSilenceRule"
    >
      <a-form layout="vertical">
        <a-form-item label="车辆">
          <a-select v-model:value="silenceRuleForm.vehicleId" allow-clear placeholder="选择车辆（留空表示所有车辆）">
            <a-select-option v-for="v in vehicleOptions" :key="v.vehicleId" :value="v.vehicleId">
              {{ v.vehicleCode }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="异常类型">
          <a-select v-model:value="silenceRuleForm.exceptionType" allow-clear placeholder="选择异常类型（留空表示所有类型）">
            <a-select-option value="VEHICLE_OFFLINE">车辆失联</a-select-option>
            <a-select-option value="CHARGING_PILE_FAULT">充电桩故障</a-select-option>
            <a-select-option value="CRITICAL_SOC">危急 SOC</a-select-option>
            <a-select-option value="TASK_EXECUTE_FAILED">任务执行失败</a-select-option>
            <a-select-option value="EXECUTE_TIMEOUT">执行超时</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="时间范围">
          <a-time-picker v-model:value="silenceRuleForm.startTime" format="HH:mm" minute-step="30" placeholder="开始" style="width: 120px" />
          <span style="margin: 0 8px">至</span>
          <a-time-picker v-model:value="silenceRuleForm.endTime" format="HH:mm" minute-step="30" placeholder="结束" style="width: 120px" />
        </a-form-item>
        <a-form-item label="重复星期">
          <a-checkbox-group v-model:value="silenceRuleForm.weekDays">
            <a-checkbox :value="1">周一</a-checkbox>
            <a-checkbox :value="2">周二</a-checkbox>
            <a-checkbox :value="3">周三</a-checkbox>
            <a-checkbox :value="4">周四</a-checkbox>
            <a-checkbox :value="5">周五</a-checkbox>
            <a-checkbox :value="6">周六</a-checkbox>
            <a-checkbox :value="0">周日</a-checkbox>
          </a-checkbox-group>
        </a-form-item>
        <a-form-item label="备注">
          <a-input v-model:value="silenceRuleForm.note" placeholder="选填" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 升级规则 Modal -->
    <a-modal
      v-model:open="escalationRuleModalVisible"
      :title="editingEscalationRule ? '编辑升级规则' : '添加升级规则'"
      ok-text="保存"
      @ok="saveEscalationRule"
    >
      <a-form layout="vertical">
        <a-form-item label="严重级别" required>
          <a-select v-model:value="escalationRuleForm.severity">
            <a-select-option value="CRITICAL">CRITICAL</a-select-option>
            <a-select-option value="HIGH">HIGH</a-select-option>
            <a-select-option value="MEDIUM">MEDIUM</a-select-option>
            <a-select-option value="LOW">LOW</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="超时时间（分钟）" required>
          <a-input-number v-model:value="escalationRuleForm.timeoutMinutes" :min="1" :max="1440" />
        </a-form-item>
        <a-form-item label="升级角色" required>
          <a-select v-model:value="escalationRuleForm.escalateToRole">
            <a-select-option value="ADMIN">管理员</a-select-option>
            <a-select-option value="OPERATOR">运营人员</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="通知方式" required>
          <a-select v-model:value="escalationRuleForm.notifyMethod">
            <a-select-option value="sound">声音</a-select-option>
            <a-select-option value="browser">浏览器通知</a-select-option>
            <a-select-option value="sms">短信</a-select-option>
          </a-select>
        </a-form-item>
      </a-form>
    </a-modal>
  </PageContainer>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { CheckCircleOutlined, CloseCircleOutlined, CloseOutlined } from '@ant-design/icons-vue'
import dayjs from 'dayjs'
import PageContainer from '@/components/common/PageContainer.vue'
import { useAlertStore } from '@/stores/alert'
import { playAlertTone, requestBrowserNotifyPermission } from '@/utils/alertSound'
import * as alertSettingsApi from '@/api/alertSettings'
import type { EscalationRule, SilenceRule, SmsNotificationConfig, SmsNotificationHistoryItem } from '@/types/alert'
import { DEFAULT_ESCALATION_RULES } from '@/types/alert'

const alertStore = useAlertStore()
const levels = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW']
const form = reactive(JSON.parse(JSON.stringify(alertStore.rules)))

// ── SMS ──
const smsForm = reactive<SmsNotificationConfig>({
  enabled: false,
  phoneNumbers: [],
  severityThreshold: 'CRITICAL',
  eventTypes: ['VEHICLE_OFFLINE', 'CHARGING_PILE_FAULT', 'CRITICAL_SOC'],
})
const smsHistory = ref<SmsNotificationHistoryItem[]>([])
const smsBackendReady = ref(true)
const silenceBackendReady = ref(true)
const escalationBackendReady = ref(true)

function validatePhone(phone: string): boolean {
  return /^1[3-9]\d{9}$/.test(phone)
}

function removePhone(index: number) {
  smsForm.phoneNumbers.splice(index, 1)
}

async function saveSmsConfig() {
  const invalid = smsForm.phoneNumbers.some(p => !validatePhone(p))
  if (invalid) {
    message.warning('存在无效的手机号码')
    return
  }
  await alertSettingsApi.saveSmsConfig({ ...smsForm })
  message.success('短信配置已保存')
}

async function testSms() {
  if (smsForm.phoneNumbers.length === 0) {
    message.warning('请先添加手机号')
    return
  }
  const ok = await alertSettingsApi.testSmsNotification(smsForm.phoneNumbers[0])
  if (ok) {
    message.success('测试短信已发送')
    await loadSmsHistory()
  }
}

async function loadSmsConfig() {
  try {
    const res = await alertSettingsApi.fetchSmsConfig()
    Object.assign(smsForm, res.data)
    smsBackendReady.value = true
  } catch {
    smsBackendReady.value = false
  }
}

async function loadSmsHistory() {
  if (!smsBackendReady.value) return
  try {
    const res = await alertSettingsApi.fetchSmsHistory()
    smsHistory.value = res.data
  } catch {
    smsBackendReady.value = false
    smsHistory.value = []
  }
}

// ── 静默规则 ──
const silenceRuleColumns = [
  { title: '启用', key: 'enabled', width: 60 },
  { title: '范围', key: 'scope' },
  { title: '时间', key: 'schedule' },
  { title: '备注', key: 'note' },
  { title: '操作', key: 'actions', width: 120 },
]
const silenceRules = ref<SilenceRule[]>([])
const silenceRuleModalVisible = ref(false)
const editingSilenceRule = ref<SilenceRule | null>(null)
const silenceRuleForm = reactive<{
  vehicleId?: number
  exceptionType?: string
  startTime?: any
  endTime?: any
  weekDays: number[]
  note: string
}>({
  vehicleId: undefined,
  exceptionType: undefined,
  startTime: undefined,
  endTime: undefined,
  weekDays: [],
  note: '',
})
const vehicleOptions = ref<{ vehicleId: number; vehicleCode: string }[]>([])

const legacySilentStart = ref<any>(null)
const legacySilentEnd = ref<any>(null)

function weekDayLabel(d: number) {
  const map: Record<number, string> = { 0: '日', 1: '一', 2: '二', 3: '三', 4: '四', 5: '五', 6: '六' }
  return `周${map[d]}`
}

function openSilenceRuleModal() {
  editingSilenceRule.value = null
  silenceRuleForm.vehicleId = undefined
  silenceRuleForm.exceptionType = undefined
  silenceRuleForm.startTime = undefined
  silenceRuleForm.endTime = undefined
  silenceRuleForm.weekDays = []
  silenceRuleForm.note = ''
  silenceRuleModalVisible.value = true
}

function editSilenceRule(rule: SilenceRule) {
  editingSilenceRule.value = rule
  silenceRuleForm.vehicleId = rule.vehicleId
  silenceRuleForm.exceptionType = rule.exceptionType
  silenceRuleForm.startTime = rule.startTime ? dayjs(rule.startTime, 'HH:mm') : undefined
  silenceRuleForm.endTime = rule.endTime ? dayjs(rule.endTime, 'HH:mm') : undefined
  silenceRuleForm.weekDays = [...rule.weekDays]
  silenceRuleForm.note = rule.note
  silenceRuleModalVisible.value = true
}

function saveSilenceRule() {
  const rule: SilenceRule = {
    id: editingSilenceRule.value?.id || `sr-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
    enabled: editingSilenceRule.value?.enabled ?? true,
    vehicleId: silenceRuleForm.vehicleId,
    vehicleCode: silenceRuleForm.vehicleId
      ? vehicleOptions.value.find(v => v.vehicleId === silenceRuleForm.vehicleId)?.vehicleCode
      : undefined,
    exceptionType: silenceRuleForm.exceptionType,
    startTime: silenceRuleForm.startTime ? dayjs(silenceRuleForm.startTime).format('HH:mm') : undefined,
    endTime: silenceRuleForm.endTime ? dayjs(silenceRuleForm.endTime).format('HH:mm') : undefined,
    weekDays: silenceRuleForm.weekDays,
    note: silenceRuleForm.note,
  }
  if (editingSilenceRule.value) {
    const idx = silenceRules.value.findIndex(r => r.id === rule.id)
    if (idx >= 0) silenceRules.value[idx] = rule
  } else {
    silenceRules.value.push(rule)
  }
  silenceRuleModalVisible.value = false
  saveSilenceRulesToStore()
  message.success('静默规则已保存')
}

function toggleSilenceRule(id: string, enabled: boolean) {
  const rule = silenceRules.value.find(r => r.id === id)
  if (rule) {
    rule.enabled = enabled
    saveSilenceRulesToStore()
  }
}

function removeSilenceRule(id: string) {
  silenceRules.value = silenceRules.value.filter(r => r.id !== id)
  saveSilenceRulesToStore()
  message.success('规则已删除')
}

function saveSilenceRulesToStore() {
  alertSettingsApi.saveSilenceRules(silenceRules.value)
}

async function loadSilenceRules() {
  try {
    const res = await alertSettingsApi.fetchSilenceRules()
    silenceRules.value = res.data
    silenceBackendReady.value = true
  } catch {
    silenceBackendReady.value = false
    silenceRules.value = []
  }
}

// ── 升级规则 ──
const escalationRuleColumns = [
  { title: '严重级别', dataIndex: 'severity', key: 'severity', width: 100 },
  { title: '超时（分钟）', dataIndex: 'timeoutMinutes', key: 'timeoutMinutes', width: 100 },
  { title: '升级角色', dataIndex: 'escalateToRole', key: 'escalateToRole', width: 100 },
  { title: '通知方式', dataIndex: 'notifyMethod', key: 'notifyMethod', width: 100 },
  { title: '操作', key: 'actions', width: 120 },
]
const escalationRules = ref<EscalationRule[]>([...DEFAULT_ESCALATION_RULES])
const escalationHistory = ref<any[]>([])
const escalationRuleModalVisible = ref(false)
const editingEscalationRule = ref<EscalationRule | null>(null)
const escalationRuleForm = reactive({
  severity: 'HIGH' as string,
  timeoutMinutes: 30,
  escalateToRole: 'ADMIN' as string,
  notifyMethod: 'browser' as 'sound' | 'browser' | 'sms',
})

function openEscalationRuleModal() {
  editingEscalationRule.value = null
  escalationRuleForm.severity = 'HIGH'
  escalationRuleForm.timeoutMinutes = 30
  escalationRuleForm.escalateToRole = 'ADMIN'
  escalationRuleForm.notifyMethod = 'browser'
  escalationRuleModalVisible.value = true
}

function editEscalationRule(rule: EscalationRule) {
  editingEscalationRule.value = rule
  escalationRuleForm.severity = rule.severity
  escalationRuleForm.timeoutMinutes = rule.timeoutMinutes
  escalationRuleForm.escalateToRole = rule.escalateToRole
  escalationRuleForm.notifyMethod = rule.notifyMethod
  escalationRuleModalVisible.value = true
}

function saveEscalationRule() {
  const rule: EscalationRule = {
    id: editingEscalationRule.value?.id || `er-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
    severity: escalationRuleForm.severity,
    timeoutMinutes: escalationRuleForm.timeoutMinutes,
    escalateToRole: escalationRuleForm.escalateToRole,
    notifyMethod: escalationRuleForm.notifyMethod,
  }
  if (editingEscalationRule.value) {
    const idx = escalationRules.value.findIndex(r => r.id === rule.id)
    if (idx >= 0) escalationRules.value[idx] = rule
  } else {
    escalationRules.value.push(rule)
  }
  escalationRuleModalVisible.value = false
  saveEscalationRulesToStore()
  message.success('升级规则已保存')
}

function removeEscalationRule(id: string) {
  escalationRules.value = escalationRules.value.filter(r => r.id !== id)
  saveEscalationRulesToStore()
  message.success('规则已删除')
}

function saveEscalationRulesToStore() {
  alertSettingsApi.saveEscalationRules(escalationRules.value)
}

async function loadEscalationRules() {
  try {
    const res = await alertSettingsApi.fetchEscalationRules()
    escalationRules.value = res.data
    escalationBackendReady.value = true
  } catch {
    escalationBackendReady.value = false
    escalationRules.value = []
  }
}

async function loadEscalationHistory() {
  if (!escalationBackendReady.value) return
  try {
    const res = await alertSettingsApi.fetchEscalationHistory()
    escalationHistory.value = res.data
  } catch {
    escalationBackendReady.value = false
    escalationHistory.value = []
  }
}

function formatTime(value: string) {
  return dayjs(value).format('MM-DD HH:mm')
}

async function requestPermission() {
  const ok = await requestBrowserNotifyPermission()
  message[ok ? 'success' : 'warning'](ok ? '通知权限已开启' : '通知权限未授予')
}

async function save() {
  const json = JSON.stringify(form)
  try {
    await alertSettingsApi.saveAlertSettings(json)
  } catch {
    message.warning('服务端保存失败，已写入本地')
  }
  alertStore.updateRules(JSON.parse(JSON.stringify(form)))
  message.success('告警设置已保存')
}

onMounted(async () => {
  try {
    const res = await alertSettingsApi.fetchAlertSettings()
    const parsed = JSON.parse(res.data.rulesJson)
    Object.assign(form, parsed)
    alertStore.updateRules(parsed)
  } catch {
    Object.assign(form, JSON.parse(JSON.stringify(alertStore.rules)))
  }
  await Promise.all([
    loadSmsConfig(),
    loadSmsHistory(),
    loadSilenceRules(),
    loadEscalationRules(),
    loadEscalationHistory(),
  ])
  // Legacy silent hours -> time picker
  if (form.silentStartHour != null) {
    legacySilentStart.value = dayjs().hour(form.silentStartHour).minute(0)
  }
  if (form.silentEndHour != null) {
    legacySilentEnd.value = dayjs().hour(form.silentEndHour).minute(0)
  }
})

function reset() {
  alertStore.resetRules()
  Object.assign(form, JSON.parse(JSON.stringify(alertStore.rules)))
  message.success('已恢复默认设置')
}

function testSound() {
  playAlertTone(true)
}
</script>

<style scoped lang="less">
.settings-form {
  max-width: 720px;
}

.level-rules {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.level-row {
  display: grid;
  grid-template-columns: 80px 1fr 1fr;
  align-items: center;
  padding: 8px 12px;
  border: 1px solid var(--fsd-border);
  border-radius: 8px;
}

.phone-validation {
  margin-top: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  align-items: center;
}

.validation-hint {
  font-size: 12px;
  color: var(--fsd-error);
}

.sms-history-item,
.esc-history-item {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.sms-history-phone {
  font-family: 'JetBrains Mono', monospace;
  font-weight: 600;
}

.sms-history-msg {
  color: var(--fsd-text-secondary);
  font-size: 12px;
}

.sms-history-time,
.text-muted {
  color: var(--fsd-text-tertiary);
  font-size: 11px;
}

.empty-hint {
  padding: 16px 0;
  color: var(--fsd-text-tertiary);
  text-align: center;
}

.section-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--fsd-text-primary);
}
</style>