<template>
  <section class="quick-order-panel">
    <!-- 顶部下单引导区（类似京东"填写订单"标题区） -->
    <header class="order-header">
      <div class="header-titles">
        <h2 class="order-title">填写订单</h2>
        <p class="order-subtitle">{{ demoRoutes.length }} 条典型短驳线路 · 一件代发</p>
      </div>
      <span v-if="parkLocked" class="park-tag">{{ parkName }}</span>
    </header>

    <!-- 模式切换 -->
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

    <!-- 快速下单按钮 -->
    <button type="button" class="quick-fill-btn" :disabled="submitting" @click="$emit('quickFill')">
      ⚡ 快速下单 · 一键填入默认取送货点
    </button>

    <!-- 热门路线卡片（类似淘宝商品列表） -->
    <section class="order-section">
      <div class="section-head">
        <span class="section-title">热门路线</span>
        <span class="section-hint">点击立即下单</span>
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
          <div class="route-card-main">
            <span class="route-label">{{ route.label }}</span>
            <span class="route-codes">{{ route.pickupCode }} → {{ route.dropoffCode }}</span>
          </div>
          <span class="route-action">{{ submitting ? '提交中…' : '立即下单' }}</span>
        </button>
      </div>
    </section>

    <!-- 自定义取送区（平铺展示，不再折叠） -->
    <section class="order-section">
      <div class="section-head">
        <span class="section-title">自定义取送</span>
      </div>

      <a-form layout="vertical" class="order-form">
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
      </a-form>

      <!-- 路线预览 -->
      <div class="route-preview">
        <span class="preview-station">{{ pickupPreview }}</span>
        <span class="arrow">→</span>
        <span class="preview-station">{{ dropoffPreview }}</span>
      </div>

      <!-- 配重选择（醒目，规格选择器风格，类似京东规格选择） -->
      <div class="spec-block">
        <div class="spec-head">
          <span class="spec-label">
            <span class="spec-icon" aria-hidden="true">⚖</span>
            货物配重
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

      <!-- 优先级选择 -->
      <div class="spec-block">
        <div class="spec-head">
          <span class="spec-label">派单优先级</span>
          <span class="spec-value">{{ priority }}</span>
        </div>
        <div class="priority-row">
          <button
            v-for="item in priorities"
            :key="item.value"
            type="button"
            class="priority-chip"
            :class="{ active: priority === item.value }"
            @click="$emit('update:priority', item.value)"
          >
            <span class="chip-code">{{ item.value }}</span>
            <span class="chip-text">{{ item.label }}</span>
          </button>
        </div>
      </div>

      <div class="spec-block">
        <div class="spec-head">
          <span class="spec-label">订单优先级</span>
          <span class="spec-value">{{ orderPriority }}</span>
        </div>
        <div class="priority-row">
          <button
            v-for="item in orderPriorities"
            :key="item.value"
            type="button"
            class="priority-chip"
            :class="{ active: orderPriority === item.value }"
            @click="$emit('update:orderPriority', item.value)"
          >
            {{ item.label }}
          </button>
        </div>
      </div>

      <!-- 备注 -->
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

    <!-- 底部固定提交栏（类似京东淘宝底部结算栏） -->
    <footer class="submit-bar">
      <div class="submit-summary">
        <span class="summary-route">{{ pickupPreview }} → {{ dropoffPreview }}</span>
        <span v-if="weight" class="summary-weight">配重 {{ weight }}kg</span>
      </div>
      <button type="button" class="submit-btn" :disabled="submitting" @click="$emit('submitCustom')">
        {{ submitting ? '提交中…' : '提交订单' }}
      </button>
    </footer>
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
  orderPriority: 'HIGH' | 'NORMAL' | 'LOW'
  weight?: number | null
  remark: string
  loadingParks?: boolean
  loadingStations?: boolean
  parkOptions?: { value: number; label: string }[]
}>()

const emit = defineEmits<{
  submitDemo: [route: DemoRoutePreset]
  submitCustom: []
  quickFill: []
  'update:parkId': [value: number]
  'update:orderMode': [value: MobileOrderMode]
  'update:pickupStationId': [value: number]
  'update:dropoffStationId': [value: number]
  'update:priority': [value: string]
  'update:orderPriority': [value: 'HIGH' | 'NORMAL' | 'LOW']
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
  return props.weight != null && !weightPresets.some(p => p.value === props.weight)
})

function selectWeight(val: number) {
  customWeightMode.value = false
  emit('update:weight', val)
}

function enableCustomWeight() {
  customWeightMode.value = true
  // 切换到自定义时，若当前是预设值则清空，便于用户重新输入
  if (props.weight != null && weightPresets.some(p => p.value === props.weight)) {
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

const priorities = [
  { value: 'P0', label: '最高' },
  { value: 'P1', label: '优先' },
  { value: 'P2', label: '标准' },
  { value: 'P3', label: '低' },
]

const orderPriorities = [
  { value: 'HIGH' as const, label: 'HIGH' },
  { value: 'NORMAL' as const, label: 'NORMAL' },
  { value: 'LOW' as const, label: 'LOW' },
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
  padding: 16px 14px 0;
  border-radius: var(--fsd-radius-xl);
  border: 1px solid var(--fsd-border);
  background:
    radial-gradient(circle at 0% 0%, var(--fsd-accent-subtle), transparent 45%),
    var(--fsd-bg-base);
  box-shadow: var(--fsd-shadow-card);
  position: relative;

  &::before {
    content: '';
    position: absolute;
    top: 20px;
    right: 20px;
    width: 180px;
    height: 180px;
    border-radius: 50%;
    background: radial-gradient(circle, var(--fsd-accent-glow), transparent 60%);
    filter: blur(40px);
    pointer-events: none;
    opacity: 0.4;
    z-index: 0;
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

.header-titles {
  min-width: 0;
}

.order-title {
  margin: 0;
  font-family: var(--fsd-font-display);
  font-size: var(--fsd-text-xl);
  font-weight: var(--fsd-font-bold);
  color: var(--fsd-text-heading);
  letter-spacing: var(--fsd-tracking-tight);
  line-height: var(--fsd-leading-tight);
}

.order-subtitle {
  margin: 4px 0 0;
  font-size: var(--fsd-text-xs);
  color: var(--fsd-text-tertiary);
  letter-spacing: 0.02em;
}

.park-tag {
  flex-shrink: 0;
  padding: 4px 10px;
  border-radius: var(--fsd-radius-full);
  background: var(--fsd-accent-bg);
  border: 1px solid var(--fsd-accent-border);
  color: var(--fsd-accent);
  font-size: 10px;
  font-weight: var(--fsd-font-semibold);
  font-family: var(--fsd-font-mono);
  letter-spacing: -0.01em;
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
  height: 36px;
  border-radius: var(--fsd-radius-sm);
  border: 1px solid transparent;
  background: transparent;
  color: var(--fsd-text-secondary);
  font-size: 12px;
  font-weight: var(--fsd-font-semibold);
  letter-spacing: -0.005em;
  transition: all var(--fsd-transition-fast);

  &:hover:not(.active) {
    color: var(--fsd-text-primary);
  }
}

.mode-chip.active {
  border-color: var(--fsd-border-active);
  background: var(--fsd-bg-elevated);
  color: var(--fsd-accent);
  box-shadow: var(--fsd-shadow-soft);
}

/* ── 快速下单按钮 ── */
.quick-fill-btn {
  width: 100%;
  height: 44px;
  margin-bottom: 16px;
  border-radius: var(--fsd-radius-md);
  border: 1px dashed var(--fsd-accent-border);
  background: var(--fsd-accent-bg);
  color: var(--fsd-accent);
  font-size: 13px;
  font-weight: var(--fsd-font-semibold);
  letter-spacing: -0.005em;
  cursor: pointer;
  transition: all var(--fsd-transition-fast);

  &:hover:not(:disabled) {
    background: rgba(34, 211, 238, 0.14);
    border-color: var(--fsd-accent);
    border-style: solid;
  }

  &:active:not(:disabled) {
    transform: scale(0.99);
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
  letter-spacing: -0.005em;

  &::before {
    content: '';
    display: inline-block;
    width: 3px;
    height: 12px;
    margin-right: 8px;
    vertical-align: -1px;
    border-radius: 2px;
    background: var(--fsd-gradient-accent);
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
  gap: 8px;
  position: relative;
}

.route-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px;
  border-radius: var(--fsd-radius-lg);
  border: 1px solid var(--fsd-border);
  background:
    var(--fsd-gradient-card),
    var(--fsd-bg-elevated);
  text-align: left;
  transition: all var(--fsd-transition-fast);
  cursor: pointer;

  &:hover:not(:disabled) {
    border-color: var(--fsd-accent-border);
    background: var(--fsd-bg-hover);
    transform: translateY(-1px);
    box-shadow: var(--fsd-shadow-soft);
  }
}

.route-card:active:not(:disabled) {
  transform: scale(0.99);
  border-color: var(--fsd-accent);
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
  letter-spacing: -0.015em;
}

.route-codes {
  font-family: var(--fsd-font-mono);
  font-size: 11px;
  color: var(--fsd-text-tertiary);
  letter-spacing: -0.01em;
}

.route-action {
  flex-shrink: 0;
  padding: 8px 14px;
  border-radius: var(--fsd-radius-full);
  background: var(--fsd-gradient-accent);
  color: #04141a;
  font-size: 11px;
  font-weight: var(--fsd-font-bold);
  white-space: nowrap;
  letter-spacing: 0.02em;
  box-shadow: 0 2px 8px rgba(34, 211, 238, 0.24);
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
  border-radius: var(--fsd-radius-md);
  background: var(--fsd-bg-deep);
  border: 1px solid var(--fsd-border);
  font-family: var(--fsd-font-mono);
  font-size: var(--fsd-text-md);
  font-weight: var(--fsd-font-semibold);
  color: var(--fsd-text-primary);
  letter-spacing: -0.01em;
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
  border-radius: var(--fsd-radius-md);
  background: var(--fsd-bg-elevated);
  border: 1px solid var(--fsd-border);
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
  letter-spacing: -0.005em;
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

/* ── 配重选择器（按钮组） ── */
.weight-selector {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.weight-chip {
  min-width: 56px;
  height: 40px;
  padding: 0 14px;
  border-radius: var(--fsd-radius-sm);
  border: 1px solid var(--fsd-border);
  background: var(--fsd-bg-deep);
  color: var(--fsd-text-secondary);
  font-size: var(--fsd-text-sm);
  font-weight: var(--fsd-font-semibold);
  letter-spacing: -0.005em;
  transition: all var(--fsd-transition-fast);
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
  box-shadow: 0 0 0 1px var(--fsd-accent-border) inset;
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
  height: 40px;
  padding: 0 14px;
  border-radius: var(--fsd-radius-sm);
  border: 1px solid var(--fsd-border);
  background: var(--fsd-bg-deep);
  color: var(--fsd-text-secondary);
  font-size: var(--fsd-text-sm);
  font-weight: var(--fsd-font-semibold);
  transition: all var(--fsd-transition-fast);
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
  box-shadow: 0 0 0 1px var(--fsd-accent-border) inset;
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
  bottom: 0;
  left: 0;
  right: 0;
  z-index: 5;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  margin: 4px -14px 0;
  background: linear-gradient(180deg, rgba(15, 18, 24, 0.85) 0%, var(--fsd-bg-base) 40%);
  backdrop-filter: blur(8px);
  border-top: 1px solid var(--fsd-border-split);
  box-shadow: 0 -8px 24px rgba(0, 0, 0, 0.32);
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
  letter-spacing: -0.01em;
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
  height: 48px;
  padding: 0 24px;
  border: 1px solid var(--fsd-accent-border);
  border-radius: var(--fsd-radius-full);
  background: var(--fsd-gradient-accent);
  color: #04141a;
  font-size: var(--fsd-text-md);
  font-weight: var(--fsd-font-bold);
  letter-spacing: -0.005em;
  cursor: pointer;
  transition: all var(--fsd-transition-fast);
  box-shadow: 0 4px 14px rgba(34, 211, 238, 0.32);

  &:hover:not(:disabled) {
    filter: brightness(1.08);
    box-shadow: 0 6px 20px rgba(34, 211, 238, 0.42);
    transform: translateY(-1px);
  }

  &:active:not(:disabled) {
    transform: translateY(0);
    filter: brightness(0.96);
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
