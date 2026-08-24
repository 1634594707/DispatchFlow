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
        <section class="detail-summary" aria-label="车辆概要">
          <div class="detail-summary-primary">
            <span class="detail-summary-label">车辆编号</span>
            <span class="detail-summary-code mono">{{ store.detail.vehicleCode }}</span>
          </div>
          <dl class="detail-summary-metrics">
            <div class="detail-summary-metric">
              <dt>在线状态</dt>
              <dd><StatusBadge :status="store.detail.onlineStatus" type="online" /></dd>
            </div>
            <div class="detail-summary-metric">
              <dt>调度状态</dt>
              <dd><StatusBadge :status="store.detail.dispatchStatus" type="dispatch" /></dd>
            </div>
            <div class="detail-summary-metric">
              <dt>当前电量</dt>
              <dd>{{ store.detail.batteryLevel }}%</dd>
            </div>
            <div class="detail-summary-metric">
              <dt>当前任务</dt>
              <dd>
                <router-link
                  v-if="store.detail.currentTaskId"
                  :to="`/tasks/${store.detail.currentTaskId}`"
                  class="link"
                >
                  任务#{{ store.detail.currentTaskId }}
                </router-link>
                <span v-else class="text-muted">-</span>
              </dd>
            </div>
          </dl>
        </section>

        <div class="detail-sections">
          <section class="detail-section" aria-labelledby="vehicle-information-heading">
            <div class="detail-section-heading">
              <div>
                <h2 id="vehicle-information-heading">基本信息</h2>
                <p>车辆标识、类型和接入配置</p>
              </div>
            </div>
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
              <a-descriptions-item label="连接模式">
                <a-tag class="metadata-tag">{{ store.detail.linkMode || 'SIM' }}</a-tag>
              </a-descriptions-item>
              <template v-if="store.detail.linkMode === 'VDA5050'">
                <a-descriptions-item label="Manufacturer">
                  <span class="mono">{{ store.detail.vdaManufacturer || '-' }}</span>
                </a-descriptions-item>
                <a-descriptions-item label="Serial Number">
                  <span class="mono">{{ store.detail.vdaSerialNumber || '-' }}</span>
                </a-descriptions-item>
                <a-descriptions-item label="MQTT Topic 前缀" :span="2">
                  <span class="mono">{{ vdaTopicPrefix(store.detail) }}</span>
                </a-descriptions-item>
              </template>
              <a-descriptions-item label="备注">
                {{ store.detail.remark || '-' }}
              </a-descriptions-item>
            </a-descriptions>
          </section>

          <section class="detail-section" aria-labelledby="vehicle-runtime-heading">
            <div class="detail-section-heading">
              <div>
                <h2 id="vehicle-runtime-heading">实时状态</h2>
                <p>连接、路线和任务执行上下文</p>
              </div>
            </div>
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
                  :stroke-color="
                    store.detail.batteryLevel < 20 ? 'var(--fsd-error)' : 'var(--fsd-success)'
                  "
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
              <a-descriptions-item label="最近道路节点">
                <span v-if="store.detail.currentRoadNodeCode" class="mono">{{
                  store.detail.currentRoadNodeCode
                }}</span>
                <span v-else class="text-secondary">未匹配（偏离道路）</span>
              </a-descriptions-item>
              <a-descriptions-item label="路线 ID">
                <span v-if="store.detail.routeAuditRouteId" class="mono">{{
                  store.detail.routeAuditRouteId
                }}</span>
                <span v-else class="text-secondary">-</span>
              </a-descriptions-item>
              <a-descriptions-item label="地图版本">
                <span v-if="store.detail.routeMapVersion" class="mono">{{
                  store.detail.routeMapVersion
                }}</span>
                <span v-else class="text-secondary">-</span>
              </a-descriptions-item>
              <a-descriptions-item label="偏航距离">
                <span
                  v-if="store.detail.routeDeviationMeters != null"
                  class="mono"
                  :class="{ 'text-error': store.detail.routeDeviationMeters > 50 }"
                  >{{ store.detail.routeDeviationMeters }} m</span
                >
                <span v-else class="text-secondary">-</span>
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
          </section>

          <section class="detail-section" aria-labelledby="vehicle-constraints-heading">
            <div class="detail-section-heading">
              <div>
                <h2 id="vehicle-constraints-heading">车辆尺寸与通行约束</h2>
                <p>尺寸、转弯和允许道路等级</p>
              </div>
            </div>
            <a-descriptions :column="2" size="small" bordered>
              <a-descriptions-item label="车辆宽度">
                <span v-if="store.detail.widthCm != null" class="mono"
                  >{{ store.detail.widthCm }} cm</span
                >
                <span v-else class="text-muted">-</span>
              </a-descriptions-item>
              <a-descriptions-item label="车辆长度">
                <span v-if="store.detail.lengthCm != null" class="mono"
                  >{{ store.detail.lengthCm }} cm</span
                >
                <span v-else class="text-muted">-</span>
              </a-descriptions-item>
              <a-descriptions-item label="最小转弯半径">
                <span v-if="store.detail.turningRadiusM != null" class="mono"
                  >{{ store.detail.turningRadiusM }} m</span
                >
                <span v-else class="text-muted">-</span>
              </a-descriptions-item>
              <a-descriptions-item label="允许道路等级">
                <template v-if="store.detail.allowedRoadClasses">
                  <a-tag
                    v-for="(cls, idx) in store.detail.allowedRoadClasses.split(',')"
                    :key="idx"
                    class="metadata-tag road-class-tag"
                    >{{ cls.trim() }}</a-tag
                  >
                </template>
                <span v-else class="text-muted">全部</span>
              </a-descriptions-item>
            </a-descriptions>
          </section>

          <section
            class="detail-section detail-section--tabs"
            aria-labelledby="vehicle-records-heading"
          >
            <div class="detail-section-heading">
              <div>
                <h2 id="vehicle-records-heading">关联记录与管理</h2>
                <p>操作日志、凭证、健康度、轨迹和维护记录</p>
              </div>
            </div>
            <a-tabs v-model:active-key="activeTab" class="detail-tabs">
              <a-tab-pane key="logs" tab="关联操作日志">
                <a-timeline v-if="operateLogs.length > 0">
                  <a-timeline-item v-for="log in operateLogs" :key="log.id">
                    <router-link :to="`/tasks/${log.taskId}`" class="link">{{
                      log.taskNo || log.taskId
                    }}</router-link>
                    · {{ log.operateType }} · {{ formatTime(log.createdAt) }}
                  </a-timeline-item>
                </a-timeline>
                <a-empty v-else description="暂无操作日志" />
              </a-tab-pane>

              <a-tab-pane v-if="authStore.isAdmin" key="credentials" tab="车端凭证">
                <div class="tab-toolbar">
                  <a-button size="small" type="primary" @click="handleCreateCredential"
                    >生成凭证</a-button
                  >
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
                    <a-tag :class="healthTagClass(health.healthLevel)">{{
                      health.healthLevel
                    }}</a-tag>
                    <p>
                      OPEN 异常 {{ health.openExceptionCount }} · 失败任务
                      {{ health.failedTaskCount }} · 维护 {{ health.maintenanceCount }}
                    </p>
                    <ul v-if="health.suggestions?.length">
                      <li v-for="(s, i) in health.suggestions" :key="i">{{ s }}</li>
                    </ul>
                  </div>
                </div>
                <a-empty v-else description="暂无健康数据" />
              </a-tab-pane>

              <a-tab-pane v-if="authStore.isOperator" key="trajectory" tab="轨迹回放">
                <TrajectoryReplayPanel :vehicle-id="Number(route.params.vehicleId)" />
                <div v-if="authStore.isAdmin" class="tab-toolbar tab-toolbar--spaced">
                  <a-button size="small" @click="exportTrajectory">导出 CSV（ADMIN）</a-button>
                </div>
              </a-tab-pane>

              <a-tab-pane v-if="authStore.isAdmin" key="maintenance" tab="维护记录">
                <div class="tab-toolbar">
                  <a-button size="small" type="primary" @click="maintenanceModalOpen = true"
                    >新增维护</a-button
                  >
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
          </section>
        </div>
      </template>
    </a-spin>

    <a-modal
      v-model:open="editModalOpen"
      title="编辑车辆"
      :confirm-loading="saving"
      @ok="handleSave"
    >
      <a-form layout="vertical">
        <a-form-item label="所属园区" required>
          <a-select
            v-model:value="form.parkId"
            :options="parkScope.parkOptions.filter((option) => option.value != null)"
            placeholder="请选择园区"
          />
        </a-form-item>
        <a-form-item label="车辆编码" required
          ><a-input v-model:value="form.vehicleCode"
        /></a-form-item>
        <a-form-item label="车辆名称" required
          ><a-input v-model:value="form.vehicleName"
        /></a-form-item>
        <a-form-item label="车辆类型"><a-input v-model:value="form.vehicleType" /></a-form-item>
        <a-form-item label="连接模式">
          <a-select v-model:value="form.linkMode" :options="linkModeOptions" />
        </a-form-item>
        <template v-if="form.linkMode === 'VDA5050'">
          <a-form-item label="Manufacturer" required>
            <a-input v-model:value="form.vdaManufacturer" placeholder="如 DispatchFlow" />
          </a-form-item>
          <a-form-item label="Serial Number" required>
            <a-input v-model:value="form.vdaSerialNumber" placeholder="如 AGV-001" />
          </a-form-item>
          <a-form-item label="Interface">
            <a-input v-model:value="form.vdaInterfaceName" placeholder="默认 uagv/v2" />
          </a-form-item>
        </template>
        <a-form-item label="备注"><a-textarea v-model:value="form.remark" :rows="2" /></a-form-item>
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="maintenanceModalOpen"
      title="新增维护记录"
      :confirm-loading="saving"
      @ok="handleCreateMaintenance"
    >
      <a-form layout="vertical">
        <a-form-item label="维护类型" required>
          <a-select v-model:value="maintForm.maintenanceType" :options="maintenanceTypeOptions" />
        </a-form-item>
        <a-form-item label="描述" required
          ><a-textarea v-model:value="maintForm.description" :rows="3"
        /></a-form-item>
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
import TrajectoryReplayPanel from '@/components/vehicle/TrajectoryReplayPanel.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import { useVehicleStore } from '@/stores/vehicle'
import { useAuthStore } from '@/stores/auth'
import { useParkScopeStore } from '@/stores/parkScope'
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
const parkScope = useParkScopeStore()

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
  parkId: undefined as number | undefined,
  vehicleCode: '',
  vehicleName: '',
  vehicleType: 'GENERAL',
  linkMode: 'SIM',
  vdaManufacturer: '',
  vdaSerialNumber: '',
  vdaInterfaceName: 'uagv/v2',
  remark: '',
})

const linkModeOptions = [
  { label: '仿真 SIM', value: 'SIM' },
  { label: '真实 REAL (HTTP 遥测)', value: 'REAL' },
  { label: 'VDA5050 MQTT', value: 'VDA5050' },
]

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

function vdaTopicPrefix(detail: {
  vdaInterfaceName?: string | null
  vdaManufacturer?: string | null
  vdaSerialNumber?: string | null
}) {
  const iface = detail.vdaInterfaceName || 'uagv/v2'
  const mfg = detail.vdaManufacturer || '?'
  const serial = detail.vdaSerialNumber || '?'
  return `${iface}/${mfg}/${serial}`
}

function formatTime(t: string) {
  return dayjs(t).format('YYYY-MM-DD HH:mm:ss')
}

function healthColor(level: string) {
  const map: Record<string, string> = {
    GOOD: 'var(--fsd-success)',
    FAIR: 'var(--fsd-warning)',
    POOR: 'var(--fsd-warning)',
    CRITICAL: 'var(--fsd-error)',
  }
  return map[level] || 'var(--fsd-text-tertiary)'
}

function healthTagClass(level: string) {
  if (level === 'GOOD') return 'health-tag health-tag--success'
  if (level === 'FAIR' || level === 'POOR') return 'health-tag health-tag--warning'
  if (level === 'CRITICAL') return 'health-tag health-tag--error'
  return 'health-tag health-tag--neutral'
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
  ctx.fillStyle = '#0E1116'
  ctx.fillRect(0, 0, width, height)
  const xs = points.map((p) => p.x)
  const ys = points.map((p) => p.y)
  const minX = Math.min(...xs)
  const maxX = Math.max(...xs)
  const minY = Math.min(...ys)
  const maxY = Math.max(...ys)
  const pad = 24
  const scale = Math.min(
    (width - pad * 2) / (maxX - minX || 1),
    (height - pad * 2) / (maxY - minY || 1),
  )
  const tx = (x: number) => pad + (x - minX) * scale
  const ty = (y: number) => pad + (y - minY) * scale
  ctx.strokeStyle = '#56B9C8'
  ctx.lineWidth = 2
  ctx.beginPath()
  points.forEach((p, i) => {
    if (i === 0) ctx.moveTo(tx(p.x), ty(p.y))
    else ctx.lineTo(tx(p.x), ty(p.y))
  })
  ctx.stroke()
  ctx.fillStyle = '#67C587'
  const last = points[points.length - 1]
  ctx.beginPath()
  ctx.arc(tx(last.x), ty(last.y), 5, 0, Math.PI * 2)
  ctx.fill()
}

async function exportTrajectory() {
  const vehicleId = Number(route.params.vehicleId)
  const points = (
    await fetchVehicleTrajectory(vehicleId, {
      source: 'history',
      from: dayjs().subtract(24, 'hour').toISOString(),
      to: dayjs().toISOString(),
    })
  ).data
  const rows = ['x,y,soc,ts', ...points.map((p) => `${p.x},${p.y},${p.soc ?? ''},${p.ts ?? ''}`)]
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
    fetchVehicleOperateLogs(vehicleId, parkScope.selectedParkId),
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
  form.parkId = store.detail.parkId ?? parkScope.selectedParkId
  form.vehicleCode = store.detail.vehicleCode
  form.vehicleName = store.detail.vehicleName
  form.vehicleType = store.detail.vehicleType
  form.linkMode = store.detail.linkMode || 'SIM'
  form.vdaManufacturer = store.detail.vdaManufacturer || ''
  form.vdaSerialNumber = store.detail.vdaSerialNumber || ''
  form.vdaInterfaceName = store.detail.vdaInterfaceName || 'uagv/v2'
  form.remark = store.detail.remark || ''
  editModalOpen.value = true
}

async function handleSave() {
  const vehicleId = Number(route.params.vehicleId)
  if (!form.parkId) {
    message.warning('请选择所属园区')
    return
  }
  const parkId = form.parkId
  if (form.linkMode === 'VDA5050' && (!form.vdaManufacturer || !form.vdaSerialNumber)) {
    message.warning('VDA5050 车辆须填写 manufacturer 与 serialNumber')
    return
  }
  saving.value = true
  try {
    await updateVehicle(vehicleId, { ...form, parkId })
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
    if (store.detail) {
      await loadExtras(id)
    } else {
      operateLogs.value = []
      credentials.value = []
      maintenanceRecords.value = []
      health.value = null
    }
  }
}

onMounted(fetchData)
watch(() => route.params.vehicleId, fetchData)
watch(() => parkScope.selectedParkId, fetchData)
watch(activeTab, (tab) => {
  if (tab === 'trajectory' && authStore.isAdmin) {
    loadTrajectory()
  }
})
</script>

<style scoped lang="less">
.detail-summary {
  display: flex;
  min-width: 0;
  align-items: stretch;
  gap: var(--fsd-space-6);
  padding: var(--fsd-space-4) var(--fsd-space-5);
  border: 1px solid var(--fsd-border);
  border-radius: var(--fsd-radius-md);
  background: var(--fsd-surface-status);
}

.detail-summary-primary {
  display: flex;
  min-width: 160px;
  flex-direction: column;
  justify-content: center;
  gap: var(--fsd-space-1);
}

.detail-summary-label,
.detail-summary-metric dt {
  color: var(--fsd-text-tertiary);
  font-size: var(--fsd-text-xs);
  line-height: var(--fsd-leading-normal);
}

.detail-summary-code {
  color: var(--fsd-text-heading);
  font-size: var(--fsd-text-lg);
  font-weight: var(--fsd-font-semibold);
  font-variant-numeric: tabular-nums;
}

.detail-summary-metrics {
  display: grid;
  min-width: 0;
  flex: 1;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin: 0;
}

.detail-summary-metric {
  min-width: 0;
  padding: 0 var(--fsd-space-4);
  border-left: 1px solid var(--fsd-border-split);
}

.detail-summary-metric dt,
.detail-summary-metric dd {
  margin: 0;
}

.detail-summary-metric dd {
  min-height: 22px;
  margin-top: var(--fsd-space-1);
  overflow: hidden;
  color: var(--fsd-text-primary);
  font-size: var(--fsd-text-sm);
  line-height: var(--fsd-leading-normal);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.detail-sections {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.detail-section {
  min-width: 0;
  padding: var(--fsd-space-5) 0;
  border-top: 1px solid var(--fsd-border);
}

.detail-section:first-child {
  border-top: 0;
  padding-top: 0;
}

.detail-section-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--fsd-space-3);
  margin-bottom: var(--fsd-space-3);
}

.detail-section-heading h2 {
  margin: 0;
  color: var(--fsd-text-primary);
  font-size: var(--fsd-text-md);
  font-weight: var(--fsd-font-semibold);
  line-height: var(--fsd-leading-snug);
}

.detail-section-heading p {
  margin: var(--fsd-space-1) 0 0;
  color: var(--fsd-text-tertiary);
  font-size: var(--fsd-text-xs);
  line-height: var(--fsd-leading-normal);
}

.detail-section--tabs {
  padding-bottom: 0;
}

.detail-tabs {
  min-width: 0;
}

.tab-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: var(--fsd-space-3);
}

.tab-toolbar--spaced {
  margin-top: var(--fsd-space-2);
}

.link {
  color: var(--fsd-accent);
  font-family: var(--fsd-font-mono);

  &:hover {
    color: var(--fsd-accent-strong);
    text-decoration: underline;
  }
}

.mono {
  font-family: var(--fsd-font-mono);
  font-size: var(--fsd-text-sm);
}

.text-muted,
.text-secondary {
  color: var(--fsd-text-tertiary);
}

.text-secondary {
  font-size: var(--fsd-text-xs);
}

.text-error {
  color: var(--fsd-error);
}

.metadata-tag,
.health-tag {
  border: 1px solid var(--fsd-border);
  border-radius: var(--fsd-radius-sm);
  background: var(--fsd-neutral-bg);
  color: var(--fsd-text-secondary);
}

.health-tag--success {
  border-color: var(--fsd-success);
  background: var(--fsd-success-bg);
  color: var(--fsd-success);
}

.health-tag--warning {
  border-color: var(--fsd-warning);
  background: var(--fsd-warning-bg);
  color: var(--fsd-warning);
}

.health-tag--error {
  border-color: var(--fsd-error);
  background: var(--fsd-error-bg);
  color: var(--fsd-error);
}

.road-class-tag {
  margin-bottom: var(--fsd-space-1);
}

.health-panel {
  display: flex;
  align-items: flex-start;
  gap: var(--fsd-space-6);
}

.health-meta ul {
  margin: var(--fsd-space-2) 0 0;
  padding-left: 18px;
  color: var(--fsd-text-secondary);
}

.trajectory-canvas {
  width: 100%;
  border: 1px solid var(--fsd-border);
  border-radius: var(--fsd-radius-md);
}

@media (max-width: 1023px) {
  .detail-summary {
    flex-direction: column;
    gap: var(--fsd-space-4);
  }

  .detail-summary-primary {
    min-width: 0;
  }
}

@media (max-width: 767px) {
  .detail-summary {
    padding: var(--fsd-space-4);
  }

  .detail-summary-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    border-top: 1px solid var(--fsd-border-split);
  }

  .detail-summary-metric {
    padding: var(--fsd-space-3) 0 0;
    border-left: 0;
  }

  .detail-summary-metric:nth-child(even) {
    padding-left: var(--fsd-space-3);
    border-left: 1px solid var(--fsd-border-split);
  }

  .detail-section {
    padding: var(--fsd-space-4) 0;
  }

  .health-panel {
    flex-direction: column;
    gap: var(--fsd-space-4);
  }

  :deep(.ant-descriptions) {
    .ant-descriptions-item {
      display: flex;
      flex-direction: column;
    }

    .ant-descriptions-item-label,
    .ant-descriptions-item-content {
      width: 100% !important;
    }

    .ant-descriptions-item-label {
      padding-bottom: 0;
    }
  }
}
</style>
