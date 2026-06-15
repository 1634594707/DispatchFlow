<template>
  <PageContainer title="园区管理" subtitle="管理园区基础信息与地图配置">
    <template #actions>
      <a-button @click="wizardOpen = true">配置向导</a-button>
      <a-button type="primary" @click="openCreate">
        <PlusOutlined /> 新建园区
      </a-button>
    </template>
    <ParkSetupWizard v-model:open="wizardOpen" />

    <a-table
      :columns="columns"
      :data-source="parks"
      :loading="loading"
      row-key="id"
      :pagination="false"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'status'">
          <StatusBadge :status="record.status" type="infra" />
        </template>
        <template v-else-if="column.key === 'defaultPark'">
          <a-tag v-if="record.defaultPark" color="cyan">默认</a-tag>
          <span v-else>-</span>
        </template>
        <template v-else-if="column.key === 'mapSize'">
          {{ record.mapWidth ?? '-' }} × {{ record.mapHeight ?? '-' }}
        </template>
        <template v-else-if="column.key === 'actions'">
          <a-space>
            <a-button type="link" size="small" @click="openEdit(record)">编辑</a-button>
            <a-popconfirm
              v-if="!record.defaultPark"
              :title="record.status === 'ACTIVE' ? '确定停用该园区？' : '确定启用该园区？'"
              @confirm="handleToggle(record.id)"
            >
              <a-button type="link" size="small">
                {{ record.status === 'ACTIVE' ? '停用' : '启用' }}
              </a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>

    <a-modal
      v-model:open="modalOpen"
      :title="editing ? '编辑园区' : '新建园区'"
      :confirm-loading="saving"
      width="560px"
      @ok="handleSave"
    >
      <a-form layout="vertical">
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="园区编码" required>
              <a-input v-model:value="form.parkCode" placeholder="如 DEFAULT" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="园区名称" required>
              <a-input v-model:value="form.parkName" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-row :gutter="16">
          <a-col :span="8">
            <a-form-item label="地图宽度(px)">
              <a-input-number v-model:value="form.mapWidth" :min="100" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="地图高度(px)">
              <a-input-number v-model:value="form.mapHeight" :min="100" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="仿真车速(px/s)">
              <a-input-number v-model:value="form.vehicleSpeedPxPerSecond" :min="1" :step="0.5" style="width: 100%" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-row :gutter="16">
          <a-col :span="8">
            <a-form-item label="最小缩放">
              <a-input-number v-model:value="form.minZoom" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="最大缩放">
              <a-input-number v-model:value="form.maxZoom" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="状态">
              <a-select v-model:value="form.status" :options="statusOptions" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item label="设为默认园区">
          <a-switch v-model:checked="form.defaultPark" />
        </a-form-item>
        <a-form-item label="备注">
          <a-textarea v-model:value="form.remark" :rows="2" />
        </a-form-item>
      </a-form>
    </a-modal>
  </PageContainer>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import PageContainer from '@/components/common/PageContainer.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import ParkSetupWizard from '@/views/infrastructure/ParkSetupWizard.vue'
import * as infraApi from '@/api/infrastructure'
import type { AdminPark } from '@/types/infrastructure'

const wizardOpen = ref(false)
const loading = ref(false)
const saving = ref(false)
const parks = ref<AdminPark[]>([])
const modalOpen = ref(false)
const editing = ref<AdminPark | null>(null)

const form = reactive({
  parkCode: '',
  parkName: '',
  mapWidth: 1200 as number | undefined,
  mapHeight: 800 as number | undefined,
  minZoom: -1 as number | undefined,
  maxZoom: 3 as number | undefined,
  vehicleSpeedPxPerSecond: 8 as number | undefined,
  status: 'ACTIVE',
  defaultPark: false,
  remark: '',
})

const columns = [
  { title: '编码', dataIndex: 'parkCode', key: 'parkCode', width: 120 },
  { title: '名称', dataIndex: 'parkName', key: 'parkName' },
  { title: '地图尺寸', key: 'mapSize', width: 140 },
  { title: '状态', key: 'status', width: 90 },
  { title: '默认', key: 'defaultPark', width: 80 },
  { title: '操作', key: 'actions', width: 140 },
]

const statusOptions = [
  { label: '启用', value: 'ACTIVE' },
  { label: '停用', value: 'INACTIVE' },
]

async function loadData() {
  loading.value = true
  try {
    parks.value = (await infraApi.fetchParks()).data
  } finally {
    loading.value = false
  }
}

function resetForm() {
  form.parkCode = ''
  form.parkName = ''
  form.mapWidth = 1200
  form.mapHeight = 800
  form.minZoom = -1
  form.maxZoom = 3
  form.vehicleSpeedPxPerSecond = 8
  form.status = 'ACTIVE'
  form.defaultPark = false
  form.remark = ''
}

function openCreate() {
  editing.value = null
  resetForm()
  modalOpen.value = true
}

function openEdit(record: AdminPark) {
  editing.value = record
  form.parkCode = record.parkCode
  form.parkName = record.parkName
  form.mapWidth = record.mapWidth
  form.mapHeight = record.mapHeight
  form.minZoom = record.minZoom
  form.maxZoom = record.maxZoom
  form.vehicleSpeedPxPerSecond = record.vehicleSpeedPxPerSecond
  form.status = record.status
  form.defaultPark = record.defaultPark
  form.remark = record.remark ?? ''
  modalOpen.value = true
}

async function handleSave() {
  if (!form.parkCode || !form.parkName) {
    message.warning('请填写园区编码和名称')
    return
  }
  saving.value = true
  try {
    const payload = { ...form }
    if (editing.value) {
      await infraApi.updatePark(editing.value.id, payload)
      message.success('园区已更新')
    } else {
      await infraApi.createPark(payload)
      message.success('园区已创建')
    }
    modalOpen.value = false
    await loadData()
  } finally {
    saving.value = false
  }
}

async function handleToggle(parkId: number) {
  await infraApi.toggleParkStatus(parkId)
  message.success('状态已更新')
  await loadData()
}

onMounted(loadData)
</script>
