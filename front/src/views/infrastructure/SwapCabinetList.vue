<template>
  <PageContainer title="换电柜管理" subtitle="配置换电柜位置与容量">
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
        <a-button type="primary" @click="openCreate"><PlusOutlined /> 新建换电柜</a-button>
      </a-space>
    </template>

    <a-table :columns="columns" :data-source="cabinets" :loading="loading" row-key="id" :pagination="false">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'coords'">{{ record.coordX }}, {{ record.coordY }}</template>
        <template v-else-if="column.key === 'status'">
          <a-tag :color="record.status === 'ACTIVE' ? 'success' : 'default'">{{ record.status }}</a-tag>
        </template>
        <template v-else-if="column.key === 'actions'">
          <a-button type="link" size="small" @click="openEdit(record)">编辑</a-button>
          <a-popconfirm title="确认删除？" @confirm="remove(record.id)">
            <a-button type="link" size="small" danger>删除</a-button>
          </a-popconfirm>
        </template>
      </template>
    </a-table>

    <a-modal v-model:open="modalOpen" :title="editing ? '编辑换电柜' : '新建换电柜'" :confirm-loading="saving" width="520px" @ok="handleSave">
      <a-form layout="vertical">
        <a-form-item label="所属园区" required>
          <a-select v-model:value="form.parkId" :options="parkOptions" placeholder="选择园区" />
        </a-form-item>
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="编码" required><a-input v-model:value="form.cabinetCode" /></a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="名称" required><a-input v-model:value="form.cabinetName" /></a-form-item>
          </a-col>
        </a-row>
        <a-row :gutter="16">
          <a-col :span="8"><a-form-item label="X" required><a-input-number v-model:value="form.coordX" style="width:100%" /></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="Y" required><a-input-number v-model:value="form.coordY" style="width:100%" /></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="槽位数"><a-input-number v-model:value="form.slotCount" :min="1" style="width:100%" /></a-form-item></a-col>
        </a-row>
        <a-form-item label="状态">
          <a-select v-model:value="form.status" :options="statusOptions" />
        </a-form-item>
      </a-form>
    </a-modal>
  </PageContainer>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import PageContainer from '@/components/common/PageContainer.vue'
import { useParkOptions } from '@/composables/useParkOptions'
import * as infraApi from '@/api/infrastructure'
import type { AdminBatterySwapCabinet } from '@/api/infrastructure'

const { parkOptions } = useParkOptions()
const loading = ref(false)
const saving = ref(false)
const cabinets = ref<AdminBatterySwapCabinet[]>([])
const filterParkId = ref<number>()
const modalOpen = ref(false)
const editing = ref<AdminBatterySwapCabinet | null>(null)

const form = reactive({
  parkId: undefined as number | undefined,
  cabinetCode: '',
  cabinetName: '',
  coordX: 600,
  coordY: 500,
  slotCount: 4,
  status: 'ACTIVE',
})

const statusOptions = [
  { label: '启用', value: 'ACTIVE' },
  { label: '停用', value: 'INACTIVE' },
]

const columns = [
  { title: '编码', dataIndex: 'cabinetCode', key: 'cabinetCode', width: 120 },
  { title: '名称', dataIndex: 'cabinetName', key: 'cabinetName' },
  { title: '坐标', key: 'coords', width: 140 },
  { title: '槽位', dataIndex: 'slotCount', key: 'slotCount', width: 80 },
  { title: '状态', key: 'status', width: 90 },
  { title: '操作', key: 'actions', width: 120 },
]

async function loadData() {
  loading.value = true
  try {
    cabinets.value = (await infraApi.fetchSwapCabinets(filterParkId.value)).data
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editing.value = null
  form.parkId = parkOptions.value[0]?.value
  form.cabinetCode = ''
  form.cabinetName = ''
  form.coordX = 600
  form.coordY = 500
  form.slotCount = 4
  form.status = 'ACTIVE'
  modalOpen.value = true
}

function openEdit(record: AdminBatterySwapCabinet) {
  editing.value = record
  Object.assign(form, record)
  modalOpen.value = true
}

async function handleSave() {
  if (!form.parkId) return message.warning('请选择园区')
  saving.value = true
  try {
    if (editing.value) await infraApi.updateSwapCabinet(editing.value.id, form as infraApi.AdminBatterySwapCabinetUpsertPayload)
    else await infraApi.createSwapCabinet(form as infraApi.AdminBatterySwapCabinetUpsertPayload)
    message.success('已保存')
    modalOpen.value = false
    await loadData()
  } finally {
    saving.value = false
  }
}

async function remove(id: number) {
  await infraApi.deleteSwapCabinet(id)
  message.success('已删除')
  await loadData()
}

onMounted(loadData)
</script>
