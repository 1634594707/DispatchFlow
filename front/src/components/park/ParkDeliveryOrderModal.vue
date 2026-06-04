<template>
  <a-modal
    :open="open"
    title="创建短驳订单"
    width="640px"
    :confirm-loading="submitting"
    ok-text="提交并自动派车"
    cancel-text="取消"
    @ok="handleSubmit"
    @cancel="emit('update:open', false)"
  >
    <a-form layout="vertical">
      <a-form-item label="取货站点" required>
        <a-select
          v-model:value="form.pickupStationId"
          placeholder="选择取货站点"
          show-search
          option-filter-prop="label"
          :loading="loadingStations"
          :options="pickupOptions"
        />
      </a-form-item>
      <a-form-item label="送货站点" required>
        <a-select
          v-model:value="form.dropoffStationId"
          placeholder="选择送货站点"
          show-search
          option-filter-prop="label"
          :loading="loadingStations"
          :options="dropoffOptions"
        />
      </a-form-item>
      <a-form-item label="典型线路（一键填充）">
        <a-space wrap>
          <a-button
            v-for="preset in parkDeliveryDemoRoutes"
            :key="preset.label"
            size="small"
            @click="applyDemoRoute(preset.pickupCode, preset.dropoffCode)"
          >
            {{ preset.label }}
          </a-button>
        </a-space>
      </a-form-item>
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
import { createParkOrder, getParkStations } from '@/api/park'
import {
  buildGroupedMobileStationOptions,
  filterMobileOrderStations,
  findMobileOrderStation,
  syncDefaultMobileOrderStations,
} from '@/maps/stationLayers'
import { parkDeliveryDemoRoutes } from '@/constants/parkDelivery'
import type { ParkOrderCreateRequest, ParkStation } from '@/types/park'

const props = defineProps<{
  open: boolean
  parkId?: number
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  created: []
}>()

const submitting = ref(false)
const loadingStations = ref(false)
const stations = ref<ParkStation[]>([])

const form = reactive<ParkOrderCreateRequest>({
  parkId: undefined,
  externalOrderNo: '',
  pickupStationId: undefined as unknown as number,
  dropoffStationId: undefined as unknown as number,
  priority: 'P1',
  remark: '',
})

const priorityOptions = [
  { value: 'P0', label: 'P0 · 最高' },
  { value: 'P1', label: 'P1 · 优先' },
  { value: 'P2', label: 'P2 · 标准' },
  { value: 'P3', label: 'P3 · 低' },
]

const orderableStations = computed(() => filterMobileOrderStations(stations.value))

const pickupOptions = computed(() =>
  buildGroupedMobileStationOptions(orderableStations.value).flatMap(group =>
    group.options.map(option => ({
      value: option.value,
      label: `${group.label} · ${option.label}`,
    })),
  ),
)

const dropoffOptions = computed(() =>
  buildGroupedMobileStationOptions(orderableStations.value, {
    excludeStationId: form.pickupStationId ?? null,
  }).flatMap(group =>
    group.options.map(option => ({
      value: option.value,
      label: `${group.label} · ${option.label}`,
    })),
  ),
)

function applyDefaultStations() {
  const synced = syncDefaultMobileOrderStations(stations.value, {
    pickupStationId: form.pickupStationId,
    dropoffStationId: form.dropoffStationId,
  })
  if (synced.pickupStationId) form.pickupStationId = synced.pickupStationId
  if (synced.dropoffStationId) form.dropoffStationId = synced.dropoffStationId
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

function applyDemoRoute(pickupCode: string, dropoffCode: string) {
  const pickup = findMobileOrderStation(stations.value, { stationCode: pickupCode })
  const dropoff = findMobileOrderStation(stations.value, { stationCode: dropoffCode })
  if (!pickup || !dropoff) {
    message.warning('演示站点尚未加载，请稍后重试')
    return
  }
  form.pickupStationId = pickup.stationId
  form.dropoffStationId = dropoff.stationId
}

async function handleSubmit() {
  if (!form.pickupStationId || !form.dropoffStationId) {
    message.warning('请选择取货与送货站点')
    return
  }
  const pickup = findMobileOrderStation(stations.value, { stationId: form.pickupStationId })
  const dropoff = findMobileOrderStation(stations.value, { stationId: form.dropoffStationId })
  if (!pickup || !dropoff) {
    applyDefaultStations()
    message.warning('站点已失效，已重置为默认可下单站点，请确认后重试')
    return
  }
  submitting.value = true
  try {
    const res = await createParkOrder({ ...form })
    message.success(res.data?.message || `订单 ${res.data?.orderNo} 已创建`)
    emit('created')
    emit('update:open', false)
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '创建失败')
  } finally {
    submitting.value = false
  }
}

watch(
  () => props.open,
  open => {
    if (open) {
      form.pickupStationId = undefined as unknown as number
      form.dropoffStationId = undefined as unknown as number
      loadStations()
    }
  },
)
</script>
