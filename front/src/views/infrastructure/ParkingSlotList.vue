<template>
  <PageContainer title="停车位管理" subtitle="管理园区停车位及占用状态">
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
          <PlusOutlined /> 新建车位
        </a-button>
      </a-space>
    </template>

    <a-table
      :columns="columns"
      :data-source="slots"
      :loading="loading"
      row-key="id"
      :pagination="false"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'slotType'">
          {{ record.slotType === 'CHARGING_ONLY' ? '充电专用' : '待命' }}
        </template>
        <template v-else-if="column.key === 'status'">
          <StatusBadge :status="record.status" type="slot" />
        </template>
        <template v-else-if="column.key === 'coord'">
          ({{ record.coordX }}, {{ record.coordY }})
        </template>
        <template v-else-if="column.key === 'occupiedVehicleId'">
          {{ record.occupiedVehicleId ?? '-' }}
        </template>
        <template v-else-if="column.key === 'actions'">
          <a-button type="link" size="small" @click="openEdit(record)">编辑</a-button>
        </template>
      </template>
    </a-table>

    <a-card v-if="filterParkId && mapPoints.length > 0" title="车位地图预览" size="small" style="margin-top: 16px;">
      <ParkInfraPreview :park-id="filterParkId" :points="mapPoints" />
    </a-card>

    <a-modal
      v-model:open="modalOpen"
      :title="editing ? '编辑车位' : '新建车位'"
      :confirm-loading="saving"
      width="520px"
      @ok="handleSave"
    >
      <a-form layout="vertical">
        <a-form-item label="所属园区" required>
          <a-select v-model:value="form.parkId" :options="parkOptions" placeholder="选择园区" />
        </a-form-item>
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="车位编码" required>
              <a-input v-model:value="form.slotCode" placeholder="如 P1" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="车位名称" required>
              <a-input v-model:value="form.slotName" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="车位类型">
              <a-select v-model:value="form.slotType" :options="typeOptions" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="状态">
              <a-select v-model:value="form.status" :options="statusOptions" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-row :gutter="16">
          <a-col :span="8">
            <a-form-item label="坐标 X" required>
              <a-input-number v-model:value="form.coordX" :min="0" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="坐标 Y" required>
              <a-input-number v-model:value="form.coordY" :min="0" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="排序">
              <a-input-number v-model:value="form.sortOrder" :min="0" style="width: 100%" />
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
import { ref, reactive, computed, onMounted } from 'vue'
import ParkInfraPreview from '@/components/infrastructure/ParkInfraPreview.vue'
import { message } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import PageContainer from '@/components/common/PageContainer.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import { useParkOptions } from '@/composables/useParkOptions'
import * as infraApi from '@/api/infrastructure'
import type { AdminParkingSlot } from '@/types/infrastructure'

const { parkOptions } = useParkOptions()
const loading = ref(false)
const saving = ref(false)
const slots = ref<AdminParkingSlot[]>([])
const filterParkId = ref<number | undefined>()
const modalOpen = ref(false)
const editing = ref<AdminParkingSlot | null>(null)

const mapPoints = computed(() =>
  slots.value
    .filter((s) => !filterParkId.value || s.parkId === filterParkId.value)
    .map((s) => ({
      code: s.slotCode,
      label: s.slotCode,
      x: Number(s.coordX),
      y: Number(s.coordY),
      color: s.status === 'FREE' ? '#00e676' : '#ffb703',
    }))
)

const form = reactive({
  parkId: undefined as number | undefined,
  slotCode: '',
  slotName: '',
  slotType: 'STANDBY',
  coordX: 0,
  coordY: 0,
  status: 'FREE',
  sortOrder: 0,
  remark: '',
})

const columns = [
  { title: '编码', dataIndex: 'slotCode', key: 'slotCode', width: 90 },
  { title: '名称', dataIndex: 'slotName', key: 'slotName' },
  { title: '园区', dataIndex: 'parkName', key: 'parkName', width: 130 },
  { title: '类型', key: 'slotType', width: 100 },
  { title: '坐标', key: 'coord', width: 130 },
  { title: '状态', key: 'status', width: 90 },
  { title: '占用车辆', key: 'occupiedVehicleId', width: 100 },
  { title: '操作', key: 'actions', width: 80 },
]

const typeOptions = [
  { label: '待命车位', value: 'STANDBY' },
  { label: '充电专用', value: 'CHARGING_ONLY' },
]

const statusOptions = [
  { label: '空闲', value: 'FREE' },
  { label: '占用', value: 'OCCUPIED' },
  { label: '预留', value: 'RESERVED' },
  { label: '充电中', value: 'CHARGING' },
  { label: '故障', value: 'FAULT' },
]

async function loadData() {
  loading.value = true
  try {
    slots.value = (await infraApi.fetchParkingSlots(filterParkId.value)).data
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editing.value = null
  form.parkId = filterParkId.value ?? parkOptions.value[0]?.value
  form.slotCode = ''
  form.slotName = ''
  form.slotType = 'STANDBY'
  form.coordX = 0
  form.coordY = 0
  form.status = 'FREE'
  form.sortOrder = 0
  form.remark = ''
  modalOpen.value = true
}

function openEdit(record: AdminParkingSlot) {
  editing.value = record
  form.parkId = record.parkId
  form.slotCode = record.slotCode
  form.slotName = record.slotName
  form.slotType = record.slotType
  form.coordX = Number(record.coordX)
  form.coordY = Number(record.coordY)
  form.status = record.status
  form.sortOrder = record.sortOrder ?? 0
  form.remark = record.remark ?? ''
  modalOpen.value = true
}

async function handleSave() {
  if (!form.parkId || !form.slotCode || !form.slotName) {
    message.warning('请填写完整信息')
    return
  }
  saving.value = true
  try {
    const payload = {
      parkId: form.parkId,
      slotCode: form.slotCode,
      slotName: form.slotName,
      slotType: form.slotType,
      coordX: form.coordX,
      coordY: form.coordY,
      status: form.status,
      sortOrder: form.sortOrder,
      remark: form.remark || undefined,
    }
    if (editing.value) {
      await infraApi.updateParkingSlot(editing.value.id, payload)
      message.success('车位已更新')
    } else {
      await infraApi.createParkingSlot(payload)
      message.success('车位已创建')
    }
    modalOpen.value = false
    await loadData()
  } finally {
    saving.value = false
  }
}

onMounted(loadData)
</script>
