<template>
  <PageContainer :title="`车辆详情：${store.detail?.vehicleCode || route.params.vehicleId}`">
    <template #actions>
      <a-space>
        <a-button v-if="authStore.isAdmin" @click="openEdit">编辑</a-button>
        <a-button @click="router.back()">返回列表</a-button>
      </a-space>
    </template>

    <a-spin :spinning="store.detailLoading">
      <template v-if="store.detail">
        <div class="vehicle-detail-grid">
          <a-card title="基本信息" size="small">
            <a-descriptions :column="2" size="small" bordered>
              <a-descriptions-item label="车辆编号">
                <span class="mono">{{ store.detail.vehicleCode }}</span>
              </a-descriptions-item>
              <a-descriptions-item label="车辆名称">
                {{ store.detail.vehicleName }}
              </a-descriptions-item>
              <a-descriptions-item label="车辆类型">
                <a-tag>{{ store.detail.vehicleType }}</a-tag>
              </a-descriptions-item>
              <a-descriptions-item label="备注">
                {{ store.detail.remark || '-' }}
              </a-descriptions-item>
            </a-descriptions>
          </a-card>

          <a-card title="实时状态" size="small">
            <a-descriptions :column="2" size="small" bordered>
              <a-descriptions-item label="在线状态">
                <StatusBadge :status="store.detail.onlineStatus" type="online" />
              </a-descriptions-item>
              <a-descriptions-item label="调度状态">
                <StatusBadge :status="store.detail.dispatchStatus" type="dispatch" />
              </a-descriptions-item>
              <a-descriptions-item label="电量">
                <a-progress
                  :percent="store.detail.batteryLevel"
                  :stroke-color="store.detail.batteryLevel < 20 ? '#FF3D71' : '#00E676'"
                  size="small"
                />
              </a-descriptions-item>
              <a-descriptions-item label="最后回传">
                <span class="mono">{{ formatTime(store.detail.lastReportTime) }}</span>
              </a-descriptions-item>
              <a-descriptions-item label="当前位置">
                <span class="mono">
                  {{ store.detail.currentLatitude }}, {{ store.detail.currentLongitude }}
                </span>
              </a-descriptions-item>
              <a-descriptions-item label="当前任务">
                <router-link
                  v-if="store.detail.currentTaskId"
                  :to="`/tasks/${store.detail.currentTaskId}`"
                  class="link"
                >
                  任务#{{ store.detail.currentTaskId }}
                </router-link>
                <span v-else class="text-muted">-</span>
              </a-descriptions-item>
            </a-descriptions>
          </a-card>
        </div>

        <a-tabs v-model:activeKey="activeTab" style="margin-top: 16px;">
          <a-tab-pane key="logs" tab="关联操作日志">
            <a-timeline v-if="operateLogs.length > 0">
              <a-timeline-item v-for="log in operateLogs" :key="log.id">
                <router-link :to="`/tasks/${log.taskId}`" class="link">{{ log.taskNo || log.taskId }}</router-link>
                · {{ log.operateType }} · {{ formatTime(log.createdAt) }}
              </a-timeline-item>
            </a-timeline>
            <a-empty v-else description="暂无操作日志" />
          </a-tab-pane>

          <a-tab-pane v-if="authStore.isAdmin" key="credentials" tab="车端凭证">
            <div class="tab-toolbar">
              <a-button size="small" type="primary" @click="handleCreateCredential">生成凭证</a-button>
            </div>
            <a-table
              size="small"
              row-key="id"
              :pagination="false"
              :data-source="credentials"
              :columns="credentialColumns"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'apiKey'">
                  <span class="mono">{{ record.apiKey }}</span>
                </template>
                <template v-else-if="column.key === 'actions'">
                  <a-popconfirm
                    v-if="record.status === 'ACTIVE'"
                    title="确定禁用该凭证？"
                    @confirm="handleDisableCredential(record.id)"
                  >
                    <a-button type="link" size="small" danger>禁用</a-button>
                  </a-popconfirm>
                </template>
              </template>
            </a-table>
          </a-tab-pane>

          <a-tab-pane v-if="authStore.isOperator" key="health" tab="健康度">
            <div v-if="health" class="health-panel">
              <a-progress
                type="circle"
                :percent="health.healthScore"
                :stroke-color="healthColor(health.healthLevel)"
              />
              <div class="health-meta">
                <a-tag :color="healthColor(health.healthLevel)">{{ health.healthLevel }}</a-tag>
                <p>OPEN 异常 {{ health.openExceptionCount }} · 失败任务 {{ health.failedTaskCount }} · 维护 {{ health.maintenanceCount }}</p>
                <ul v-if="health.suggestions?.length">
                  <li v-for="(s, i) in health.suggestions" :key="i">{{ s }}</li>
                </ul>
              </div>
            </div>
            <a-empty v-else description="暂无健康数据" />
          </a-tab-pane>

          <a-tab-pane v-if="authStore.isAdmin" key="trajectory" tab="轨迹回放">
            <div class="tab-toolbar">
              <a-button size="small" :loading="trajectoryLoading" @click="loadTrajectory">刷新轨迹</a-button>
              <a-button size="small" @click="exportTrajectory">导出 CSV</a-button>
            </div>
            <canvas ref="trajectoryCanvas" class="trajectory-canvas" height="320" />
            <p v-if="trajectoryPoints.length === 0" class="text-muted">暂无轨迹点（Redis 运行时缓存）</p>
          </a-tab-pane>

          <a-tab-pane v-if="authStore.isAdmin" key="maintenance" tab="维护记录">
            <div class="tab-toolbar">
              <a-button size="small" type="primary" @click="maintenanceModalOpen = true">新增维护</a-button>
            </div>
            <a-table
              size="small"
              row-key="id"
              :pagination="false"
              :data-source="maintenanceRecords"
              :columns="maintenanceColumns"
            />
          </a-tab-pane>
        </a-tabs>
      </template>
    </a-spin>

    <a-modal v-model:open="editModalOpen" title="编辑车辆" :confirm-loading="saving" @ok="handleSave">
      <a-form layout="vertical">
        <a-form-item label="车辆编码" required><a-input v-model:value="form.vehicleCode" /></a-form-item>
        <a-form-item label="车辆名称" required><a-input v-model:value="form.vehicleName" /></a-form-item>
        <a-form-item label="车辆类型"><a-input v-model:value="form.vehicleType" /></a-form-item>
        <a-form-item label="连接模式">
          <a-select v-model:value="form.linkMode" :options="[{ label: '仿真 SIM', value: 'SIM' }, { label: '真实 REAL', value: 'REAL' }]" />
        </a-form-item>
        <a-form-item label="备注"><a-textarea v-model:value="form.remark" :rows="2" /></a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:open="maintenanceModalOpen" title="新增维护记录" :confirm-loading="saving" @ok="handleCreateMaintenance">
      <a-form layout="vertical">
        <a-form-item label="维护类型" required>
          <a-select v-model:value="maintForm.maintenanceType" :options="maintenanceTypeOptions" />
        </a-form-item>
        <a-form-item label="描述" required><a-textarea v-model:value="maintForm.description" :rows="3" /></a-form-item>
        <a-form-item label="维护时间" required>
          <a-date-picker v-model:value="maintForm.maintenanceAt" show-time style="width: 100%" />
        </a-form-item>
      </a-form>
    </a-modal>
  </PageContainer>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import dayjs, { type Dayjs } from 'dayjs'
import PageContainer from '@/components/common/PageContainer.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import { useVehicleStore } from '@/stores/vehicle'
import { useAuthStore } from '@/stores/auth'
import { fetchVehicleOperateLogs } from '@/api/operateLog'
import {
  updateVehicle,
  fetchVehicleCredentials,
  createVehicleCredential,
  disableVehicleCredential,
  fetchVehicleMaintenance,
  createVehicleMaintenance,
  fetchVehicleHealth,
  fetchVehicleTrajectory,
  type VehicleCredential,
  type VehicleHealth,
  type VehicleMaintenanceRecord,
  type VehicleTrajectoryPoint,
} from '@/api/vehicle'
import type { OperateLogItem } from '@/types/operateLog'

const router = useRouter()
const route = useRoute()
const store = useVehicleStore()
const authStore = useAuthStore()

const activeTab = ref('logs')
const health = ref<VehicleHealth | null>(null)
const trajectoryPoints = ref<VehicleTrajectoryPoint[]>([])
const trajectoryLoading = ref(false)
const trajectoryCanvas = ref<HTMLCanvasElement | null>(null)
const operateLogs = ref<OperateLogItem[]>([])
const credentials = ref<VehicleCredential[]>([])
const maintenanceRecords = ref<VehicleMaintenanceRecord[]>([])
const editModalOpen = ref(false)
const maintenanceModalOpen = ref(false)
const saving = ref(false)

const form = reactive({
  vehicleCode: '',
  vehicleName: '',
  vehicleType: 'GENERAL',
  linkMode: 'SIM',
  remark: '',
})

const maintForm = reactive({
  maintenanceType: 'ROUTINE',
  description: '',
  maintenanceAt: dayjs() as Dayjs | null,
})

const credentialColumns = [
  { title: 'API Key', key: 'apiKey' },
  { title: '状态', dataIndex: 'status', key: 'status', width: 100 },
  { title: '操作', key: 'actions', width: 80 },
]

const maintenanceColumns = [
  { title: '类型', dataIndex: 'maintenanceType', key: 'maintenanceType', width: 100 },
  { title: '描述', dataIndex: 'description', key: 'description' },
  { title: '时间', dataIndex: 'maintenanceAt', key: 'maintenanceAt', width: 170 },
  { title: '操作人', dataIndex: 'operatorName', key: 'operatorName', width: 100 },
]

const maintenanceTypeOptions = [
  { label: '例行保养', value: 'ROUTINE' },
  { label: '故障维修', value: 'REPAIR' },
  { label: '巡检', value: 'INSPECTION' },
]

function formatTime(t: string) {
  return dayjs(t).format('YYYY-MM-DD HH:mm:ss')
}

function healthColor(level: string) {
  const map: Record<string, string> = { GOOD: '#00E676', FAIR: '#FFD666', POOR: '#FFAA00', CRITICAL: '#FF3D71' }
  return map[level] || '#58a6ff'
}

async function loadTrajectory() {
  const vehicleId = Number(route.params.vehicleId)
  trajectoryLoading.value = true
  try {
    trajectoryPoints.value = (await fetchVehicleTrajectory(vehicleId)).data
    drawTrajectory()
  } finally {
    trajectoryLoading.value = false
  }
}

function drawTrajectory() {
  const canvas = trajectoryCanvas.value
  const points = trajectoryPoints.value
  if (!canvas || points.length === 0) return
  const wrap = canvas.parentElement
  if (!wrap) return
  const width = wrap.clientWidth
  const height = 320
  const dpr = window.devicePixelRatio || 1
  canvas.width = width * dpr
  canvas.height = height * dpr
  canvas.style.width = `${width}px`
  canvas.style.height = `${height}px`
  const ctx = canvas.getContext('2d')
  if (!ctx) return
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
  ctx.fillStyle = '#0d1117'
  ctx.fillRect(0, 0, width, height)
  const xs = points.map((p) => p.x)
  const ys = points.map((p) => p.y)
  const minX = Math.min(...xs)
  const maxX = Math.max(...xs)
  const minY = Math.min(...ys)
  const maxY = Math.max(...ys)
  const pad = 24
  const scale = Math.min((width - pad * 2) / (maxX - minX || 1), (height - pad * 2) / (maxY - minY || 1))
  const tx = (x: number) => pad + (x - minX) * scale
  const ty = (y: number) => pad + (y - minY) * scale
  ctx.strokeStyle = '#00b4d8'
  ctx.lineWidth = 2
  ctx.beginPath()
  points.forEach((p, i) => {
    if (i === 0) ctx.moveTo(tx(p.x), ty(p.y))
    else ctx.lineTo(tx(p.x), ty(p.y))
  })
  ctx.stroke()
  ctx.fillStyle = '#00E676'
  const last = points[points.length - 1]
  ctx.beginPath()
  ctx.arc(tx(last.x), ty(last.y), 5, 0, Math.PI * 2)
  ctx.fill()
}

function exportTrajectory() {
  const rows = ['x,y,soc,ts', ...trajectoryPoints.value.map((p) => `${p.x},${p.y},${p.soc ?? ''},${p.ts ?? ''}`)]
  const blob = new Blob([rows.join('\n')], { type: 'text/csv' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `trajectory-${route.params.vehicleId}.csv`
  a.click()
  URL.revokeObjectURL(url)
}

async function loadExtras(vehicleId: number) {
  const [logsRes, credRes, maintRes, healthRes] = await Promise.allSettled([
    fetchVehicleOperateLogs(vehicleId),
    authStore.isAdmin ? fetchVehicleCredentials(vehicleId) : Promise.resolve({ data: [] }),
    authStore.isAdmin ? fetchVehicleMaintenance(vehicleId) : Promise.resolve({ data: [] }),
    authStore.isOperator ? fetchVehicleHealth(vehicleId) : Promise.resolve({ data: null }),
  ])
  operateLogs.value = logsRes.status === 'fulfilled' ? logsRes.value.data : []
  credentials.value = credRes.status === 'fulfilled' ? credRes.value.data : []
  maintenanceRecords.value = maintRes.status === 'fulfilled' ? maintRes.value.data : []
  health.value = healthRes.status === 'fulfilled' ? healthRes.value.data : null
  if (authStore.isAdmin && activeTab.value === 'trajectory') {
    await loadTrajectory()
  }
}

function openEdit() {
  if (!store.detail) return
  form.vehicleCode = store.detail.vehicleCode
  form.vehicleName = store.detail.vehicleName
  form.vehicleType = store.detail.vehicleType
  form.linkMode = 'SIM'
  form.remark = store.detail.remark || ''
  editModalOpen.value = true
}

async function handleSave() {
  const vehicleId = Number(route.params.vehicleId)
  saving.value = true
  try {
    await updateVehicle(vehicleId, { ...form })
    message.success('车辆已更新')
    editModalOpen.value = false
    await fetchData()
  } finally {
    saving.value = false
  }
}

async function handleCreateCredential() {
  const vehicleId = Number(route.params.vehicleId)
  await createVehicleCredential(vehicleId)
  message.success('凭证已生成')
  credentials.value = (await fetchVehicleCredentials(vehicleId)).data
}

async function handleDisableCredential(credentialId: number) {
  await disableVehicleCredential(credentialId)
  message.success('凭证已禁用')
  await loadExtras(Number(route.params.vehicleId))
}

async function handleCreateMaintenance() {
  const vehicleId = Number(route.params.vehicleId)
  if (!maintForm.description || !maintForm.maintenanceAt) {
    message.warning('请填写完整信息')
    return
  }
  saving.value = true
  try {
    await createVehicleMaintenance({
      vehicleId,
      maintenanceType: maintForm.maintenanceType,
      description: maintForm.description,
      maintenanceAt: maintForm.maintenanceAt.format('YYYY-MM-DD HH:mm:ss'),
    })
    message.success('维护记录已添加')
    maintenanceModalOpen.value = false
    maintenanceRecords.value = (await fetchVehicleMaintenance(vehicleId)).data
  } finally {
    saving.value = false
  }
}

async function fetchData() {
  const id = Number(route.params.vehicleId)
  if (id) {
    await store.fetchDetail(id)
    await loadExtras(id)
  }
}

onMounted(fetchData)
watch(() => route.params.vehicleId, fetchData)
watch(activeTab, (tab) => {
  if (tab === 'trajectory' && authStore.isAdmin) {
    loadTrajectory()
  }
})
</script>

<style scoped lang="less">
.vehicle-detail-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.tab-toolbar {
  margin-bottom: 12px;
  display: flex;
  justify-content: flex-end;
}

.link {
  color: var(--fsd-accent);
  font-family: 'JetBrains Mono', monospace;
}

.mono {
  font-family: 'JetBrains Mono', monospace;
  font-size: 13px;
}

.text-muted {
  color: var(--fsd-text-tertiary);
}

.health-panel {
  display: flex;
  gap: 24px;
  align-items: flex-start;
}

.health-meta ul {
  margin: 8px 0 0;
  padding-left: 18px;
  color: var(--fsd-text-secondary);
}

.trajectory-canvas {
  width: 100%;
  border-radius: 8px;
  border: 1px solid var(--fsd-border);
}
</style>
