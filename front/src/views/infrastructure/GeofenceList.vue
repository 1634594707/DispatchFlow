<template>
  <PageContainer title="地理围栏" subtitle="管理园区 GCJ-02 多边形边界与禁入区">
    <template #actions>
      <a-space>
        <a-select
          v-model:value="filterParkId"
          :options="parkOptions"
          placeholder="选择园区"
          style="width: 200px"
          @change="loadData"
        />
        <a-button type="primary" :disabled="!filterParkId" @click="openCreate">
          <PlusOutlined /> 新建围栏
        </a-button>
      </a-space>
    </template>

    <a-table :columns="columns" :data-source="geofences" :loading="loading" row-key="id" :pagination="false">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'fenceType'">
          <a-tag :color="record.fenceType === 'RESTRICTED' ? 'red' : 'blue'">
            {{ record.fenceType === 'RESTRICTED' ? '禁入区' : '边界' }}
          </a-tag>
        </template>
        <template v-else-if="column.key === 'status'">
          <a-tag :color="record.status === 'ACTIVE' ? 'success' : 'default'">
            {{ record.status === 'ACTIVE' ? '启用' : '停用' }}
          </a-tag>
        </template>
        <template v-else-if="column.key === 'vertices'">
          {{ record.polygon?.length ?? 0 }} 点
        </template>
        <template v-else-if="column.key === 'actions'">
          <a-button type="link" size="small" @click="openEdit(record)">编辑</a-button>
          <a-popconfirm title="确认删除该围栏？" @confirm="handleDelete(record.id)">
            <a-button type="link" size="small" danger>删除</a-button>
          </a-popconfirm>
        </template>
      </template>
    </a-table>

    <a-modal
      v-model:open="modalOpen"
      :title="editing ? '编辑围栏' : '新建围栏'"
      :confirm-loading="saving"
      width="640px"
      @ok="handleSave"
    >
      <a-form layout="vertical">
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="围栏编码" required>
              <a-input v-model:value="form.fenceCode" :disabled="!!editing" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="围栏名称" required>
              <a-input v-model:value="form.fenceName" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="类型" required>
              <a-select v-model:value="form.fenceType" :options="typeOptions" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="状态">
              <a-select v-model:value="form.status" :options="statusOptions" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item label="多边形 JSON（[[lng,lat],...]）" required>
          <a-textarea v-model:value="form.polygonJson" :rows="6" placeholder="[[121.052,31.902],[121.072,31.902],...]" />
        </a-form-item>
        <a-button size="small" @click="fillDefaultRectangle">从叠石桥中心生成 2km 矩形</a-button>
        <a-form-item label="备注" style="margin-top: 12px">
          <a-input v-model:value="form.remark" />
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
import { createGeofence, deleteGeofence, fetchGeofences, fetchParks, updateGeofence } from '@/api/infrastructure'
import type { AdminGeofence } from '@/types/infrastructure'

const loading = ref(false)
const saving = ref(false)
const modalOpen = ref(false)
const editing = ref<AdminGeofence | null>(null)
const geofences = ref<AdminGeofence[]>([])
const parks = ref<{ id: number; parkName: string }[]>([])
const filterParkId = ref<number>()

const parkOptions = computed(() =>
  parks.value.map((park) => ({ label: park.parkName, value: park.id })),
)

const columns = [
  { title: '编码', dataIndex: 'fenceCode', key: 'fenceCode' },
  { title: '名称', dataIndex: 'fenceName', key: 'fenceName' },
  { title: '类型', key: 'fenceType' },
  { title: '顶点', key: 'vertices', width: 80 },
  { title: '状态', key: 'status', width: 90 },
  { title: '操作', key: 'actions', width: 140 },
]

const typeOptions = [
  { label: '边界（越界告警）', value: 'BOUNDARY' },
  { label: '禁入区（进入告警）', value: 'RESTRICTED' },
]

const statusOptions = [
  { label: '启用', value: 'ACTIVE' },
  { label: '停用', value: 'INACTIVE' },
]

const form = reactive({
  fenceCode: '',
  fenceName: '',
  fenceType: 'BOUNDARY',
  polygonJson: '',
  status: 'ACTIVE',
  remark: '',
})

async function loadParks() {
  const response = await fetchParks()
  parks.value = (response.data || []).map((park) => ({ id: park.id, parkName: park.parkName }))
  if (!filterParkId.value && parks.value.length > 0) {
    filterParkId.value = parks.value[0].id
  }
}

async function loadData() {
  if (!filterParkId.value) return
  loading.value = true
  try {
    const response = await fetchGeofences(filterParkId.value)
    geofences.value = response.data || []
  } finally {
    loading.value = false
  }
}

function resetForm() {
  form.fenceCode = ''
  form.fenceName = ''
  form.fenceType = 'BOUNDARY'
  form.polygonJson = ''
  form.status = 'ACTIVE'
  form.remark = ''
}

function openCreate() {
  editing.value = null
  resetForm()
  fillDefaultRectangle()
  modalOpen.value = true
}

function openEdit(record: AdminGeofence) {
  editing.value = record
  form.fenceCode = record.fenceCode
  form.fenceName = record.fenceName
  form.fenceType = record.fenceType
  form.polygonJson = JSON.stringify(record.polygon)
  form.status = record.status
  form.remark = record.remark || ''
  modalOpen.value = true
}

function fillDefaultRectangle() {
  form.polygonJson = JSON.stringify([
    [121.05228, 31.90245],
    [121.07228, 31.90245],
    [121.07228, 31.92245],
    [121.05228, 31.92245],
  ])
}

async function handleSave() {
  if (!filterParkId.value) return
  saving.value = true
  try {
    if (editing.value) {
      await updateGeofence(editing.value.id, {
        fenceName: form.fenceName,
        fenceType: form.fenceType,
        polygonJson: form.polygonJson,
        status: form.status,
        remark: form.remark,
      })
      message.success('围栏已更新')
    } else {
      await createGeofence({
        parkId: filterParkId.value,
        fenceCode: form.fenceCode,
        fenceName: form.fenceName,
        fenceType: form.fenceType,
        polygonJson: form.polygonJson,
        remark: form.remark,
      })
      message.success('围栏已创建')
    }
    modalOpen.value = false
    await loadData()
  } catch {
    message.error('保存失败，请检查 JSON 格式')
  } finally {
    saving.value = false
  }
}

async function handleDelete(id: number) {
  await deleteGeofence(id)
  message.success('已删除')
  await loadData()
}

onMounted(async () => {
  await loadParks()
  await loadData()
})
</script>
