<template>
  <PageContainer :title="`任务详情：${store.detail?.taskNo || route.params.taskId}`">
    <template #actions>
      <a-button @click="router.back()">返回列表</a-button>
    </template>

    <a-spin :spinning="store.detailLoading">
      <template v-if="store.detail">
        <div class="detail-grid">
          <a-card title="任务信息" size="small">
            <a-descriptions :column="2" size="small" bordered>
              <a-descriptions-item label="任务编号">
                <span class="mono">{{ store.detail.taskNo }}</span>
              </a-descriptions-item>
              <a-descriptions-item label="状态">
                <StatusBadge :status="store.detail.status" type="task" />
              </a-descriptions-item>
              <a-descriptions-item label="关联订单">
                <router-link :to="`/orders/${store.detail.orderId}`" class="link">
                  {{ store.detail.orderId }}
                </router-link>
              </a-descriptions-item>
              <a-descriptions-item label="派单类型">
                <a-tag :color="store.detail.dispatchType === 'AUTO' ? 'cyan' : 'orange'">
                  {{ store.detail.dispatchType === 'AUTO' ? '自动' : '手动' }}
                </a-tag>
              </a-descriptions-item>
              <a-descriptions-item label="人工介入">
                {{ store.detail.manualFlag === 1 ? '是' : '否' }}
              </a-descriptions-item>
              <a-descriptions-item label="重试次数">
                {{ store.detail.retryCount }}
              </a-descriptions-item>
              <a-descriptions-item label="失败原因" :span="2">
                {{ store.detail.failReasonMsg || '-' }}
              </a-descriptions-item>
              <a-descriptions-item label="备注" :span="2">
                {{ store.detail.remark || '-' }}
              </a-descriptions-item>
            </a-descriptions>
          </a-card>

          <a-card title="车辆信息" size="small">
            <template v-if="vehicleDetail">
              <a-descriptions :column="2" size="small" bordered>
                <a-descriptions-item label="车辆编号">
                  <router-link :to="`/vehicles/${vehicleDetail.vehicleId}`" class="link">
                    {{ vehicleDetail.vehicleCode }}
                  </router-link>
                </a-descriptions-item>
                <a-descriptions-item label="车辆名称">
                  {{ vehicleDetail.vehicleName }}
                </a-descriptions-item>
                <a-descriptions-item label="在线状态">
                  <StatusBadge :status="vehicleDetail.onlineStatus" type="online" />
                </a-descriptions-item>
                <a-descriptions-item label="调度状态">
                  <StatusBadge :status="vehicleDetail.dispatchStatus" type="dispatch" />
                </a-descriptions-item>
                <a-descriptions-item label="电量">
                  <a-progress
                    :percent="vehicleDetail.batteryLevel"
                    :stroke-color="vehicleDetail.batteryLevel < 20 ? '#FF3D71' : '#00E676'"
                    size="small"
                  />
                </a-descriptions-item>
                <a-descriptions-item label="最后回传">
                  <span class="mono">{{ formatTime(vehicleDetail.lastReportTime) }}</span>
                </a-descriptions-item>
              </a-descriptions>
            </template>
            <a-empty v-else-if="!store.detail.vehicleId" description="暂无关联车辆" />
            <a-spin v-else size="small" />
          </a-card>

          <a-card title="操作" size="small">
            <div class="detail-actions">
              <a-button v-if="canAutoAssign" type="primary" :loading="actionLoading" @click="handleAutoAssign">
                自动派车
              </a-button>
              <a-button v-if="canManualAssign" type="primary" ghost @click="openManualModal">
                手动派车
              </a-button>
              <a-button v-if="canReassign" type="primary" ghost @click="openReassignModal">
                改派
              </a-button>
              <a-popconfirm v-if="canCancel" title="确认取消该任务？" @confirm="handleCancel">
                <a-button danger :loading="actionLoading">取消任务</a-button>
              </a-popconfirm>
            </div>
          </a-card>
        </div>

        <a-card title="操作日志时间线" size="small" style="margin-bottom: 16px;">
          <a-timeline v-if="operateLogs.length > 0">
            <a-timeline-item v-for="log in operateLogs" :key="log.id" :color="logColor(log.operateType)">
              <p>{{ operateTypeLabel(log.operateType) }}</p>
              <p v-if="log.beforeStatus || log.afterStatus" class="mono text-secondary">
                {{ log.beforeStatus || '-' }} → {{ log.afterStatus || '-' }}
              </p>
              <p class="text-secondary">
                {{ log.operatorName || log.operatorType }}
                <span v-if="log.operateRemark"> · {{ log.operateRemark }}</span>
              </p>
              <span class="mono text-secondary">{{ formatTime(log.createdAt) }}</span>
            </a-timeline-item>
          </a-timeline>
          <a-empty v-else description="暂无操作日志" />
        </a-card>

        <a-card title="任务状态时间线" size="small">
          <a-timeline>
            <a-timeline-item color="green">
              <p>任务创建</p>
              <span class="mono text-secondary">{{ formatTime(store.detail.createdAt) }}</span>
            </a-timeline-item>
            <a-timeline-item v-if="store.detail.assignTime" color="blue">
              <p>已派单</p>
              <span class="mono text-secondary">{{ formatTime(store.detail.assignTime) }}</span>
            </a-timeline-item>
            <a-timeline-item v-if="store.detail.startTime" color="cyan">
              <p>开始执行</p>
              <span class="mono text-secondary">{{ formatTime(store.detail.startTime) }}</span>
            </a-timeline-item>
            <a-timeline-item v-if="store.detail.finishTime" :color="finishColor">
              <p>{{ store.detail.status === 'SUCCESS' ? '执行完成' : '执行结束' }}</p>
              <span class="mono text-secondary">{{ formatTime(store.detail.finishTime) }}</span>
            </a-timeline-item>
          </a-timeline>
        </a-card>
      </template>
    </a-spin>

    <a-modal
      v-model:open="assignModalOpen"
      :title="assignMode === 'reassign' ? '改派车辆' : '手动派车'"
      ok-text="确认"
      :confirm-loading="actionLoading"
      @ok="submitAssign"
    >
      <a-form layout="vertical">
        <a-form-item label="选择车辆" required>
          <a-select
            v-model:value="assignForm.vehicleId"
            placeholder="在线且空闲的车辆"
            show-search
            :loading="vehiclesLoading"
            :options="vehicleOptions"
          />
        </a-form-item>
        <a-form-item label="备注">
          <a-input v-model:value="assignForm.remark" />
        </a-form-item>
      </a-form>
    </a-modal>
  </PageContainer>
</template>

<script setup lang="ts">
import { ref, computed, reactive, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import PageContainer from '@/components/common/PageContainer.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import { useTaskStore } from '@/stores/task'
import { getVehicleDetail, queryVehicles } from '@/api/vehicle'
import { autoAssignTask, manualAssignTask, cancelTask, reassignTask } from '@/api/task'
import { fetchTaskOperateLogs } from '@/api/operateLog'
import { TaskStatus, DispatchStatus } from '@/constants/enums'
import dayjs from 'dayjs'
import type { VehicleDetailResponse } from '@/types/vehicle'
import type { OperateLogItem } from '@/types/operateLog'

const router = useRouter()
const route = useRoute()
const store = useTaskStore()

const vehicleDetail = ref<VehicleDetailResponse | null>(null)
const operateLogs = ref<OperateLogItem[]>([])
const actionLoading = ref(false)
const vehiclesLoading = ref(false)
const assignModalOpen = ref(false)
const assignMode = ref<'manual' | 'reassign'>('manual')
const assignForm = reactive({ vehicleId: undefined as number | undefined, remark: '' })
const vehicleOptions = ref<{ label: string; value: number }[]>([])

const canAutoAssign = computed(() => {
  const s = store.detail?.status
  return s === TaskStatus.PENDING
})

const canManualAssign = computed(() => {
  const s = store.detail?.status
  return s === TaskStatus.PENDING || s === TaskStatus.MANUAL_PENDING
})

const canReassign = computed(() => store.detail?.status === TaskStatus.ASSIGNED)
const canCancel = computed(() => {
  const s = store.detail?.status
  return s === TaskStatus.PENDING || s === TaskStatus.MANUAL_PENDING || s === TaskStatus.ASSIGNED
})

const finishColor = computed(() =>
  store.detail?.status === TaskStatus.SUCCESS ? 'green' : 'red'
)

function formatTime(t: string | null | undefined) {
  return t ? dayjs(t).format('YYYY-MM-DD HH:mm:ss') : '-'
}

function operateTypeLabel(type: string) {
  const map: Record<string, string> = {
    CREATE_TASK: '创建任务', AUTO_ASSIGN: '自动派车', MANUAL_ASSIGN: '手动派车',
    REASSIGN: '改派', CANCEL_TASK: '取消任务', EXCEPTION_RESOLVE: '异常处置',
  }
  return map[type] || type
}

function logColor(type: string) {
  if (type.includes('FAIL') || type === 'CANCEL_TASK') return 'red'
  if (type.includes('ASSIGN') || type === 'REASSIGN') return 'blue'
  return 'gray'
}

async function loadVehicle(vehicleId: number) {
  try {
    vehicleDetail.value = (await getVehicleDetail(vehicleId)).data
  } catch {
    vehicleDetail.value = null
  }
}

async function loadOperateLogs(taskId: number) {
  try {
    operateLogs.value = (await fetchTaskOperateLogs(taskId)).data
  } catch {
    operateLogs.value = []
  }
}

async function loadAssignableVehicles() {
  vehiclesLoading.value = true
  try {
    const res = await queryVehicles({
      onlineStatus: 'ONLINE' as any,
      dispatchStatus: DispatchStatus.IDLE,
      pageNo: 1,
      pageSize: 100,
    })
    vehicleOptions.value = (res.data.records || []).map((v) => ({
      label: `${v.vehicleCode} · ${v.batteryLevel}%`,
      value: v.vehicleId,
    }))
  } finally {
    vehiclesLoading.value = false
  }
}

async function handleAutoAssign() {
  const taskId = Number(route.params.taskId)
  actionLoading.value = true
  try {
    await autoAssignTask(taskId)
    message.success('自动派车已提交')
    await fetchData()
  } finally {
    actionLoading.value = false
  }
}

function openManualModal() {
  assignMode.value = 'manual'
  assignForm.vehicleId = undefined
  assignForm.remark = ''
  loadAssignableVehicles()
  assignModalOpen.value = true
}

function openReassignModal() {
  assignMode.value = 'reassign'
  assignForm.vehicleId = undefined
  assignForm.remark = ''
  loadAssignableVehicles()
  assignModalOpen.value = true
}

async function submitAssign() {
  if (!assignForm.vehicleId) {
    message.warning('请选择车辆')
    return
  }
  const taskId = Number(route.params.taskId)
  actionLoading.value = true
  try {
    const payload = { vehicleId: assignForm.vehicleId, remark: assignForm.remark }
    if (assignMode.value === 'reassign') {
      await reassignTask(taskId, payload)
      message.success('改派成功')
    } else {
      await manualAssignTask(taskId, payload)
      message.success('手动派车成功')
    }
    assignModalOpen.value = false
    await fetchData()
  } finally {
    actionLoading.value = false
  }
}

async function handleCancel() {
  const taskId = Number(route.params.taskId)
  actionLoading.value = true
  try {
    await cancelTask(taskId)
    message.success('任务已取消')
    await fetchData()
  } finally {
    actionLoading.value = false
  }
}

async function fetchData() {
  const id = Number(route.params.taskId)
  if (!id) return
  await store.fetchDetail(id)
  await loadOperateLogs(id)
  if (store.detail?.vehicleId) {
    await loadVehicle(store.detail.vehicleId)
  } else {
    vehicleDetail.value = null
  }
}

onMounted(fetchData)
watch(() => route.params.taskId, fetchData)
</script>

<style scoped lang="less">
.detail-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 16px;

  > :last-child {
    grid-column: 1 / -1;
  }
}

.detail-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.link {
  color: var(--fsd-accent);
  font-family: 'JetBrains Mono', monospace;

  &:hover {
    text-decoration: underline;
  }
}

.mono {
  font-family: 'JetBrains Mono', monospace;
  font-size: 13px;
}

.text-secondary {
  color: var(--fsd-text-secondary);
  font-size: 12px;
}
</style>
