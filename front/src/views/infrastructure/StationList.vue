<template>
  <PageContainer title="站点管理" subtitle="管理取货站、送货站及通用站点">
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
          <PlusOutlined /> 新建站点
        </a-button>
      </a-space>
    </template>

    <a-table
      :columns="columns"
      :data-source="stations"
      :loading="loading"
      row-key="id"
      :pagination="false"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'stationType'">
          <a-tag :color="typeColor(record.stationType)">{{ typeLabel(record.stationType) }}</a-tag>
        </template>
        <template v-else-if="column.key === 'status'">
          <StatusBadge :status="record.status" type="infra" />
        </template>
        <template v-else-if="column.key === 'coord'">
          ({{ record.coordX }}, {{ record.coordY }})
        </template>
        <template v-else-if="column.key === 'actions'">
          <a-button type="link" size="small" @click="openEdit(record)">编辑</a-button>
        </template>
      </template>
    </a-table>

    <a-card v-if="filterParkId && mapPoints.length > 0" title="站点地图预览" size="small" style="margin-top: 16px;">
      <ParkInfraPreview :park-id="filterParkId" :points="mapPoints" />
    </a-card>

    <a-modal
      v-model:open="modalOpen"
      :title="editing ? '编辑站点' : '新建站点'"
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
            <a-form-item label="站点编码" required>
              <a-input v-model:value="form.stationCode" placeholder="如 A1" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="站点名称" required>
              <a-input v-model:value="form.stationName" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="站点类型" required>
              <a-select v-model:value="form.stationType" :options="typeOptions" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="区域">
              <a-input v-model:value="form.area" placeholder="如 A / B" />
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
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="GCJ-02 经度（可选）">
              <a-input-number v-model:value="form.coordLng" :step="0.000001" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="GCJ-02 纬度（可选）">
              <a-input-number v-model:value="form.coordLat" :step="0.000001" style="width: 100%" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-row :gutter="16">
          <a-col :span="8">
            <a-form-item label="容量上限">
              <a-input-number v-model:value="form.capacityLimit" :min="1" placeholder="枢纽/缓冲" style="width: 100%" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item label="状态">
          <a-select v-model:value="form.status" :options="statusOptions" />
        </a-form-item>
        <a-form-item label="备注">
          <a-textarea v-model:value="form.remark" :rows="2" />
        </a-form-item>
      </a-form>
    </a-modal>
  </PageContainer>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import PageContainer from '@/components/common/PageContainer.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import ParkInfraPreview from '@/components/infrastructure/ParkInfraPreview.vue'
import { useParkOptions } from '@/composables/useParkOptions'
import * as infraApi from '@/api/infrastructure'
import type { AdminStation } from '@/types/infrastructure'

const { parkOptions } = useParkOptions()
const loading = ref(false)
const saving = ref(false)
const stations = ref<AdminStation[]>([])
const filterParkId = ref<number | undefined>()
const modalOpen = ref(false)
const editing = ref<AdminStation | null>(null)

const mapPoints = computed(() =>
  stations.value
    .filter((s) => !filterParkId.value || s.parkId === filterParkId.value)
    .map((s) => ({
      code: s.stationCode,
      label: s.stationCode,
      x: Number(s.coordX),
      y: Number(s.coordY),
      color: s.stationType === 'PICKUP' ? '#00b4d8' : s.stationType === 'DROPOFF' ? '#ffb703' : '#8b949e',
    }))
)

const form = reactive({
  parkId: undefined as number | undefined,
  stationCode: '',
  stationName: '',
  stationType: 'PICKUP',
  coordX: 0,
  coordY: 0,
  coordLng: undefined as number | undefined,
  coordLat: undefined as number | undefined,
  area: '',
  status: 'ACTIVE',
  sortOrder: 0,
  capacityLimit: undefined as number | undefined,
  remark: '',
})

const columns = [
  { title: '编码', dataIndex: 'stationCode', key: 'stationCode', width: 100 },
  { title: '名称', dataIndex: 'stationName', key: 'stationName' },
  { title: '园区', dataIndex: 'parkName', key: 'parkName', width: 140 },
  { title: '类型', key: 'stationType', width: 100 },
  { title: '坐标', key: 'coord', width: 140 },
  { title: '状态', key: 'status', width: 90 },
  { title: '操作', key: 'actions', width: 80 },
]

const typeOptions = [
  { label: '取货站', value: 'PICKUP' },
  { label: '送货站', value: 'DROPOFF' },
  { label: '通用站', value: 'GENERAL' },
  { label: '枢纽', value: 'HUB' },
  { label: '缓冲', value: 'BUFFER' },
  { label: '母港', value: 'MOTHERSHIP' },
]

const statusOptions = [
  { label: '可用', value: 'ACTIVE' },
  { label: '维护中', value: 'INACTIVE' },
]

function typeLabel(type: string) {
  const map: Record<string, string> = { PICKUP: '取货', DROPOFF: '送货', GENERAL: '通用' }
  return map[type] || type
}

function typeColor(type: string) {
  const map: Record<string, string> = { PICKUP: 'processing', DROPOFF: 'success', GENERAL: 'default' }
  return map[type] || 'default'
}

async function loadData() {
  loading.value = true
  try {
    stations.value = (await infraApi.fetchStations(filterParkId.value)).data
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editing.value = null
  form.parkId = filterParkId.value ?? parkOptions.value[0]?.value
  form.stationCode = ''
  form.stationName = ''
  form.stationType = 'PICKUP'
  form.coordX = 0
  form.coordY = 0
  form.coordLng = undefined
  form.coordLat = undefined
  form.area = ''
  form.status = 'ACTIVE'
  form.sortOrder = 0
  form.remark = ''
  modalOpen.value = true
}

function openEdit(record: AdminStation) {
  editing.value = record
  form.parkId = record.parkId
  form.stationCode = record.stationCode
  form.stationName = record.stationName
  form.stationType = record.stationType
  form.coordX = Number(record.coordX)
  form.coordY = Number(record.coordY)
  form.coordLng = record.coordLng != null ? Number(record.coordLng) : undefined
  form.coordLat = record.coordLat != null ? Number(record.coordLat) : undefined
  form.area = record.area ?? ''
  form.status = record.status
  form.sortOrder = record.sortOrder ?? 0
  form.remark = record.remark ?? ''
  modalOpen.value = true
}

async function handleSave() {
  if (!form.parkId || !form.stationCode || !form.stationName) {
    message.warning('请填写完整信息')
    return
  }
  saving.value = true
  try {
    const payload = {
      parkId: form.parkId,
      stationCode: form.stationCode,
      stationName: form.stationName,
      stationType: form.stationType,
      coordX: form.coordX,
      coordY: form.coordY,
      coordLng: form.coordLng,
      coordLat: form.coordLat,
      area: form.area || undefined,
      status: form.status,
      sortOrder: form.sortOrder,
      remark: form.remark || undefined,
    }
    if (editing.value) {
      await infraApi.updateStation(editing.value.id, payload)
      message.success('站点已更新')
    } else {
      await infraApi.createStation(payload)
      message.success('站点已创建')
    }
    modalOpen.value = false
    await loadData()
  } finally {
    saving.value = false
  }
}

onMounted(loadData)
</script>
