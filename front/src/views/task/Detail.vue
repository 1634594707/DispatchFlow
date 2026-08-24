<template>
  <PageContainer :title="`任务详情：${store.detail?.taskNo || route.params.taskId}`">
    <template #actions>
      <router-link v-if="store.detail?.vehicleId && store.detail?.orderId" :to="geoTrackingLink">
        <a-button type="primary">地图追踪</a-button>
      </router-link>
      <a-button @click="router.back()">返回列表</a-button>
    </template>

    <a-spin :spinning="store.detailLoading">
      <template v-if="store.detail">
        <section class="detail-summary" aria-label="任务概要">
          <div class="detail-summary-primary">
            <span class="detail-summary-label">任务编号</span>
            <span class="detail-summary-code mono">{{ store.detail.taskNo }}</span>
          </div>
          <dl class="detail-summary-metrics">
            <div class="detail-summary-metric">
              <dt>当前状态</dt>
              <dd><StatusBadge :status="store.detail.status" type="task" /></dd>
            </div>
            <div class="detail-summary-metric">
              <dt>关联订单</dt>
              <dd>
                <router-link :to="`/orders/${store.detail.orderId}`" class="link">
                  {{ store.detail.orderId }}
                </router-link>
              </dd>
            </div>
            <div class="detail-summary-metric">
              <dt>执行车辆</dt>
              <dd>
                <router-link
                  v-if="store.detail.vehicleId"
                  :to="`/vehicles/${store.detail.vehicleId}`"
                  class="link"
                >
                  {{ vehicleDetail?.vehicleCode || store.detail.vehicleId }}
                </router-link>
                <span v-else class="text-secondary">待派车</span>
              </dd>
            </div>
            <div class="detail-summary-metric">
              <dt>派单类型</dt>
              <dd>
                <a-tag class="metadata-tag">
                  {{ store.detail.dispatchType === 'AUTO' ? '自动' : '手动' }}
                </a-tag>
              </dd>
            </div>
          </dl>
        </section>

        <div class="detail-sections">
          <section class="detail-section" aria-labelledby="task-information-heading">
            <div class="detail-section-heading">
              <div>
                <h2 id="task-information-heading">任务信息</h2>
                <p>订单、站点与异常上下文</p>
              </div>
            </div>
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
              <a-descriptions-item label="取货点">
                <span v-if="store.detail.pickupStationCode">
                  {{ store.detail.pickupStationCode }} · {{ store.detail.pickupPointName }}
                </span>
                <span v-else class="text-secondary">-</span>
              </a-descriptions-item>
              <a-descriptions-item label="送货点">
                <span v-if="store.detail.dropoffStationCode">
                  {{ store.detail.dropoffStationCode }} · {{ store.detail.dropoffPointName }}
                </span>
                <span v-else class="text-secondary">-</span>
              </a-descriptions-item>
              <a-descriptions-item label="派单类型">
                <a-tag class="metadata-tag">
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
          </section>

          <section class="detail-section" aria-labelledby="task-vehicle-heading">
            <div class="detail-section-heading">
              <div>
                <h2 id="task-vehicle-heading">车辆信息</h2>
                <p>在线、调度与回传状态</p>
              </div>
            </div>
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
                    :stroke-color="
                      vehicleDetail.batteryLevel < 20 ? 'var(--fsd-error)' : 'var(--fsd-success)'
                    "
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
          </section>

          <section class="detail-section" aria-labelledby="task-actions-heading">
            <div class="detail-section-heading">
              <div>
                <h2 id="task-actions-heading">任务操作</h2>
                <p>按当前状态执行派车、改派或取消</p>
              </div>
            </div>
            <div class="detail-actions">
              <a-button
                v-if="canAutoAssign"
                type="primary"
                :loading="actionLoading"
                @click="handleAutoAssign"
              >
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
          </section>

          <section class="detail-section" aria-labelledby="task-timeline-heading">
            <div class="detail-section-heading">
              <div>
                <h2 id="task-timeline-heading">任务统一时间线</h2>
                <p>创建、派车、回报、异常与终态</p>
              </div>
            </div>
            <p v-if="nextActionHint" class="next-action-hint">下一步动作：{{ nextActionHint }}</p>
            <a-timeline v-if="timelineEntries.length > 0">
              <a-timeline-item
                v-for="(entry, index) in timelineEntries"
                :key="index"
                :color="entry.exception ? 'var(--fsd-error)' : timelineColor(entry.eventType)"
              >
                <p>
                  {{ timelineEventTypeLabel(entry.eventType) }}
                  <a-tag v-if="entry.severity" class="severity-tag">{{ entry.severity }}</a-tag>
                </p>
                <p v-if="entry.beforeStatus || entry.afterStatus" class="mono text-secondary">
                  {{ entry.beforeStatus || '-' }} → {{ entry.afterStatus || '-' }}
                </p>
                <p class="text-secondary">
                  {{ sourceLabel(entry.source)
                  }}{{ entry.operatorName ? ' · ' + entry.operatorName : '' }}
                  <span v-if="entry.message"> · {{ entry.message }}</span>
                </p>
                <p v-if="entry.failReason" class="fail-reason">失败原因：{{ entry.failReason }}</p>
                <span class="mono text-secondary">{{ formatTime(entry.time) }}</span>
              </a-timeline-item>
            </a-timeline>
            <template v-else>
              <a-timeline v-if="operateLogs.length > 0">
                <a-timeline-item
                  v-for="log in operateLogs"
                  :key="log.id"
                  :color="logColor(log.operateType)"
                >
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
              <a-empty v-else description="暂无时间线数据" />
            </template>
          </section>

          <section class="detail-section" aria-labelledby="task-status-timeline-heading">
            <div class="detail-section-heading">
              <div>
                <h2 id="task-status-timeline-heading">任务状态节点</h2>
                <p>关键时间点摘要</p>
              </div>
            </div>
            <a-timeline>
              <a-timeline-item color="var(--fsd-text-tertiary)">
                <p>任务创建</p>
                <span class="mono text-secondary">{{ formatTime(store.detail.createdAt) }}</span>
              </a-timeline-item>
              <a-timeline-item v-if="store.detail.assignTime" color="var(--fsd-text-tertiary)">
                <p>已派单</p>
                <span class="mono text-secondary">{{ formatTime(store.detail.assignTime) }}</span>
              </a-timeline-item>
              <a-timeline-item v-if="store.detail.startTime" color="var(--fsd-accent)">
                <p>开始执行</p>
                <span class="mono text-secondary">{{ formatTime(store.detail.startTime) }}</span>
              </a-timeline-item>
              <a-timeline-item v-if="store.detail.finishTime" :color="finishColor">
                <p>{{ store.detail.status === 'SUCCESS' ? '执行完成' : '执行结束' }}</p>
                <span class="mono text-secondary">{{ formatTime(store.detail.finishTime) }}</span>
              </a-timeline-item>
            </a-timeline>
          </section>
        </div>
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
import { useParkScopeStore } from '@/stores/parkScope'
import { getVehicleDetail, queryVehicles } from '@/api/vehicle'
import { autoAssignTask, manualAssignTask, cancelTask, reassignTask } from '@/api/task'
import { fetchTaskOperateLogs } from '@/api/operateLog'
import { TaskStatus, DispatchStatus } from '@/constants/enums'
import { buildGeoTrackingLink } from '@/constants/parkDelivery'
import dayjs from 'dayjs'
import type { VehicleDetailResponse } from '@/types/vehicle'
import type { OperateLogItem } from '@/types/operateLog'

const router = useRouter()
const route = useRoute()
const store = useTaskStore()
const parkScope = useParkScopeStore()

const vehicleDetail = ref<VehicleDetailResponse | null>(null)
const operateLogs = ref<OperateLogItem[]>([])
const actionLoading = ref(false)
const vehiclesLoading = ref(false)
const assignModalOpen = ref(false)
const assignMode = ref<'manual' | 'reassign'>('manual')
const assignForm = reactive({ vehicleId: undefined as number | undefined, remark: '' })
const vehicleOptions = ref<{ label: string; value: number }[]>([])

const geoTrackingLink = computed(() =>
  buildGeoTrackingLink(store.detail?.orderId, store.detail?.vehicleId ?? undefined),
)

/** 统一时间线条目（路线图 3.2），来自 /admin/tasks/{id}/timeline 读取模型。 */
const timelineEntries = computed(() => store.timeline?.entries ?? [])

/** 下一步动作提示：按当前状态给出操作建议（路线图 3.2）。 */
const nextActionHint = computed(() => {
  const s = store.detail?.status
  if (s === TaskStatus.PENDING) return '等待派车，可执行自动派车或手动派车'
  if (s === TaskStatus.ASSIGNING) return '自动派车进行中，正在选择最优车辆'
  if (s === TaskStatus.MANUAL_PENDING) return '自动派单失败，请人工派车或取消任务'
  if (s === TaskStatus.ASSIGNED) return '已派车，等待车辆开始执行'
  if (s === TaskStatus.EXECUTING) return '配送执行中，等待车辆回报完成'
  if (s === TaskStatus.SUCCESS) return '任务已完成'
  if (s === TaskStatus.FAILED) return '任务失败，请排查原因后重新下单'
  if (s === TaskStatus.CANCELLED) return '任务已取消'
  return null
})

function timelineEventTypeLabel(type: string) {
  const map: Record<string, string> = {
    CREATE_ORDER: '订单创建',
    CREATE_TASK: '创建任务',
    AUTO_ASSIGN: '自动派车',
    MANUAL_ASSIGN: '手动派车',
    UNASSIGN_TASK: '人工接管退回',
    REASSIGN: '改派换车',
    CANCEL_TASK: '取消任务',
    START_EXECUTE: '开始执行',
    FINISH_SUCCESS: '配送完成',
    FINISH_FAILED: '配送失败',
    TASK_RETRY: '失败重试',
    RESET_FOR_AUTO_ASSIGN: '重置待派',
    ENTER_MANUAL_PENDING: '转人工处理',
    ISSUE_COMMAND: '下发车辆指令',
    COMMAND_FAILED: '车辆指令失败',
    EXCEPTION_RESOLVE: '异常处置',
    EXCEPTION_RAISED: '异常上报',
    EXCEPTION_RESOLVED: '异常解除',
  }
  return map[type] || type
}

function sourceLabel(source?: string | null) {
  const map: Record<string, string> = {
    SYSTEM: '系统',
    DISPATCHER: '调度员',
    VEHICLE: '车辆回报',
    MOBILE: '移动端',
  }
  if (!source) return '系统'
  return map[source] || source
}

function timelineColor(type: string) {
  if (type === 'FINISH_SUCCESS') return 'var(--fsd-success)'
  if (type === 'START_EXECUTE' || type === 'ISSUE_COMMAND' || type === 'EXCEPTION_RESOLVED') {
    return 'var(--fsd-accent)'
  }
  return 'var(--fsd-text-tertiary)'
}

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
  return (
    s === TaskStatus.PENDING ||
    s === TaskStatus.MANUAL_PENDING ||
    s === TaskStatus.ASSIGNED ||
    s === TaskStatus.EXECUTING
  )
})

const finishColor = computed(() =>
  store.detail?.status === TaskStatus.SUCCESS ? 'var(--fsd-success)' : 'var(--fsd-error)',
)

function formatTime(t: string | null | undefined) {
  return t ? dayjs(t).format('YYYY-MM-DD HH:mm:ss') : '-'
}

function operateTypeLabel(type: string) {
  const map: Record<string, string> = {
    CREATE_TASK: '创建任务',
    AUTO_ASSIGN: '自动派车',
    MANUAL_ASSIGN: '手动派车',
    REASSIGN: '改派',
    CANCEL_TASK: '取消任务',
    EXCEPTION_RESOLVE: '异常处置',
  }
  return map[type] || type
}

function logColor(type: string) {
  if (type.includes('FAIL') || type === 'CANCEL_TASK') return 'var(--fsd-error)'
  return 'var(--fsd-text-tertiary)'
}

async function loadVehicle(vehicleId: number) {
  try {
    vehicleDetail.value = (await getVehicleDetail(vehicleId, parkScope.selectedParkId)).data
  } catch {
    vehicleDetail.value = null
  }
}

async function loadOperateLogs(taskId: number) {
  try {
    operateLogs.value = (await fetchTaskOperateLogs(taskId, parkScope.selectedParkId)).data
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
      parkId: parkScope.selectedParkId,
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
    await autoAssignTask(taskId, parkScope.selectedParkId)
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
      await reassignTask(taskId, payload, parkScope.selectedParkId)
      message.success('改派成功')
    } else {
      await manualAssignTask(taskId, payload, parkScope.selectedParkId)
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
    await cancelTask(taskId, undefined, parkScope.selectedParkId)
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
  if (!store.detail) {
    operateLogs.value = []
    vehicleDetail.value = null
    return
  }
  await loadOperateLogs(id)
  if (store.detail?.vehicleId) {
    await loadVehicle(store.detail.vehicleId)
  } else {
    vehicleDetail.value = null
  }
}

onMounted(fetchData)
watch(() => route.params.taskId, fetchData)
watch(() => parkScope.selectedParkId, fetchData)
</script>

<style scoped lang="less">
@mobile-break: 767px;

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

.fail-reason {
  margin: var(--fsd-space-1) 0;
  color: var(--fsd-error);
}

.next-action-hint {
  margin: 0 0 var(--fsd-space-3);
  padding: var(--fsd-space-2) var(--fsd-space-3);
  border: 1px solid var(--fsd-accent-border);
  border-radius: var(--fsd-radius-sm);
  background: var(--fsd-accent-selected);
  color: var(--fsd-accent-strong);
  font-size: var(--fsd-text-sm);
}

.detail-actions {
  display: flex;
  gap: var(--fsd-space-2);
  flex-wrap: wrap;
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

.text-secondary {
  color: var(--fsd-text-secondary);
  font-size: var(--fsd-text-xs);
}

.metadata-tag {
  border: 1px solid var(--fsd-border);
  border-radius: var(--fsd-radius-sm);
  background: var(--fsd-neutral-bg);
  color: var(--fsd-text-secondary);
}

.severity-tag {
  margin-left: var(--fsd-space-1);
  border: 1px solid var(--fsd-warning);
  border-radius: var(--fsd-radius-sm);
  background: var(--fsd-warning-bg);
  color: var(--fsd-warning);
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

@media (max-width: @mobile-break) {
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

  .detail-actions {
    flex-direction: column;

    > * {
      width: 100%;
    }
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
