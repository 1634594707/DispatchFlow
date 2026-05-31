<template>
  <PageContainer title="路网管理" subtitle="管理路网节点与路段（施工维护时可禁用路段）">
    <template #actions>
      <a-select
        v-model:value="filterParkId"
        :options="parkOptions"
        placeholder="筛选园区"
        allow-clear
        style="width: 180px"
        @change="loadAll"
      />
    </template>

    <a-tabs v-model:activeKey="activeTab">
      <a-tab-pane key="nodes" tab="路网节点">
        <div class="tab-toolbar">
          <a-button type="primary" size="small" @click="openNodeCreate">
            <PlusOutlined /> 新建节点
          </a-button>
        </div>
        <a-table
          :columns="nodeColumns"
          :data-source="nodes"
          :loading="loadingNodes"
          row-key="id"
          :pagination="{ pageSize: 20 }"
          size="small"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'status'">
              <a-tag :color="record.status === 'ACTIVE' ? 'success' : 'default'">
                {{ record.status === 'ACTIVE' ? '启用' : '禁用' }}
              </a-tag>
            </template>
            <template v-else-if="column.key === 'coord'">
              ({{ record.coordX }}, {{ record.coordY }})
            </template>
            <template v-else-if="column.key === 'actions'">
              <a-button type="link" size="small" @click="openNodeEdit(record)">编辑</a-button>
            </template>
          </template>
        </a-table>
      </a-tab-pane>

      <a-tab-pane key="segments" tab="路网边">
        <div class="tab-toolbar">
          <a-button type="primary" size="small" @click="openSegmentCreate">
            <PlusOutlined /> 新建路段
          </a-button>
        </div>
        <a-table
          :columns="segmentColumns"
          :data-source="segments"
          :loading="loadingSegments"
          row-key="id"
          :pagination="{ pageSize: 20 }"
          size="small"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'status'">
              <a-tag :color="record.status === 'ACTIVE' ? 'success' : 'warning'">
                {{ record.status === 'ACTIVE' ? '启用' : '禁用' }}
              </a-tag>
            </template>
            <template v-else-if="column.key === 'route'">
              {{ record.fromNodeCode }} → {{ record.toNodeCode }}
            </template>
            <template v-else-if="column.key === 'actions'">
              <a-space>
                <a-button type="link" size="small" @click="openSegmentEdit(record)">编辑</a-button>
                <a-button type="link" size="small" @click="confirmToggleSegment(record)">
                  {{ record.status === 'ACTIVE' ? '禁用' : '启用' }}
                </a-button>
              </a-space>
            </template>
          </template>
        </a-table>
      </a-tab-pane>
    </a-tabs>

    <a-card v-if="filterParkId && mapPoints.length > 0" title="路网地图预览（可拖拽节点）" size="small" style="margin-top: 16px;">
      <ParkInfraPreview
        :park-id="filterParkId"
        :points="mapPoints"
        :segments="mapSegments"
        :height="320"
        pickable
        draggable
        @pick="onMapPick"
        @node-move="onNodeDrag"
      />
    </a-card>

    <a-modal
      v-model:open="nodeModalOpen"
      :title="editingNode ? '编辑节点' : '新建节点'"
      :confirm-loading="saving"
      width="480px"
      @ok="handleNodeSave"
    >
      <a-form layout="vertical">
        <a-form-item label="所属园区" required>
          <a-select v-model:value="nodeForm.parkId" :options="parkOptions" />
        </a-form-item>
        <a-form-item label="节点编码" required>
          <a-input v-model:value="nodeForm.nodeCode" placeholder="如 R1" />
        </a-form-item>
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="坐标 X" required>
              <a-input-number v-model:value="nodeForm.coordX" :min="0" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="坐标 Y" required>
              <a-input-number v-model:value="nodeForm.coordY" :min="0" style="width: 100%" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item label="状态">
          <a-select v-model:value="nodeForm.status" :options="roadStatusOptions" />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="segmentModalOpen"
      :title="editingSegment ? '编辑路段' : '新建路段'"
      :confirm-loading="saving"
      width="480px"
      @ok="handleSegmentSave"
    >
      <a-form layout="vertical">
        <a-form-item label="所属园区" required>
          <a-select v-model:value="segmentForm.parkId" :options="parkOptions" />
        </a-form-item>
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="起点节点" required>
              <a-input v-model:value="segmentForm.fromNodeCode" placeholder="如 R1" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="终点节点" required>
              <a-input v-model:value="segmentForm.toNodeCode" placeholder="如 R2" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item label="状态">
          <a-select v-model:value="segmentForm.status" :options="roadStatusOptions" />
        </a-form-item>
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="限速 km/h">
              <a-input-number v-model:value="segmentForm.speedLimitKmh" :min="5" :max="60" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="拥堵等级">
              <a-input-number v-model:value="segmentForm.congestionLevel" :min="0" :max="3" style="width: 100%" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item label="备注">
          <a-input v-model:value="segmentForm.remark" placeholder="施工/维护说明" />
        </a-form-item>
      </a-form>
    </a-modal>
  </PageContainer>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import ParkInfraPreview from '@/components/infrastructure/ParkInfraPreview.vue'
import { message, Modal } from 'ant-design-vue'
import * as trafficApi from '@/api/traffic'
import { PlusOutlined } from '@ant-design/icons-vue'
import PageContainer from '@/components/common/PageContainer.vue'
import { useParkOptions } from '@/composables/useParkOptions'
import * as infraApi from '@/api/infrastructure'
import type { AdminRoadNode, AdminRoadSegment } from '@/types/infrastructure'

const { parkOptions } = useParkOptions()
const activeTab = ref('nodes')
const filterParkId = ref<number | undefined>()
const loadingNodes = ref(false)
const loadingSegments = ref(false)
const saving = ref(false)
const nodes = ref<AdminRoadNode[]>([])
const segments = ref<AdminRoadSegment[]>([])
const nodeModalOpen = ref(false)
const segmentModalOpen = ref(false)
const editingNode = ref<AdminRoadNode | null>(null)
const editingSegment = ref<AdminRoadSegment | null>(null)

const mapPoints = computed(() =>
  nodes.value
    .filter((n) => !filterParkId.value || n.parkId === filterParkId.value)
    .map((n) => ({
      code: n.nodeCode,
      label: n.nodeCode,
      x: Number(n.coordX),
      y: Number(n.coordY),
      color: n.status === 'ACTIVE' ? '#58a6ff' : '#6e7681',
    }))
)

const mapSegments = computed(() =>
  segments.value
    .filter((s) => !filterParkId.value || s.parkId === filterParkId.value)
    .map((s) => ({
      from: s.fromNodeCode,
      to: s.toNodeCode,
      color: segmentCongestionColor(s.congestionLevel ?? 0),
    }))
)

function segmentCongestionColor(level: number) {
  if (level >= 3) return '#FF3D71'
  if (level === 2) return '#FFAA00'
  if (level === 1) return '#FFD666'
  return '#58a6ff'
}

const nodeForm = reactive({
  parkId: undefined as number | undefined,
  nodeCode: '',
  coordX: 0,
  coordY: 0,
  status: 'ACTIVE',
  remark: '',
})

const segmentForm = reactive({
  parkId: undefined as number | undefined,
  fromNodeCode: '',
  toNodeCode: '',
  status: 'ACTIVE',
  speedLimitKmh: 15,
  congestionLevel: 0,
  remark: '',
})

const nodeColumns = [
  { title: '编码', dataIndex: 'nodeCode', key: 'nodeCode', width: 90 },
  { title: '园区', dataIndex: 'parkName', key: 'parkName', width: 130 },
  { title: '坐标', key: 'coord', width: 140 },
  { title: '状态', key: 'status', width: 80 },
  { title: '操作', key: 'actions', width: 80 },
]

const segmentColumns = [
  { title: '路段', key: 'route' },
  { title: '园区', dataIndex: 'parkName', key: 'parkName', width: 130 },
  { title: '状态', key: 'status', width: 80 },
  { title: '备注', dataIndex: 'remark', key: 'remark', ellipsis: true },
  { title: '操作', key: 'actions', width: 120 },
]

const roadStatusOptions = [
  { label: '启用', value: 'ACTIVE' },
  { label: '禁用', value: 'DISABLED' },
]

async function loadNodes() {
  loadingNodes.value = true
  try {
    nodes.value = (await infraApi.fetchRoadNodes(filterParkId.value)).data
  } finally {
    loadingNodes.value = false
  }
}

async function loadSegments() {
  loadingSegments.value = true
  try {
    segments.value = (await infraApi.fetchRoadSegments(filterParkId.value)).data
  } finally {
    loadingSegments.value = false
  }
}

function loadAll() {
  loadNodes()
  loadSegments()
}

function openNodeCreate() {
  editingNode.value = null
  nodeForm.parkId = filterParkId.value ?? parkOptions.value[0]?.value
  nodeForm.nodeCode = ''
  nodeForm.coordX = 0
  nodeForm.coordY = 0
  nodeForm.status = 'ACTIVE'
  nodeForm.remark = ''
  nodeModalOpen.value = true
}

function openNodeEdit(record: AdminRoadNode) {
  editingNode.value = record
  nodeForm.parkId = record.parkId
  nodeForm.nodeCode = record.nodeCode
  nodeForm.coordX = Number(record.coordX)
  nodeForm.coordY = Number(record.coordY)
  nodeForm.status = record.status
  nodeForm.remark = record.remark ?? ''
  nodeModalOpen.value = true
}

async function handleNodeSave() {
  if (nodeForm.parkId == null || !nodeForm.nodeCode) {
    message.warning('请填写完整信息')
    return
  }
  const parkId = nodeForm.parkId
  saving.value = true
  try {
    const payload = {
      parkId,
      nodeCode: nodeForm.nodeCode,
      coordX: nodeForm.coordX,
      coordY: nodeForm.coordY,
      status: nodeForm.status,
      remark: nodeForm.remark || undefined,
    }
    if (editingNode.value) {
      await infraApi.updateRoadNode(editingNode.value.id, payload)
      message.success('节点已更新')
    } else {
      await infraApi.createRoadNode(payload)
      message.success('节点已创建')
    }
    nodeModalOpen.value = false
    await loadNodes()
  } finally {
    saving.value = false
  }
}

function openSegmentCreate() {
  editingSegment.value = null
  segmentForm.parkId = filterParkId.value ?? parkOptions.value[0]?.value
  segmentForm.fromNodeCode = ''
  segmentForm.toNodeCode = ''
  segmentForm.status = 'ACTIVE'
  segmentForm.speedLimitKmh = 15
  segmentForm.congestionLevel = 0
  segmentForm.remark = ''
  segmentModalOpen.value = true
}

function openSegmentEdit(record: AdminRoadSegment) {
  editingSegment.value = record
  segmentForm.parkId = record.parkId
  segmentForm.fromNodeCode = record.fromNodeCode
  segmentForm.toNodeCode = record.toNodeCode
  segmentForm.status = record.status
  segmentForm.speedLimitKmh = record.speedLimitKmh ?? 15
  segmentForm.congestionLevel = record.congestionLevel ?? 0
  segmentForm.remark = record.remark ?? ''
  segmentModalOpen.value = true
}

async function handleSegmentSave() {
  if (segmentForm.parkId == null || !segmentForm.fromNodeCode || !segmentForm.toNodeCode) {
    message.warning('请填写完整信息')
    return
  }
  const parkId = segmentForm.parkId
  saving.value = true
  try {
    const payload = {
      parkId,
      fromNodeCode: segmentForm.fromNodeCode,
      toNodeCode: segmentForm.toNodeCode,
      status: segmentForm.status,
      speedLimitKmh: segmentForm.speedLimitKmh,
      congestionLevel: segmentForm.congestionLevel,
      remark: segmentForm.remark || undefined,
    }
    if (editingSegment.value) {
      await infraApi.updateRoadSegment(editingSegment.value.id, payload)
      message.success('路段已更新')
    } else {
      await infraApi.createRoadSegment(payload)
      message.success('路段已创建')
    }
    segmentModalOpen.value = false
    await loadSegments()
  } finally {
    saving.value = false
  }
}

async function confirmToggleSegment(record: AdminRoadSegment) {
  if (record.status !== 'ACTIVE') {
    await handleToggleSegment(record.id)
    return
  }
  try {
    const impact = (await trafficApi.fetchSegmentImpact(record.id)).data
    const hints = (impact.alternativePathHints || []).map((h) => `· ${h}`).join('\n')
    Modal.confirm({
      title: '确认禁用路段',
      content: `影响进行中任务约 ${impact.affectedTaskCount} 个；可能不可达站点 ${impact.unreachableStationCount} 个。\n${hints}`,
      async onOk() {
        await handleToggleSegment(record.id)
      },
    })
  } catch {
    Modal.confirm({
      title: '确认禁用路段',
      content: '无法获取影响评估，仍要禁用该路段吗？',
      async onOk() {
        await handleToggleSegment(record.id)
      },
    })
  }
}

function onMapPick(x: number, y: number) {
  if (activeTab.value === 'nodes' && nodeModalOpen.value) {
    nodeForm.coordX = Math.round(x)
    nodeForm.coordY = Math.round(y)
    message.success(`已选坐标 (${nodeForm.coordX}, ${nodeForm.coordY})`)
  }
}

const dragSaveTimers = new Map<string, ReturnType<typeof setTimeout>>()

function onNodeDrag(code: string, x: number, y: number) {
  const node = nodes.value.find((n) => n.nodeCode === code)
  if (!node) return
  node.coordX = x
  node.coordY = y
  const existing = dragSaveTimers.get(code)
  if (existing) clearTimeout(existing)
  dragSaveTimers.set(code, setTimeout(async () => {
    try {
      await infraApi.updateRoadNode(node.id, {
        parkId: node.parkId,
        nodeCode: node.nodeCode,
        coordX: x,
        coordY: y,
        status: node.status,
        remark: node.remark,
      })
    } catch {
      message.error(`节点 ${code} 保存失败`)
    }
  }, 400))
}

async function handleToggleSegment(segmentId: number) {
  await infraApi.toggleRoadSegmentStatus(segmentId)
  message.success('路段状态已更新')
  await loadSegments()
}

onMounted(loadAll)
</script>

<style scoped lang="less">
.tab-toolbar {
  margin-bottom: 12px;
  display: flex;
  justify-content: flex-end;
}
</style>
