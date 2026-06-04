<template>
  <section class="quick-order-panel">
    <div class="panel-head">
      <div>
        <span class="panel-title">一键下单</span>
        <span class="panel-sub">{{ demoRoutes.length }} 条典型短驳线路</span>
      </div>
      <span v-if="parkLocked" class="park-lock">{{ parkName }}</span>
    </div>

    <div class="mode-switch" role="tablist" aria-label="下单地图模式">
      <button
        type="button"
        class="mode-chip"
        :class="{ active: orderMode === 'schematic' }"
        @click="$emit('update:orderMode', 'schematic')"
      >
        园区内部
      </button>
      <button
        type="button"
        class="mode-chip"
        :class="{ active: orderMode === 'geo' }"
        @click="$emit('update:orderMode', 'geo')"
      >
        真实地图
      </button>
    </div>

    <div class="route-cards">
      <button
        v-for="route in demoRoutes"
        :key="route.label"
        type="button"
        class="route-card"
        :disabled="submitting"
        @click="$emit('submitDemo', route)"
      >
        <span class="route-label">{{ route.label }}</span>
        <span class="route-codes">{{ route.pickupCode }} → {{ route.dropoffCode }}</span>
        <span class="route-action">{{ submitting ? '提交中…' : '立即下单' }}</span>
      </button>
    </div>

    <details class="advanced-block" :open="advancedOpen">
      <summary class="advanced-summary" @click.prevent="advancedOpen = !advancedOpen">
        <span>更多选项 · 自定义取送货</span>
        <span class="chevron" :class="{ open: advancedOpen }">›</span>
      </summary>

      <div v-show="advancedOpen" class="advanced-body">
        <a-form layout="vertical">
          <a-form-item v-if="!parkLocked" label="所属园区">
            <a-select
              :value="parkId"
              placeholder="选择园区"
              size="large"
              :loading="loadingParks"
              :options="parkOptions"
              @update:value="onParkChange"
            />
          </a-form-item>

          <a-form-item label="取货站点">
            <a-select
              :value="pickupStationId"
              placeholder="选择取货站点"
              size="large"
              :loading="loadingStations"
              show-search
              option-filter-prop="label"
              @update:value="onPickupChange"
            >
              <a-select-opt-group v-for="group in pickupGroups" :key="group.label" :label="group.label">
                <a-select-option v-for="opt in group.options" :key="opt.value" :value="opt.value" :label="opt.label">
                  {{ opt.label }}
                </a-select-option>
              </a-select-opt-group>
            </a-select>
          </a-form-item>

          <a-form-item label="送货站点">
            <a-select
              :value="dropoffStationId"
              placeholder="选择送货站点"
              size="large"
              :loading="loadingStations"
              show-search
              option-filter-prop="label"
              @update:value="onDropoffChange"
            >
              <a-select-opt-group v-for="group in dropoffGroups" :key="group.label" :label="group.label">
                <a-select-option v-for="opt in group.options" :key="opt.value" :value="opt.value" :label="opt.label">
                  {{ opt.label }}
                </a-select-option>
              </a-select-opt-group>
            </a-select>
          </a-form-item>

          <a-form-item label="优先级">
            <div class="priority-row">
              <button
                v-for="item in priorities"
                :key="item.value"
                type="button"
                class="priority-chip"
                :class="{ active: priority === item.value }"
                @click="$emit('update:priority', item.value)"
              >
                {{ item.value }}
              </button>
            </div>
          </a-form-item>

          <a-form-item label="备注">
            <a-textarea
              :value="remark"
              :rows="2"
              :maxlength="120"
              placeholder="可选备注"
              @update:value="onRemarkChange"
            />
          </a-form-item>

          <div class="route-preview">
            <span>{{ pickupPreview }}</span>
            <span class="arrow">→</span>
            <span>{{ dropoffPreview }}</span>
          </div>

          <button type="button" class="submit-btn" :disabled="submitting" @click="$emit('submitCustom')">
            {{ submitting ? '提交中…' : '提交自定义订单' }}
          </button>
        </a-form>
      </div>
    </details>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import type { MobileOrderMode } from '@/constants/parkDelivery'
import {
  buildGroupedMobileStationOptions,
  orderableStationsForMode,
} from '@/maps/stationLayers'
import type { ParkStation } from '@/types/park'

export interface DemoRoutePreset {
  label: string
  pickupCode: string
  dropoffCode: string
}

const props = defineProps<{
  stations: ParkStation[]
  submitting: boolean
  parkLocked: boolean
  parkName: string
  parkId?: number
  orderMode: MobileOrderMode
  demoRoutes: readonly DemoRoutePreset[]
  pickupStationId?: number
  dropoffStationId?: number
  priority: string
  remark: string
  loadingParks?: boolean
  loadingStations?: boolean
  parkOptions?: { value: number; label: string }[]
}>()

const emit = defineEmits<{
  submitDemo: [route: DemoRoutePreset]
  submitCustom: []
  'update:parkId': [value: number]
  'update:orderMode': [value: MobileOrderMode]
  'update:pickupStationId': [value: number]
  'update:dropoffStationId': [value: number]
  'update:priority': [value: string]
  'update:remark': [value: string]
}>()

function onParkChange(value: number) {
  emit('update:parkId', value)
}

function onPickupChange(value: number) {
  emit('update:pickupStationId', value)
}

function onDropoffChange(value: number) {
  emit('update:dropoffStationId', value)
}

function onRemarkChange(value: string) {
  emit('update:remark', value)
}

const advancedOpen = ref(false)

const orderableStations = computed(() => orderableStationsForMode(props.stations, props.orderMode))

const pickupGroups = computed(() =>
  buildGroupedMobileStationOptions(orderableStations.value, { mode: props.orderMode }),
)

const dropoffGroups = computed(() =>
  buildGroupedMobileStationOptions(orderableStations.value, {
    mode: props.orderMode,
    excludeStationId: props.pickupStationId ?? null,
  }),
)

const priorities = [
  { value: 'P0', label: '最高' },
  { value: 'P1', label: '优先' },
  { value: 'P2', label: '标准' },
  { value: 'P3', label: '低' },
]

const pickupPreview = computed(() => {
  const station = orderableStations.value.find(item => item.stationId === props.pickupStationId)
  return station?.stationCode ?? '--'
})

const dropoffPreview = computed(() => {
  const station = orderableStations.value.find(item => item.stationId === props.dropoffStationId)
  return station?.stationCode ?? '--'
})
</script>

<style scoped lang="less">
.quick-order-panel {
  padding: 16px;
  border-radius: 22px;
  border: 1px solid rgba(62, 166, 255, 0.12);
  background: rgba(6, 12, 22, 0.78);
  box-shadow: 0 16px 40px rgba(0, 0, 0, 0.24);
}

.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 14px;
}

.panel-title {
  display: block;
  font-size: 16px;
  font-weight: 800;
  color: #f4f8fc;
}

.panel-sub {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  color: #6f88a2;
}

.park-lock {
  flex-shrink: 0;
  padding: 5px 10px;
  border-radius: 999px;
  background: rgba(0, 180, 216, 0.1);
  border: 1px solid rgba(0, 180, 216, 0.22);
  color: #74c2ff;
  font-size: 11px;
  font-weight: 700;
}

.mode-switch {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  margin-bottom: 14px;
}

.mode-chip {
  height: 40px;
  border-radius: 12px;
  border: 1px solid rgba(62, 166, 255, 0.14);
  background: rgba(4, 8, 16, 0.55);
  color: #8fb4d9;
  font-size: 13px;
  font-weight: 800;
}

.mode-chip.active {
  border-color: rgba(0, 180, 216, 0.35);
  background: rgba(0, 180, 216, 0.12);
  color: #74c2ff;
}

.route-cards {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.route-card {
  display: grid;
  grid-template-columns: 1fr auto;
  grid-template-rows: auto auto;
  gap: 4px 12px;
  padding: 14px 16px;
  border-radius: 16px;
  border: 1px solid rgba(62, 166, 255, 0.12);
  background:
    linear-gradient(135deg, rgba(62, 166, 255, 0.08), rgba(6, 12, 22, 0.4));
  text-align: left;
  transition: border-color 0.15s, transform 0.15s;
}

.route-card:active:not(:disabled) {
  transform: scale(0.99);
  border-color: rgba(0, 180, 216, 0.35);
}

.route-card:disabled {
  opacity: 0.6;
}

.route-label {
  grid-column: 1;
  font-size: 15px;
  font-weight: 800;
  color: #eaf2fb;
}

.route-codes {
  grid-column: 1;
  font-family: 'JetBrains Mono', monospace;
  font-size: 11px;
  color: #6f88a2;
}

.route-action {
  grid-column: 2;
  grid-row: 1 / span 2;
  align-self: center;
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(0, 180, 216, 0.14);
  color: #74c2ff;
  font-size: 12px;
  font-weight: 800;
  white-space: nowrap;
}

.advanced-block {
  margin-top: 16px;
  border-top: 1px solid rgba(62, 166, 255, 0.08);
  padding-top: 12px;
}

.advanced-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  list-style: none;
  cursor: pointer;
  font-size: 13px;
  font-weight: 700;
  color: #8fb4d9;
  user-select: none;

  &::-webkit-details-marker {
    display: none;
  }
}

.chevron {
  display: inline-block;
  font-size: 18px;
  transform: rotate(90deg);
  transition: transform 0.2s;
  color: #58b6ff;
}

.chevron.open {
  transform: rotate(-90deg);
}

.advanced-body {
  margin-top: 14px;
}

.priority-row {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.priority-chip {
  min-width: 44px;
  height: 36px;
  padding: 0 12px;
  border-radius: 12px;
  border: 1px solid rgba(62, 166, 255, 0.12);
  background: rgba(6, 12, 22, 0.5);
  color: #8fb4d9;
  font-family: 'JetBrains Mono', monospace;
  font-size: 13px;
  font-weight: 700;
}

.priority-chip.active {
  border-color: rgba(0, 180, 216, 0.35);
  background: rgba(0, 180, 216, 0.12);
  color: #74c2ff;
}

.route-preview {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
  padding: 12px 14px;
  border-radius: 14px;
  background: rgba(4, 8, 16, 0.55);
  font-family: 'JetBrains Mono', monospace;
  font-size: 16px;
  font-weight: 800;
  color: #eaf2fb;
}

.arrow {
  color: #ffb703;
}

.submit-btn {
  width: 100%;
  height: 48px;
  border: 1px solid rgba(0, 180, 216, 0.28);
  border-radius: 16px;
  background: linear-gradient(135deg, rgba(0, 180, 216, 0.22), rgba(62, 166, 255, 0.1));
  color: #b8dcff;
  font-size: 15px;
  font-weight: 800;
}

.submit-btn:disabled {
  opacity: 0.55;
}

:deep(.ant-form-item-label > label) {
  color: #9ab0c6;
}

:deep(.ant-select-selector),
:deep(.ant-input),
:deep(.ant-input-affix-wrapper) {
  background: rgba(6, 12, 22, 0.6) !important;
  border-color: rgba(62, 166, 255, 0.14) !important;
  color: #d8e4f2 !important;
}

:deep(.ant-select-selection-placeholder),
:deep(.ant-input::placeholder) {
  color: #5a7a9a !important;
}
</style>
