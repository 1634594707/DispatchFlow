<template>
  <PageContainer title="充电桩管理" subtitle="管理充电桩配置及占用状态">
    <template #actions>
      <a-space>
        <a-select
          v-model:value="filterParkId"
          :options="parkOptions"
          placeholder="筛选园区"
          allow-clear
          style="width: 180px"
          @change="loadData"
        />
        <a-button type="primary" @click="openCreate">
          <PlusOutlined /> 新建充电桩
        </a-button>
      </a-space>
    </template>

    <!-- V5-E5: 充电桩角标「空闲/总数」 -->
    <div class="pile-summary-bar">
      <div class="pile-summary-item">
        <span class="pile-summary-label">充电桩总数</span>
        <strong class="pile-summary-value total">{{ piles.length }}</strong>
      </div>
      <div class="pile-summary-divider"></div>
      <div class="pile-summary-item">
        <span class="pile-summary-label">空闲</span>
        <strong class="pile-summary-value free">{{ freePileCount }}</strong>
      </div>
      <div class="pile-summary-divider"></div>
      <div class="pile-summary-item">
        <span class="pile-summary-label">占用 / 充电中</span>
        <strong class="pile-summary-value occupied">{{ occupiedPileCount }}</strong>
      </div>
      <div class="pile-summary-divider"></div>
      <div class="pile-summary-item">
        <span class="pile-summary-label">故障</span>
        <strong class="pile-summary-value fault">{{ faultPileCount }}</strong>
      </div>
    </div>

    <a-table
      :columns="columns"
      :data-source="piles"
      :loading="loading"
      row-key="id"
      :pagination="false"
      :scroll="{ x: 'max-content' }"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'status'">
          <StatusBadge :status="record.status" type="slot" />
        </template>
        <template v-else-if="column.key === 'maxPowerKw'">
          {{ record.maxPowerKw != null ? `${record.maxPowerKw} kW` : '-' }}
        </template>
        <template v-else-if="column.key === 'occupiedVehicleId'">
          {{ record.occupiedVehicleId ?? '-' }}
        </template>
        <template v-else-if="column.key === 'entryNodeCode'">
          <span v-if="record.entryNodeCode" class="mono-text">{{ record.entryNodeCode }}</span>
          <span v-else class="text-muted">-</span>
        </template>
        <template v-else-if="column.key === 'exitNodeCode'">
          <span v-if="record.exitNodeCode" class="mono-text">{{ record.exitNodeCode }}</span>
          <span v-else class="text-muted">-</span>
        </template>
        <template v-else-if="column.key === 'plugType'">
          <a-tag v-if="record.plugType" color="blue">{{ plugTypeLabel(record.plugType) }}</a-tag>
          <span v-else class="text-muted">-</span>
        </template>
        <template v-else-if="column.key === 'reservationState'">
          <a-tag v-if="record.reservationState" :color="reservationStateColor(record.reservationState)">
            {{ reservationStateLabel(record.reservationState) }}
          </a-tag>
          <span v-else class="text-muted">-</span>
        </template>
        <template v-else-if="column.key === 'actions'">
          <a-button type="link" size="small" @click="openEdit(record)">编辑</a-button>
        </template>
      </template>
    </a-table>

    <a-modal
      v-model:open="modalOpen"
      :title="editing ? '编辑充电桩' : '新建充电桩'"
      :confirm-loading="saving"
      width="520px"
      @ok="handleSave"
    >
      <a-form layout="vertical">
        <a-form-item label="所属园区" required>
          <a-select v-model:value="form.parkId" :options="parkOptions" placeholder="选择园区" @change="loadSlotOptions" />
        </a-form-item>
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="充电桩编码" required>
              <a-input v-model:value="form.pileCode" placeholder="如 CP1" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="充电桩名称" required>
              <a-input v-model:value="form.pileName" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item label="绑定车位" required>
          <a-select
            v-model:value="form.parkingSlotId"
            :options="slotOptions"
            placeholder="选择车位"
            :loading="loadingSlots"
          />
        </a-form-item>
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="额定功率(kW)">
              <a-input-number v-model:value="form.maxPowerKw" :min="0" :step="0.5" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="状态">
              <a-select v-model:value="form.status" :options="statusOptions" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item label="备注">
          <a-textarea v-model:value="form.remark" :rows="2" />
        </a-form-item>
      </a-form>
    </a-modal>
  </PageContainer>
</template>

<script setup lang="ts">
import { computed, ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import PageContainer from '@/components/common/PageContainer.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import { useParkOptions } from '@/composables/useParkOptions'
import * as infraApi from '@/api/infrastructure'
import type { AdminChargingPile, AdminParkingSlot } from '@/types/infrastructure'

const { parkOptions } = useParkOptions()
const loading = ref(false)
const saving = ref(false)
const loadingSlots = ref(false)
const piles = ref<AdminChargingPile[]>([])
const slotOptions = ref<{ label: string; value: number }[]>([])
const filterParkId = ref<number | undefined>()
const modalOpen = ref(false)
const editing = ref<AdminChargingPile | null>(null)

// V5-E5: 充电桩统计
const freePileCount = computed(() => piles.value.filter((p) => p.status === 'FREE').length)
const occupiedPileCount = computed(() => piles.value.filter((p) => p.status === 'OCCUPIED' || p.status === 'CHARGING' || p.status === 'RESERVED').length)
const faultPileCount = computed(() => piles.value.filter((p) => p.status === 'FAULT').length)

const form = reactive({
  parkId: undefined as number | undefined,
  pileCode: '',
  pileName: '',
  parkingSlotId: undefined as number | undefined,
  maxPowerKw: 7 as number | undefined,
  status: 'FREE',
  sortOrder: 0,
  remark: '',
})

const columns = [
  { title: '编码', dataIndex: 'pileCode', key: 'pileCode', width: 90 },
  { title: '名称', dataIndex: 'pileName', key: 'pileName' },
  { title: '园区', dataIndex: 'parkName', key: 'parkName', width: 130 },
  { title: '绑定车位', dataIndex: 'parkingSlotCode', key: 'parkingSlotCode', width: 100 },
  { title: '功率', key: 'maxPowerKw', width: 90 },
  { title: '进站节点', key: 'entryNodeCode', width: 110 },
  { title: '出站节点', key: 'exitNodeCode', width: 110 },
  { title: '充电枪类型', key: 'plugType', width: 120 },
  { title: '预约状态', key: 'reservationState', width: 110 },
  { title: '状态', key: 'status', width: 90 },
  { title: '占用车辆', key: 'occupiedVehicleId', width: 100 },
  { title: '操作', key: 'actions', width: 80, fixed: 'right' as const },
]

/** P2-5: 充电枪类型标签 */
function plugTypeLabel(plug: string): string {
  const map: Record<string, string> = {
    CCS2: 'CCS2',
    GB_T_DC: '国标直流',
    CHAOJI: '超充',
    AC_GENERIC: '交流通用',
    WIRELESS: '无线',
  }
  return map[plug] || plug
}

/** P2-5: 预约状态颜色与标签 */
function reservationStateLabel(state: string): string {
  const map: Record<string, string> = {
    FREE: '空闲',
    RESERVED: '已预约',
    CHARGING: '充电中',
    FAULT: '故障',
  }
  return map[state] || state
}

function reservationStateColor(state: string): string {
  const map: Record<string, string> = {
    FREE: 'success',
    RESERVED: 'processing',
    CHARGING: 'cyan',
    FAULT: 'error',
  }
  return map[state] || 'default'
}

const statusOptions = [
  { label: '空闲', value: 'FREE' },
  { label: '占用', value: 'OCCUPIED' },
  { label: '预留', value: 'RESERVED' },
  { label: '充电中', value: 'CHARGING' },
  { label: '故障', value: 'FAULT' },
]

async function loadSlotOptions() {
  if (!form.parkId) {
    slotOptions.value = []
    return
  }
  loadingSlots.value = true
  try {
    const slots = (await infraApi.fetchParkingSlots(form.parkId)).data
    slotOptions.value = slots.map((s: AdminParkingSlot) => ({
      label: `${s.slotCode} - ${s.slotName}`,
      value: s.id,
    }))
  } finally {
    loadingSlots.value = false
  }
}

async function loadData() {
  loading.value = true
  try {
    piles.value = (await infraApi.fetchChargingPiles(filterParkId.value)).data
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editing.value = null
  form.parkId = filterParkId.value ?? parkOptions.value[0]?.value
  form.pileCode = ''
  form.pileName = ''
  form.parkingSlotId = undefined
  form.maxPowerKw = 7
  form.status = 'FREE'
  form.sortOrder = 0
  form.remark = ''
  loadSlotOptions()
  modalOpen.value = true
}

function openEdit(record: AdminChargingPile) {
  editing.value = record
  form.parkId = record.parkId
  form.pileCode = record.pileCode
  form.pileName = record.pileName
  form.parkingSlotId = record.parkingSlotId
  form.maxPowerKw = record.maxPowerKw != null ? Number(record.maxPowerKw) : undefined
  form.status = record.status
  form.sortOrder = record.sortOrder ?? 0
  form.remark = record.remark ?? ''
  loadSlotOptions()
  modalOpen.value = true
}

async function handleSave() {
  if (!form.parkId || !form.pileCode || !form.pileName || !form.parkingSlotId) {
    message.warning('请填写完整信息')
    return
  }
  saving.value = true
  try {
    const payload = {
      parkId: form.parkId,
      pileCode: form.pileCode,
      pileName: form.pileName,
      parkingSlotId: form.parkingSlotId,
      maxPowerKw: form.maxPowerKw,
      status: form.status,
      sortOrder: form.sortOrder,
      remark: form.remark || undefined,
    }
    if (editing.value) {
      await infraApi.updateChargingPile(editing.value.id, payload)
      message.success('充电桩已更新')
    } else {
      await infraApi.createChargingPile(payload)
      message.success('充电桩已创建')
    }
    modalOpen.value = false
    await loadData()
  } finally {
    saving.value = false
  }
}

onMounted(loadData)
</script>

<style scoped lang="less">
/* V5-E5: 充电桩角标 */
.pile-summary-bar {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 16px;
  margin-bottom: 16px;
  border-radius: var(--fsd-radius-lg);
  border: 1px solid var(--fsd-border);
  background: linear-gradient(135deg, rgba(34, 199, 230, 0.08) 0%, rgba(11, 16, 24, 0.6) 100%);
}

.pile-summary-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.pile-summary-label {
  font-size: 12px;
  color: var(--fsd-text-tertiary);
}

.pile-summary-value {
  font-size: 20px;
  font-weight: 700;
  font-family: 'JetBrains Mono', monospace;

  &.total {
    color: var(--fsd-accent);
  }

  &.free {
    color: var(--fsd-success);
  }

  &.occupied {
    color: var(--fsd-warning);
  }

  &.fault {
    color: var(--fsd-error);
  }
}

.pile-summary-divider {
  width: 1px;
  height: 28px;
  background: var(--fsd-border);
}

.text-muted {
  color: var(--fsd-text-tertiary);
}

.mono-text {
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
  color: var(--fsd-text-secondary);
}
</style>
