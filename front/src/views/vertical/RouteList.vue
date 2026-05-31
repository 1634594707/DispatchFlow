<template>
  <PageContainer title="线路管理" subtitle="家纺产业带 · 有序站点 + 运营时段">
    <template #actions>
      <a-space>
        <a-select v-model:value="filterParkId" :options="parkOptions" placeholder="筛选园区" allow-clear style="width: 180px" @change="loadData" />
        <a-button type="primary" @click="openCreate"><PlusOutlined /> 新建线路</a-button>
      </a-space>
    </template>

    <a-table :columns="columns" :data-source="routes" :loading="loading" row-key="id" :pagination="false">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'status'">
          <a-tag :color="record.status === 'ACTIVE' ? 'success' : 'default'">{{ record.status === 'ACTIVE' ? '启用' : '停用' }}</a-tag>
        </template>
        <template v-else-if="column.key === 'occupancy'">
          {{ record.activeTaskCount ?? 0 }} / {{ record.maxConcurrentTasks ?? '∞' }}
        </template>
        <template v-else-if="column.key === 'stations'">
          {{ (record.stations || []).map((s: RouteStation) => s.stationCode).join(' → ') }}
        </template>
        <template v-else-if="column.key === 'actions'">
          <a-button type="link" size="small" @click="openEdit(record)">编辑</a-button>
          <a-button type="link" size="small" @click="toggleStatus(record)">{{ record.status === 'ACTIVE' ? '停用' : '启用' }}</a-button>
        </template>
      </template>
    </a-table>

    <a-modal v-model:open="modalOpen" :title="editing ? '编辑线路' : '新建线路'" :confirm-loading="saving" width="640px" @ok="handleSave">
      <a-form layout="vertical">
        <a-row :gutter="16">
          <a-col :span="12"><a-form-item label="园区" required><a-select v-model:value="form.parkId" :options="parkOptions" /></a-form-item></a-col>
          <a-col :span="12"><a-form-item label="线路编码" required><a-input v-model:value="form.routeCode" /></a-form-item></a-col>
        </a-row>
        <a-form-item label="线路名称" required><a-input v-model:value="form.routeName" /></a-form-item>
        <a-row :gutter="16">
          <a-col :span="8"><a-form-item label="车型"><a-select v-model:value="form.requiredVehicleType" allow-clear :options="vehicleTypeOptions" /></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="最大并发"><a-input-number v-model:value="form.maxConcurrentTasks" :min="1" style="width:100%" /></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="运营时段"><a-input v-model:value="serviceWindow" placeholder="08:00-22:00" /></a-form-item></a-col>
        </a-row>
        <a-form-item label="站点顺序（取货→枢纽→送货）" required>
          <a-select v-model:value="form.stationIds" mode="multiple" :options="stationOptions" placeholder="按顺序多选" />
        </a-form-item>
        <a-form-item v-if="form.parkId && form.stationIds.length >= 2" label="线路地图预览">
          <ParkInfraPreview
            :park-id="form.parkId"
            :points="routeMapPoints"
            :segments="routeMapSegments"
            :height="240"
          />
        </a-form-item>
      </a-form>
    </a-modal>
  </PageContainer>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import PageContainer from '@/components/common/PageContainer.vue'
import ParkInfraPreview from '@/components/infrastructure/ParkInfraPreview.vue'
import { useParkOptions } from '@/composables/useParkOptions'
import * as verticalApi from '@/api/vertical'
import type { DispatchRoute, RouteStation } from '@/api/vertical'
import * as infraApi from '@/api/infrastructure'
import type { AdminStation } from '@/types/infrastructure'

const { parkOptions } = useParkOptions()
const loading = ref(false)
const saving = ref(false)
const routes = ref<DispatchRoute[]>([])
const filterParkId = ref<number>()
const modalOpen = ref(false)
const editing = ref<DispatchRoute | null>(null)
const stationOptions = ref<{ label: string; value: number }[]>([])
const stationById = ref<Record<number, AdminStation>>({})

const routeMapPoints = computed(() => form.stationIds
  .map((id) => stationById.value[id])
  .filter(Boolean)
  .map((s, index) => ({
    code: s!.stationCode,
    label: `${index + 1}. ${s!.stationName}`,
    x: Number(s!.coordX),
    y: Number(s!.coordY),
    color: index === 0 ? '#52c41a' : index === form.stationIds.length - 1 ? '#1677ff' : '#faad14',
  })))

const routeMapSegments = computed(() => {
  const points = routeMapPoints.value
  const segments: { from: string; to: string; color?: string }[] = []
  for (let i = 0; i < points.length - 1; i++) {
    segments.push({ from: points[i].code, to: points[i + 1].code, color: '#00B4D8' })
  }
  return segments
})

const form = reactive({
  parkId: undefined as number | undefined,
  routeCode: '',
  routeName: '',
  requiredVehicleType: undefined as string | undefined,
  maxConcurrentTasks: 8 as number | undefined,
  stationIds: [] as number[],
  serviceStartTime: undefined as string | undefined,
  serviceEndTime: undefined as string | undefined,
})

const serviceWindow = computed({
  get: () => [form.serviceStartTime, form.serviceEndTime].filter(Boolean).join('-'),
  set: (v: string) => {
    const [start, end] = (v || '').split('-').map((s) => s.trim())
    form.serviceStartTime = start || undefined
    form.serviceEndTime = end || undefined
  },
})

const columns = [
  { title: '编码', dataIndex: 'routeCode', key: 'routeCode', width: 120 },
  { title: '名称', dataIndex: 'routeName', key: 'routeName' },
  { title: '站点链', key: 'stations' },
  { title: '占用', key: 'occupancy', width: 100 },
  { title: '状态', key: 'status', width: 90 },
  { title: '操作', key: 'actions', width: 140 },
]

const vehicleTypeOptions = [
  { label: '室外 L4', value: 'OUTDOOR_L4' },
  { label: '室内 AMR', value: 'INDOOR_AMR' },
  { label: '叉车', value: 'FORKLIFT' },
]

async function loadStations(parkId?: number) {
  if (!parkId) { stationOptions.value = []; stationById.value = {}; return }
  const res = await infraApi.fetchStations(parkId)
  stationById.value = Object.fromEntries(res.data.map((s) => [s.id, s]))
  stationOptions.value = res.data.map((s) => ({ label: `${s.stationCode} ${s.stationName}`, value: s.id }))
}

async function loadData() {
  loading.value = true
  try {
    routes.value = (await verticalApi.fetchRoutes(filterParkId.value)).data
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editing.value = null
  form.parkId = filterParkId.value ?? parkOptions.value[0]?.value
  form.routeCode = ''
  form.routeName = ''
  form.requiredVehicleType = 'OUTDOOR_L4'
  form.maxConcurrentTasks = 8
  form.stationIds = []
  form.serviceStartTime = '08:00:00'
  form.serviceEndTime = '22:00:00'
  loadStations(form.parkId)
  modalOpen.value = true
}

function openEdit(record: DispatchRoute) {
  editing.value = record
  form.parkId = record.parkId
  form.routeCode = record.routeCode
  form.routeName = record.routeName
  form.requiredVehicleType = record.requiredVehicleType
  form.maxConcurrentTasks = record.maxConcurrentTasks
  form.stationIds = (record.stations || []).sort((a, b) => a.sequenceNo - b.sequenceNo).map((s) => s.stationId)
  form.serviceStartTime = record.serviceStartTime
  form.serviceEndTime = record.serviceEndTime
  loadStations(record.parkId)
  modalOpen.value = true
}

async function handleSave() {
  if (!form.parkId || !form.routeCode || !form.routeName || form.stationIds.length < 2) {
    message.warning('请填写完整信息，线路至少 2 个站点')
    return
  }
  saving.value = true
  try {
    const payload = { ...form, parkId: form.parkId, stationIds: form.stationIds }
    if (editing.value) await verticalApi.updateRoute(editing.value.id, payload)
    else await verticalApi.createRoute(payload)
    message.success('保存成功')
    modalOpen.value = false
    await loadData()
  } finally {
    saving.value = false
  }
}

async function toggleStatus(record: DispatchRoute) {
  await verticalApi.toggleRouteStatus(record.id)
  message.success('状态已更新')
  await loadData()
}

onMounted(loadData)
</script>
