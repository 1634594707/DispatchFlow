<template>
  <a-modal
    :open="open"
    title="创建短驳订单"
    width="640px"
    draggable
    root-class-name="park-delivery-modal"
    :confirm-loading="submitting"
    ok-text="提交并自动派车"
    cancel-text="取消"
    @ok="handleSubmit"
    @cancel="emit('update:open', false)"
  >
    <a-form layout="vertical">
      <a-form-item label="取货位置" required>
        <OrderEndpointInput
          v-model="pickupEndpoint"
          :groups="pickupGroups"
          title="取货点"
          test-id="admin-pickup"
          size="middle"
          :disabled="submitting"
          :loading-stations="loadingStations"
          station-placeholder="选择取货站点，或在地图上点一个坐标"
        />
      </a-form-item>
      <a-form-item label="送货位置" required>
        <OrderEndpointInput
          v-model="dropoffEndpoint"
          :groups="dropoffGroups"
          title="送货点"
          test-id="admin-dropoff"
          size="middle"
          default-mode="coord"
          :disabled="submitting"
          :loading-stations="loadingStations"
          station-placeholder="选择送货站点，或在地图上点一个坐标"
        />
      </a-form-item>
      <div
        v-if="rejection"
        class="reject-note"
        role="alert"
        data-testid="admin-order-rejection"
        :data-code="rejection.code"
      >
        <strong>{{ rejection.headline }}</strong>
        <div>{{ rejection.detail }}</div>
        <em>不会自动改成最近站点，请改位置或改用服务点。</em>
      </div>
      <a-form-item label="优先级">
        <a-select v-model:value="form.priority" :options="priorityOptions" />
      </a-form-item>
      <a-form-item label="外部单号">
        <a-input v-model:value="form.externalOrderNo" allow-clear placeholder="可选" />
      </a-form-item>
      <a-form-item label="备注">
        <a-textarea v-model:value="form.remark" :rows="2" :maxlength="120" show-count />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import OrderEndpointInput from '@/components/order/OrderEndpointInput.vue'
import { createParkOrder, getParkStations } from '@/api/park'
import {
  buildGroupedMobileStationOptions,
  filterMobileOrderStations,
  findMobileOrderStation,
  syncDefaultOrderStations,
} from '@/maps/stationLayers'
import {
  describeOrderRejection,
  endpointPayload,
  isCompleteEndpoint,
} from '@/constants/orderEndpoints'
import type { OrderRejection } from '@/constants/orderEndpoints'
import { createIdempotencyKey } from '@/composables/useMobileOrderForm'
import type { ParkOrderCreateRequest, ParkOrderEndpoint, ParkStation } from '@/types/park'

const props = defineProps<{
  open: boolean
  parkId?: number
  prefill?: { pickupStationId: number; dropoffStationId: number } | null
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  created: []
}>()

const submitting = ref(false)
const loadingStations = ref(false)
const stations = ref<ParkStation[]>([])
const pickupEndpoint = ref<ParkOrderEndpoint | null>(null)
const dropoffEndpoint = ref<ParkOrderEndpoint | null>(null)
const rejection = ref<OrderRejection | null>(null)

const form = reactive<Omit<ParkOrderCreateRequest, 'pickupStationId' | 'dropoffStationId'>>({
  idempotencyKey: createIdempotencyKey(),
  parkId: undefined,
  externalOrderNo: '',
  priority: 'P1',
  remark: '',
})

watch(
  () => props.open,
  (opening) => {
    if (opening && props.prefill) {
      pickupEndpoint.value = { kind: 'station', stationId: props.prefill.pickupStationId }
      dropoffEndpoint.value = { kind: 'station', stationId: props.prefill.dropoffStationId }
    } else if (!opening) {
      // 关闭时重置表单，但不重置 prefill
    }
  },
)

const priorityOptions = [
  { value: 'P0', label: 'P0 · 最高' },
  { value: 'P1', label: 'P1 · 优先' },
  { value: 'P2', label: 'P2 · 标准' },
  { value: 'P3', label: 'P3 · 低' },
]

const orderableStations = computed(() => filterMobileOrderStations(stations.value))

const pickupGroups = computed(() => buildGroupedMobileStationOptions(orderableStations.value))

const dropoffGroups = computed(() =>
  buildGroupedMobileStationOptions(orderableStations.value, {
    excludeStationId: pickupEndpoint.value?.kind === 'station' ? pickupEndpoint.value.stationId : null,
  }),
)

function applyDefaultStations() {
  const synced = syncDefaultOrderStations(stations.value, 'geo', {
    pickupStationId: pickupEndpoint.value?.kind === 'station' ? pickupEndpoint.value.stationId : undefined,
    dropoffStationId: dropoffEndpoint.value?.kind === 'station' ? dropoffEndpoint.value.stationId : undefined,
  })
  if (!synced.pickupStationId || !synced.dropoffStationId) return
  if (pickupEndpoint.value == null || pickupEndpoint.value.kind === 'station') {
    pickupEndpoint.value = { kind: 'station', stationId: synced.pickupStationId }
  }
  if (dropoffEndpoint.value == null || dropoffEndpoint.value.kind === 'station') {
    dropoffEndpoint.value = { kind: 'station', stationId: synced.dropoffStationId }
  }
}

async function loadStations() {
  loadingStations.value = true
  try {
    const res = await getParkStations(props.parkId)
    stations.value = res.data || []
    form.parkId = props.parkId ?? stations.value[0]?.parkId
    applyDefaultStations()
  } catch {
    message.error('加载站点失败')
  } finally {
    loadingStations.value = false
  }
}

async function handleSubmit() {
  rejection.value = null
  if (!isCompleteEndpoint(pickupEndpoint.value)) {
    message.warning('请选一个取货位置（服务点或地图坐标）')
    return
  }
  if (!isCompleteEndpoint(dropoffEndpoint.value)) {
    message.warning('请选一个送货位置（服务点或地图坐标）')
    return
  }
  if (
    pickupEndpoint.value?.kind === 'station' &&
    dropoffEndpoint.value?.kind === 'station' &&
    pickupEndpoint.value.stationId === dropoffEndpoint.value.stationId
  ) {
    message.warning('取货点和送货点不能相同')
    return
  }
  submitting.value = true
  try {
    const res = await createParkOrder({
      ...form,
      ...endpointPayload('pickup', pickupEndpoint.value),
      ...endpointPayload('dropoff', dropoffEndpoint.value),
    })
    if (res.data?.replayed) {
      message.success('重复提交已拦截：返回原订单 ' + (res.data?.orderNo || ''))
    } else {
      message.success(res.data?.message || `订单 ${res.data?.orderNo} 已创建`)
    }
    form.idempotencyKey = createIdempotencyKey()
    emit('created')
    emit('update:open', false)
  } catch (e: unknown) {
    rejection.value = describeOrderRejection(e)
  } finally {
    submitting.value = false
  }
}

watch(
  () => props.open,
  (open) => {
    if (open) {
      pickupEndpoint.value = null
      dropoffEndpoint.value = null
      rejection.value = null
      loadStations()
    }
  },
)
</script>

<style scoped>
.reject-note {
  margin-bottom: 16px;
  padding: 8px 10px;
  border: 1px solid #ffccc7;
  border-left: 3px solid #cf1322;
  border-radius: 4px;
  background: #fff2f0;
}
.reject-note strong {
  color: #cf1322;
  font-size: 13px;
}
.reject-note div {
  margin-top: 2px;
  color: rgb(0 0 0 / 65%);
  font-size: 12px;
  word-break: break-all;
}
.reject-note em {
  color: rgb(0 0 0 / 45%);
  font-size: 11px;
  font-style: normal;
}

/* V5-M3: 移动端下单弹窗自适应 — 底部抽屉可拖拽 */
@media (max-width: 768px) {
  .park-delivery-modal :deep(.ant-modal) {
    max-width: 100vw;
    margin: 0;
    top: auto;
    bottom: 0;
    padding-bottom: 0;
    transform-origin: bottom center;
  }
  .park-delivery-modal :deep(.ant-modal-content) {
    border-radius: var(--fsd-radius-lg) var(--fsd-radius-lg) 0 0;
    max-height: 90vh;
    overflow-y: auto;
    box-shadow: var(--fsd-shadow-popover);
  }
  .park-delivery-modal :deep(.ant-modal-header) {
    cursor: grab;
    padding: 14px 16px 8px;
    user-select: none;
    -webkit-user-select: none;
    touch-action: none;
  }
  .park-delivery-modal :deep(.ant-modal-header)::before {
    content: '';
    display: block;
    width: 36px;
    height: 4px;
    border-radius: var(--fsd-radius-sm);
    background: var(--fsd-border);
    margin: 0 auto 10px;
  }
  .park-delivery-modal :deep(.ant-modal-body) {
    padding: 8px 16px 24px;
  }
  .park-delivery-modal :deep(.ant-select) {
    width: 100% !important;
  }
}
</style>
