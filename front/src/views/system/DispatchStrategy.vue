<template>
  <PageContainer title="调度策略" subtitle="派车评分权重 · 充电阈值 · 灰度 A/B">
    <template #actions>
      <a-button type="primary" @click="openCreate">新建策略</a-button>
      <a-button :loading="loading" @click="loadAll">刷新</a-button>
    </template>

    <a-card title="紧急暂停派单" size="small" class="pause-card">
      <a-space direction="vertical" size="middle" style="width: 100%">
        <a-space wrap>
          <a-radio-group v-model:value="pauseScopeMode" button-style="solid">
            <a-radio-button value="GLOBAL">全局（所有园区）</a-radio-button>
            <a-radio-button value="PARK">指定园区</a-radio-button>
          </a-radio-group>
          <a-select
            v-if="pauseScopeMode === 'PARK'"
            v-model:value="pauseParkId"
            :options="parkOptions"
            placeholder="选择园区"
            style="width: 180px"
          />
          <a-input
            v-model:value="pauseReason"
            placeholder="暂停原因（暂停时必填）"
            :maxlength="128"
            :disabled="!canOperatePause"
            style="width: 260px"
          />
          <a-button danger :disabled="!canOperatePause" :loading="pausing" @click="applyPause(true)">
            暂停派单
          </a-button>
          <a-button :disabled="!canOperatePause" :loading="pausing" @click="applyPause(false)">
            恢复派单
          </a-button>
        </a-space>

        <!-- 读不到状态时不许说"正常进行中"：把故障显示成绿勾，比没有面板更危险 -->
        <a-alert
          v-if="pauseError"
          type="warning"
          show-icon
          :message="`暂停状态读取失败：${pauseError}`"
        />
        <a-alert
          v-else-if="isPaused"
          type="error"
          show-icon
          :message="`${pauseScopeLabel}：派单已暂停`"
          :description="`原因：${pauseState?.pauseReason || '未记录'} · 操作人：${pauseState?.pausedBy || '未知'} · 时间：${pauseState?.pausedAt || '未知'}`"
        />
        <a-alert v-else type="success" show-icon :message="`${pauseScopeLabel}：派单正常进行中`" />
      </a-space>
    </a-card>

    <a-spin :spinning="loading">
      <a-table
        row-key="id"
        size="middle"
        :pagination="false"
        :data-source="profiles"
        :columns="columns"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'type'">
            <a-tag :color="record.profileType === 'EXPERIMENT' ? 'purple' : 'blue'">
              {{ record.profileType }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'active'">
            <a-badge :status="record.active ? 'success' : 'default'" :text="record.active ? '生效中' : '未激活'" />
          </template>
          <template v-else-if="column.key === 'weights'">
            <span class="mono">
              D{{ record.weightDistance }} / SOC{{ record.weightSocMargin }} / 插枪{{ record.weightPluggedStandbyBonus }}
            </span>
          </template>
          <template v-else-if="column.key === 'energy'">
            可派≥{{ record.minAssignableSoc }}% · 满电{{ record.fullSoc }}%
          </template>
          <template v-else-if="column.key === 'actions'">
            <a-space>
              <a-button type="link" size="small" @click="openEdit(record)">编辑</a-button>
              <a-button
                v-if="!record.active"
                type="link"
                size="small"
                @click="handleActivate(record.id)"
              >
                激活
              </a-button>
            </a-space>
          </template>
        </template>
      </a-table>

      <a-card title="变更记录" size="small" style="margin-top: 20px;">
        <a-timeline v-if="changeLogs.length > 0">
          <a-timeline-item v-for="log in changeLogs" :key="log.id">
            <strong>{{ log.changeType }}</strong> · {{ log.profileName }}
            <span v-if="log.operatorName"> · {{ log.operatorName }}</span>
            <div v-if="log.changeSummary" class="log-summary">{{ log.changeSummary }}</div>
            <div class="log-time">{{ log.createdAt }}</div>
          </a-timeline-item>
        </a-timeline>
        <a-empty v-else description="暂无变更记录" />
      </a-card>
    </a-spin>

    <a-modal
      v-model:open="modalOpen"
      :title="editing ? '编辑策略' : '新建策略'"
      width="560px"
      :confirm-loading="saving"
      @ok="handleSave"
    >
      <a-form layout="vertical">
        <a-form-item label="策略名称" required>
          <a-input v-model:value="form.profileName" />
        </a-form-item>
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="类型" required>
              <a-select v-model:value="form.profileType" :options="typeOptions" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="灰度流量 %">
              <a-input-number v-model:value="form.grayPercent" :min="0" :max="100" style="width: 100%" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item label="适用园区（空=全局）">
          <a-select v-model:value="form.parkId" allow-clear :options="parkOptions" placeholder="全局" />
        </a-form-item>
        <a-divider>评分权重</a-divider>
        <a-row :gutter="16">
          <a-col :span="8">
            <a-form-item label="距离权重">
              <a-input-number v-model:value="form.weightDistance" :min="0" :step="0.1" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="电量惩罚">
              <a-input-number v-model:value="form.weightSocMargin" :min="0" :step="0.05" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="插枪奖励">
              <a-input-number v-model:value="form.weightPluggedStandbyBonus" :min="0" :step="5" style="width: 100%" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="最低可派 SOC">
              <a-input-number v-model:value="form.minAssignableSoc" :min="0" :max="100" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="满电 SOC">
              <a-input-number v-model:value="form.fullSoc" :min="0" :max="100" style="width: 100%" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item label="低电恢复策略">
          <a-select v-model:value="form.energyRecoveryMode" :options="energyRecoveryOptions" />
        </a-form-item>
        <a-form-item label="备注">
          <a-textarea v-model:value="form.remark" :rows="2" />
        </a-form-item>
      </a-form>
    </a-modal>
  </PageContainer>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import PageContainer from '@/components/common/PageContainer.vue'
import { useParkOptions } from '@/composables/useParkOptions'
import { useParkScopeStore } from '@/stores/parkScope'
import * as strategyApi from '@/api/dispatchStrategy'
import * as pauseApi from '@/api/dispatchPause'
import type { DispatchPauseStatus } from '@/api/dispatchPause'
import type {
  DispatchStrategyProfile,
  DispatchStrategyUpsertPayload,
  StrategyChangeLog,
} from '@/types/dispatchStrategy'

const { parkOptions } = useParkOptions()

const pauseScopeMode = ref<'GLOBAL' | 'PARK'>('GLOBAL')
const pauseParkId = ref<number | undefined>(undefined)
const pauseReason = ref('')
const pausing = ref(false)
const pauseState = ref<DispatchPauseStatus | null>(null)
const pauseError = ref('')

/** 「指定园区」还没选园时 parkId 会是 undefined ⇒ 落到后端就是**全局档**，所以此时禁止操作。 */
const canOperatePause = computed(() => pauseScopeMode.value === 'GLOBAL' || pauseParkId.value != null)
const pauseScope = computed<number | null>(() =>
  pauseScopeMode.value === 'GLOBAL' ? null : pauseParkId.value ?? null,
)
const pauseScopeLabel = computed(() => {
  if (pauseScopeMode.value === 'GLOBAL') return '全局'
  const park = parkOptions.value.find((option) => option.value === pauseParkId.value)
  return park ? `园区「${park.label}」` : '未选园区'
})
const isPaused = computed(() => !!pauseState.value && (pauseState.value.globalPaused || pauseState.value.parkPaused))

async function loadPauseState() {
  pauseError.value = ''
  try {
    const res = await pauseApi.fetchDispatchPauseStatus(pauseScope.value ?? undefined)
    pauseState.value = res.data
  } catch (err) {
    pauseState.value = null
    pauseError.value = err instanceof Error ? err.message : String(err)
  }
}

async function applyPause(paused: boolean) {
  if (paused && !pauseReason.value.trim()) {
    message.warning('暂停派单必须填写原因')
    return
  }
  pausing.value = true
  try {
    const res = await pauseApi.setDispatchPause(
      pauseScope.value,
      paused,
      pauseReason.value.trim() || undefined,
    )
    pauseState.value = res.data
    message.success(paused ? '已暂停派单' : '已恢复派单')
    if (!paused) pauseReason.value = ''
  } catch (err) {
    message.error(err instanceof Error ? err.message : '操作失败')
  } finally {
    pausing.value = false
  }
}

watch(pauseScope, () => {
  void loadPauseState()
})

const loading = ref(false)
const saving = ref(false)
const profiles = ref<DispatchStrategyProfile[]>([])
const changeLogs = ref<StrategyChangeLog[]>([])
const modalOpen = ref(false)
const editing = ref<DispatchStrategyProfile | null>(null)

const typeOptions = [
  { label: '生产 PRODUCTION', value: 'PRODUCTION' },
  { label: '实验 EXPERIMENT', value: 'EXPERIMENT' },
]

const form = reactive<DispatchStrategyUpsertPayload>({
  profileName: '',
  profileType: 'PRODUCTION',
  grayPercent: 0,
  parkId: undefined,
  weightDistance: 1,
  weightSocMargin: 0.15,
  weightPluggedStandbyBonus: 80,
  minAssignableSoc: 30,
  fullSoc: 100,
  energyRecoveryMode: 'CHARGE',
  remark: '',
})

const energyRecoveryOptions = [
  { label: '充电 CHARGE', value: 'CHARGE' },
  { label: '换电 SWAP', value: 'SWAP' },
  { label: '自动 AUTO', value: 'AUTO' },
]

const columns = [
  { title: '名称', dataIndex: 'profileName', key: 'name' },
  { title: '类型', key: 'type', width: 120 },
  { title: '状态', key: 'active', width: 100 },
  { title: '灰度%', dataIndex: 'grayPercent', width: 80 },
  { title: '权重', key: 'weights' },
  { title: '电量', key: 'energy', width: 160 },
  { title: '操作', key: 'actions', width: 140 },
]

const parkScope = useParkScopeStore()

async function loadAll() {
  loading.value = true
  try {
    const [pRes, lRes] = await Promise.all([
      strategyApi.fetchStrategyProfiles(parkScope.selectedParkId),
      strategyApi.fetchStrategyChangeLogs(),
    ])
    profiles.value = pRes.data
    changeLogs.value = lRes.data
  } finally {
    loading.value = false
  }
}

function resetForm() {
  form.profileName = ''
  form.profileType = 'PRODUCTION'
  form.grayPercent = 0
  form.parkId = undefined
  form.weightDistance = 1
  form.weightSocMargin = 0.15
  form.weightPluggedStandbyBonus = 80
  form.minAssignableSoc = 30
  form.fullSoc = 100
  form.energyRecoveryMode = 'CHARGE'
  form.remark = ''
}

function openCreate() {
  editing.value = null
  resetForm()
  modalOpen.value = true
}

function openEdit(record: DispatchStrategyProfile) {
  editing.value = record
  form.profileName = record.profileName
  form.profileType = record.profileType
  form.grayPercent = record.grayPercent ?? 0
  form.parkId = record.parkId ?? undefined
  form.weightDistance = Number(record.weightDistance)
  form.weightSocMargin = Number(record.weightSocMargin)
  form.weightPluggedStandbyBonus = Number(record.weightPluggedStandbyBonus)
  form.minAssignableSoc = record.minAssignableSoc
  form.fullSoc = record.fullSoc
  form.energyRecoveryMode = record.energyRecoveryMode || 'CHARGE'
  form.remark = record.remark || ''
  modalOpen.value = true
}

async function handleSave() {
  if (!form.profileName) {
    message.warning('请填写策略名称')
    return
  }
  saving.value = true
  try {
    if (editing.value) {
      await strategyApi.updateStrategyProfile(editing.value.id, form)
      message.success('策略已更新')
    } else {
      await strategyApi.createStrategyProfile(form)
      message.success('策略已创建')
    }
    modalOpen.value = false
    await loadAll()
  } finally {
    saving.value = false
  }
}

async function handleActivate(id: number) {
  await strategyApi.activateStrategyProfile(id)
  message.success('策略已激活')
  await loadAll()
}

onMounted(() => {
  void loadAll()
  void loadPauseState()
})
</script>

<style scoped lang="less">
.mono {
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
}
.log-summary {
  color: var(--fsd-text-secondary);
  font-size: 13px;
}
.log-time {
  font-size: 12px;
  color: var(--fsd-text-tertiary);
}
</style>
