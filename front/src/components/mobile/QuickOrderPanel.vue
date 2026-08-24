<template>
  <section
    class="quick-order-panel"
    :class="{ 'quick-order-panel--tracking-visible': hasTrackedOrder }"
  >
    <header class="order-header">
      <div class="header-titles">
        <span class="order-kicker">同城即时送</span>
        <h2 class="order-title">送货下单</h2>
        <p class="order-subtitle">叠石桥家纺城短驳配送</p>
      </div>
      <span v-if="parkLocked" class="park-tag">{{ parkName }}</span>
    </header>

    <div class="service-strip">
      <span><ThunderboltOutlined />即时派车</span>
      <span><EnvironmentOutlined />服务位交接</span>
      <span><InboxOutlined />一件代发</span>
    </div>

    <section class="order-section">
      <div class="section-head">
        <span class="section-title">取送地址</span>
        <button
          type="button"
          class="recommend-btn"
          :disabled="submitting"
          @click="$emit('quickFill')"
        >
          <ThunderboltOutlined />推荐线路
        </button>
      </div>

      <a-form layout="vertical" class="order-form">
        <a-form-item v-if="!parkLocked" label="所属园区">
          <a-select
            :value="parkId"
            placeholder="选择园区"
            size="large"
            popup-class-name="mobile-order-select-dropdown"
            :loading="loadingParks"
            :options="parkOptions"
            @update:value="onParkChange"
          />
        </a-form-item>

        <div class="address-stack">
          <span class="address-rail"
            ><i class="pickup-dot" /><i class="rail-line" /><i class="dropoff-dot"
          /></span>
          <div class="address-fields">
            <a-form-item label="从哪里取货">
              <a-select
                :value="pickupStationId"
                placeholder="选择取货服务点"
                size="large"
                popup-class-name="mobile-order-select-dropdown"
                :loading="loadingStations"
                show-search
                option-filter-prop="label"
                @update:value="onPickupChange"
              >
                <a-select-opt-group
                  v-for="group in pickupGroups"
                  :key="group.label"
                  :label="group.label"
                >
                  <a-select-option
                    v-for="opt in group.options"
                    :key="opt.value"
                    :value="opt.value"
                    :label="opt.label"
                  >
                    {{ opt.label }}
                  </a-select-option>
                </a-select-opt-group>
              </a-select>
            </a-form-item>

            <a-form-item label="送到哪里">
              <a-select
                :value="dropoffStationId"
                placeholder="选择送货服务点"
                size="large"
                popup-class-name="mobile-order-select-dropdown"
                :loading="loadingStations"
                show-search
                option-filter-prop="label"
                @update:value="onDropoffChange"
              >
                <a-select-opt-group
                  v-for="group in dropoffGroups"
                  :key="group.label"
                  :label="group.label"
                >
                  <a-select-option
                    v-for="opt in group.options"
                    :key="opt.value"
                    :value="opt.value"
                    :label="opt.label"
                  >
                    {{ opt.label }}
                  </a-select-option>
                </a-select-opt-group>
              </a-select>
            </a-form-item>
          </div>
          <button type="button" class="swap-btn" title="交换取送货点" @click="swapStations">
            <SwapOutlined />
          </button>
        </div>
      </a-form>

      <div class="route-preview">
        <span class="preview-station">{{ pickupPreview }}</span>
        <span class="arrow">→</span>
        <span class="preview-station">{{ dropoffPreview }}</span>
      </div>

      <div class="spec-block">
        <div class="spec-head">
          <span class="spec-label">
            <InboxOutlined class="spec-icon" />
            货物重量
          </span>
          <span class="spec-value">{{ weight ? `${weight} kg` : '请选择' }}</span>
        </div>
        <div class="weight-selector">
          <button
            v-for="preset in weightPresets"
            :key="preset.value"
            type="button"
            class="weight-chip"
            :class="{ active: isPresetActive(preset.value) }"
            @click="selectWeight(preset.value)"
          >
            {{ preset.label }}
          </button>
          <button
            type="button"
            class="weight-chip weight-chip-custom"
            :class="{ active: customWeightActive }"
            @click="enableCustomWeight"
          >
            自定义
          </button>
        </div>
        <a-input-number
          v-show="customWeightActive"
          class="weight-input"
          :value="weight"
          :min="0"
          :step="0.5"
          :precision="2"
          placeholder="输入重量 kg，如 1.5"
          size="large"
          style="width: 100%"
          @update:value="onWeightChange"
        />
      </div>

      <div class="spec-block">
        <div class="spec-head">
          <span class="spec-label">订单备注</span>
        </div>
        <a-textarea
          :value="remark"
          :rows="2"
          :maxlength="120"
          placeholder="选填，如易碎品、加急、代发要求等"
          @update:value="onRemarkChange"
        />
      </div>
    </section>

    <section v-if="demoRoutes.length" class="order-section common-routes">
      <div class="section-head">
        <span class="section-title">常用路线</span>
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
          <span class="route-card-main">
            <strong class="route-label">{{ route.label }}</strong>
            <span class="route-codes">{{ route.pickupCode }} → {{ route.dropoffCode }}</span>
          </span>
          <span class="route-action">一键下单</span>
        </button>
      </div>
    </section>

    <footer class="submit-bar">
      <div class="submit-summary">
        <span class="summary-route">{{ pickupPreview }} → {{ dropoffPreview }}</span>
        <span v-if="weight" class="summary-weight">配重 {{ weight }}kg</span>
      </div>
      <button
        type="button"
        class="submit-btn"
        :disabled="submitting"
        @click="$emit('submitCustom')"
      >
        {{ submitting ? '正在叫车…' : '确认下单' }}
      </button>
    </footer>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import {
  EnvironmentOutlined,
  InboxOutlined,
  SwapOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons-vue'
import type { MobileOrderMode } from '@/constants/parkDelivery'
import { buildGroupedMobileStationOptions, orderableStationsForMode } from '@/maps/stationLayers'
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
  orderPriority: 'HIGH' | 'NORMAL' | 'LOW'
  weight?: number | null
  remark: string
  loadingParks?: boolean
  loadingStations?: boolean
  hasTrackedOrder?: boolean
  parkOptions?: { value: number; label: string }[]
}>()

const emit = defineEmits<{
  submitDemo: [route: DemoRoutePreset]
  submitCustom: []
  quickFill: []
  'update:parkId': [value: number]
  'update:pickupStationId': [value: number]
  'update:dropoffStationId': [value: number]
  'update:weight': [value: number | undefined]
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

function swapStations() {
  if (props.pickupStationId == null || props.dropoffStationId == null) return
  emit('update:pickupStationId', props.dropoffStationId)
  emit('update:dropoffStationId', props.pickupStationId)
}

function onRemarkChange(value: string) {
  emit('update:remark', value)
}

function onWeightChange(value: number | null) {
  emit('update:weight', value == null ? undefined : value)
}

// 配重档位预设（电商规格选择器风格）
const weightPresets = [
  { value: 1, label: '1kg' },
  { value: 5, label: '5kg' },
  { value: 10, label: '10kg' },
  { value: 20, label: '20kg' },
] as const

// 自定义重量模式：用户主动点击"自定义"时启用，或当前重量不在预设档位中时自动激活
const customWeightMode = ref(false)

function isPresetActive(val: number) {
  return props.weight === val && !customWeightMode.value
}

const customWeightActive = computed(() => {
  if (customWeightMode.value) return true
  return props.weight != null && !weightPresets.some((p) => p.value === props.weight)
})

function selectWeight(val: number) {
  customWeightMode.value = false
  emit('update:weight', val)
}

function enableCustomWeight() {
  customWeightMode.value = true
  // 切换到自定义时，若当前是预设值则清空，便于用户重新输入
  if (props.weight != null && weightPresets.some((p) => p.value === props.weight)) {
    emit('update:weight', undefined)
  }
}

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

const pickupPreview = computed(() => {
  const station = orderableStations.value.find((item) => item.stationId === props.pickupStationId)
  return station?.stationCode ?? '--'
})

const dropoffPreview = computed(() => {
  const station = orderableStations.value.find((item) => item.stationId === props.dropoffStationId)
  return station?.stationCode ?? '--'
})
</script>

<style scoped lang="less">
.quick-order-panel {
  padding: var(--fsd-space-4) var(--fsd-space-4) 0;
  border: 0;
  border-radius: 0;
  background: var(--fsd-bg-base);
  position: relative;

  &::before {
    display: none;
  }
}

/* ── 顶部下单引导区 ── */
.order-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 14px;
  position: relative;
}

.order-kicker {
  display: block;
  margin-bottom: 4px;
  color: var(--fsd-accent);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0;
}

.header-titles {
  min-width: 0;
}

.order-title {
  margin: 0;
  font-family: var(--fsd-font-display);
  font-size: var(--fsd-text-xl);
  font-weight: var(--fsd-font-bold);
  color: var(--fsd-text-heading);
  letter-spacing: 0;
  line-height: var(--fsd-leading-tight);
}

.order-subtitle {
  margin: 4px 0 0;
  font-size: var(--fsd-text-xs);
  color: var(--fsd-text-tertiary);
  letter-spacing: 0;
}

.park-tag {
  flex-shrink: 0;
  padding: 4px 10px;
  border-radius: 4px;
  background: var(--fsd-accent-bg);
  border: 1px solid var(--fsd-accent-border);
  color: var(--fsd-accent);
  font-size: 10px;
  font-weight: var(--fsd-font-semibold);
  font-family: var(--fsd-font-mono);
  letter-spacing: 0;
}

.service-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 1px;
  margin: 0 calc(-1 * var(--fsd-space-4)) var(--fsd-space-4);
  padding: var(--fsd-space-2) var(--fsd-space-4);
  border-block: 1px solid var(--fsd-border-split);
  background: var(--fsd-bg-hover);

  span {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 5px;
    min-width: 0;
    color: var(--fsd-text-secondary);
    font-size: 10px;
    white-space: nowrap;
  }

  :deep(.anticon) {
    color: var(--fsd-accent);
    font-size: 12px;
  }
}

/* ── 模式切换 ── */
.mode-switch {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
  margin-bottom: 12px;
  padding: 3px;
  border-radius: var(--fsd-radius-md);
  background: var(--fsd-bg-deep);
  border: 1px solid var(--fsd-border);
  position: relative;
}

.mode-chip {
  min-height: var(--fsd-touch-target-min);
  border: 1px solid transparent;
  border-radius: var(--fsd-radius-sm);
  background: transparent;
  color: var(--fsd-text-secondary);
  font-size: 12px;
  font-weight: var(--fsd-font-semibold);
  letter-spacing: -0.005em;
  transition:
    background-color var(--fsd-transition-base),
    border-color var(--fsd-transition-base),
    color var(--fsd-transition-base);

  &:hover:not(.active) {
    color: var(--fsd-text-primary);
  }
}

.mode-chip.active {
  border-color: var(--fsd-accent-border);
  background: var(--fsd-accent-selected);
  color: var(--fsd-accent-strong);
}

/* ── 快速下单按钮 ── */
.quick-fill-btn {
  width: 100%;
  min-height: var(--fsd-touch-target-min);
  margin-bottom: var(--fsd-space-4);
  border: 1px solid var(--fsd-accent-border);
  border-radius: var(--fsd-radius-sm);
  background: var(--fsd-accent-selected);
  color: var(--fsd-accent-strong);
  font-size: 13px;
  font-weight: var(--fsd-font-semibold);
  letter-spacing: -0.005em;
  cursor: pointer;
  transition:
    background-color var(--fsd-transition-base),
    border-color var(--fsd-transition-base),
    color var(--fsd-transition-base);

  &:hover:not(:disabled) {
    border-color: var(--fsd-border-active);
    background: var(--fsd-bg-hover);
  }

  &:active:not(:disabled) {
    background: var(--fsd-bg-active);
  }

  &:disabled {
    opacity: 0.55;
    cursor: not-allowed;
  }
}

/* ── 区块通用样式 ── */
.order-section {
  margin-bottom: 16px;
  position: relative;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 10px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--fsd-border-split);
}

.section-title {
  font-family: var(--fsd-font-display);
  font-size: var(--fsd-text-base);
  font-weight: var(--fsd-font-semibold);
  color: var(--fsd-text-primary);
  letter-spacing: 0;

  &::before {
    content: '';
    display: inline-block;
    width: 3px;
    height: 12px;
    margin-right: 8px;
    vertical-align: -1px;
    border-radius: 2px;
    background: var(--fsd-accent);
  }
}

.recommend-btn {
  display: inline-flex;
  align-items: center;
  gap: var(--fsd-space-1);
  min-height: var(--fsd-touch-target-min);
  padding: 0 9px;
  border: 1px solid var(--fsd-border);
  border-radius: var(--fsd-radius-sm);
  background: var(--fsd-bg-hover);
  color: var(--fsd-accent-strong);
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;

  &:disabled {
    opacity: 0.55;
    cursor: not-allowed;
  }
}

.address-stack {
  position: relative;
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr) var(--fsd-touch-target-min);
  gap: var(--fsd-space-2);
  align-items: center;
  padding: var(--fsd-space-3) var(--fsd-space-2);
  border-block: 1px solid var(--fsd-border);
  background: transparent;
}

.address-fields {
  min-width: 0;

  :deep(.ant-form-item:last-child) {
    margin-bottom: 0;
  }
}

.address-rail {
  align-self: stretch;
  display: grid;
  grid-template-rows: 12px 1fr 12px;
  justify-items: center;
  padding: 28px 0 22px;
}

.pickup-dot,
.dropoff-dot {
  width: 9px;
  height: 9px;
  border-radius: var(--fsd-radius-full);
}

.pickup-dot {
  background: var(--fsd-text-tertiary);
}

.dropoff-dot {
  background: var(--fsd-accent);
}

.rail-line {
  width: 1px;
  min-height: 42px;
  background: var(--fsd-border-strong);
}

.swap-btn {
  width: var(--fsd-touch-target-min);
  height: var(--fsd-touch-target-min);
  border: 1px solid var(--fsd-border);
  border-radius: var(--fsd-radius-sm);
  background: var(--fsd-bg-hover);
  color: var(--fsd-text-secondary);
  font-size: 16px;
  cursor: pointer;

  &:active {
    background: var(--fsd-bg-active);
    color: var(--fsd-accent-strong);
  }
}

.section-hint {
  font-size: 11px;
  color: var(--fsd-text-tertiary);
  letter-spacing: 0.02em;
}

/* ── 路线卡片（商品列表风格） ── */
.route-cards {
  display: flex;
  flex-direction: column;
  border-top: 1px solid var(--fsd-border);
}

.route-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--fsd-space-3);
  min-height: var(--fsd-touch-target-min);
  padding: var(--fsd-space-3) 0;
  border: 0;
  border-bottom: 1px solid var(--fsd-border);
  background: transparent;
  text-align: left;
  cursor: pointer;
  transition: background-color var(--fsd-transition-base);

  &:hover:not(:disabled),
  &:active:not(:disabled) {
    background: var(--fsd-bg-hover);
  }
}

.route-card:disabled {
  opacity: 0.55;
}

.route-card-main {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.route-label {
  font-family: var(--fsd-font-display);
  font-size: var(--fsd-text-md);
  font-weight: var(--fsd-font-semibold);
  color: var(--fsd-text-primary);
  letter-spacing: 0;
}

.route-codes {
  font-family: var(--fsd-font-mono);
  font-size: 11px;
  color: var(--fsd-text-tertiary);
  letter-spacing: 0;
}

.route-action {
  flex-shrink: 0;
  padding: 6px 9px;
  border-radius: var(--fsd-radius-sm);
  background: var(--fsd-accent-selected);
  color: var(--fsd-accent-strong);
  font-size: 11px;
  font-weight: var(--fsd-font-bold);
  white-space: nowrap;
  letter-spacing: 0;
  box-shadow: none;
}

/* ── 表单 ── */
.order-form {
  :deep(.ant-form-item) {
    margin-bottom: 12px;
  }
}

/* ── 路线预览 ── */
.route-preview {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
  padding: 14px 16px;
  border-radius: 6px;
  background: #f8fafc;
  border: 1px solid var(--fsd-border);
  font-family: var(--fsd-font-mono);
  font-size: var(--fsd-text-md);
  font-weight: var(--fsd-font-semibold);
  color: var(--fsd-text-primary);
  letter-spacing: 0;
}

.preview-station {
  flex: 1;
  text-align: center;
}

.arrow {
  color: var(--fsd-warning);
  font-weight: var(--fsd-font-bold);
  font-size: 16px;
}

/* ── 规格选择块（配重 / 优先级） ── */
.spec-block {
  margin-bottom: 14px;
  padding: 12px;
  border-radius: 0;
  background: transparent;
  border: 0;
  border-top: 1px solid #eef1f4;
}

.spec-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 10px;
}

.spec-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: var(--fsd-text-sm);
  font-weight: var(--fsd-font-semibold);
  color: var(--fsd-text-secondary);
  letter-spacing: 0;
}

.spec-icon {
  font-size: 14px;
  color: var(--fsd-accent);
}

.spec-value {
  font-family: var(--fsd-font-mono);
  font-size: var(--fsd-text-sm);
  font-weight: var(--fsd-font-bold);
  color: var(--fsd-accent);
}

.common-routes {
  padding-top: 2px;
}

/* ── 配重选择器（按钮组） ── */
.weight-selector {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.weight-chip {
  min-width: 56px;
  min-height: var(--fsd-touch-target-min);
  padding: 0 14px;
  border-radius: 6px;
  border: 1px solid var(--fsd-border);
  background: var(--fsd-bg-deep);
  color: var(--fsd-text-secondary);
  font-size: var(--fsd-text-sm);
  font-weight: var(--fsd-font-semibold);
  letter-spacing: 0;
  transition:
    background-color var(--fsd-transition-base),
    border-color var(--fsd-transition-base),
    color var(--fsd-transition-base);
  cursor: pointer;

  &:hover {
    border-color: var(--fsd-border-active);
    color: var(--fsd-text-primary);
  }
}

.weight-chip.active {
  border-color: var(--fsd-accent);
  background: var(--fsd-accent-bg);
  color: var(--fsd-accent);
}

.weight-chip-custom.active {
  border-style: dashed;
}

.weight-input {
  margin-top: 10px;
}

/* ── 优先级选择 ── */
.priority-row {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.priority-chip {
  min-width: 56px;
  min-height: var(--fsd-touch-target-min);
  padding: 0 14px;
  border-radius: var(--fsd-radius-sm);
  border: 1px solid var(--fsd-border);
  background: var(--fsd-bg-deep);
  color: var(--fsd-text-secondary);
  font-size: var(--fsd-text-sm);
  font-weight: var(--fsd-font-semibold);
  transition:
    background-color var(--fsd-transition-base),
    border-color var(--fsd-transition-base),
    color var(--fsd-transition-base);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;

  &:hover {
    border-color: var(--fsd-border-active);
    color: var(--fsd-text-primary);
  }
}

.priority-chip.active {
  border-color: var(--fsd-accent);
  background: var(--fsd-accent-bg);
  color: var(--fsd-accent);
}

.chip-code {
  font-family: var(--fsd-font-mono);
  font-weight: var(--fsd-font-bold);
}

.chip-text {
  font-size: 11px;
  color: var(--fsd-text-tertiary);
}

.priority-chip.active .chip-text {
  color: var(--fsd-accent);
}

/* ── 底部固定提交栏（京东结算栏风格） ── */
.submit-bar {
  position: sticky;
  right: 0;
  bottom: calc(56px + env(safe-area-inset-bottom, 0px));
  left: 0;
  z-index: 5;
  display: flex;
  align-items: center;
  gap: var(--fsd-space-3);
  margin: var(--fsd-space-1) calc(-1 * var(--fsd-space-4)) 0;
  padding: var(--fsd-space-3) var(--fsd-space-4);
  border-top: 1px solid var(--fsd-border);
  background: var(--fsd-bg-base);
}

.quick-order-panel--tracking-visible .submit-bar {
  position: static;
}

.submit-summary {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.summary-route {
  font-family: var(--fsd-font-mono);
  font-size: var(--fsd-text-sm);
  font-weight: var(--fsd-font-bold);
  color: var(--fsd-text-primary);
  letter-spacing: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.summary-weight {
  font-size: 11px;
  color: var(--fsd-accent);
  font-weight: var(--fsd-font-semibold);
}

.submit-btn {
  flex-shrink: 0;
  min-width: 132px;
  min-height: 48px;
  padding: 0 var(--fsd-space-4);
  border: 1px solid transparent;
  border-radius: var(--fsd-radius-sm);
  background: var(--fsd-action-primary);
  color: var(--fsd-text-on-action);
  font-size: var(--fsd-text-md);
  font-weight: var(--fsd-font-semibold);
  letter-spacing: 0;
  cursor: pointer;
  transition: background-color var(--fsd-transition-base);

  &:hover:not(:disabled) {
    background: var(--fsd-action-primary-hover);
  }

  &:active:not(:disabled) {
    background: var(--fsd-action-primary-active);
  }
}

.submit-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

/* ── Ant Design 组件深色适配 ── */
:deep(.ant-form-item-label > label) {
  color: var(--fsd-text-secondary);
  font-size: 12px;
  font-weight: var(--fsd-font-medium);
  letter-spacing: 0.02em;
}

:deep(.ant-select-selector),
:deep(.ant-input),
:deep(.ant-input-number),
:deep(.ant-input-affix-wrapper) {
  background: var(--fsd-bg-elevated) !important;
  border-color: var(--fsd-border) !important;
  color: var(--fsd-text-primary) !important;
  border-radius: var(--fsd-radius-sm) !important;
}

:deep(.ant-select-selection-placeholder),
:deep(.ant-input::placeholder),
:deep(.ant-input-number-input::placeholder) {
  color: var(--fsd-text-tertiary) !important;
}

:deep(.ant-input-number-input) {
  color: var(--fsd-text-primary) !important;
}
</style>
