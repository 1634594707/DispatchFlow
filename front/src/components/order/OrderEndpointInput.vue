<template>
  <div
    class="endpoint-input"
    :class="{ 'endpoint-input--filled': Boolean(modelValue) }"
    :data-testid="`endpoint-${testId}`"
  >
    <!--
      卡头 = 顺丰那张卡的一行：选完收成一行摘要，要改再点开。
      原来这里没有"收成"这一层，两个端点各占一块 form-item，整屏都是控件。
    -->
    <button
      type="button"
      class="endpoint-row"
      :disabled="disabled"
      :aria-expanded="expanded ? 'true' : 'false'"
      :data-testid="`endpoint-${testId}-row`"
      @click="setOpen(!expanded)"
    >
      <span class="row-dot" :class="`row-dot--${testId}`" aria-hidden="true" />
      <span class="row-body">
        <span class="row-title">{{ title }}</span>
        <span v-if="modelValue" class="row-main" :data-testid="`endpoint-${testId}-picked`">{{ summaryMain }}</span>
        <span v-else class="row-empty">{{ emptyHint }}</span>
      </span>
      <span class="row-mode-tag" v-if="modelValue">{{ modelValue.kind === 'coord' ? '地图坐标' : '服务点' }}</span>
      <span class="row-chevron" :class="{ 'row-chevron--open': expanded }" aria-hidden="true">⌄</span>
    </button>

    <div v-show="expanded" class="endpoint-editor">
      <div class="endpoint-mode" role="group" :aria-label="`${title}方式`">
        <button
          type="button"
          class="mode-chip"
          :class="{ active: mode === 'station' }"
          :disabled="disabled"
          :data-testid="`endpoint-${testId}-mode-station`"
          @click="switchMode('station')"
        >
          常用服务点
        </button>
        <button
          type="button"
          class="mode-chip"
          :class="{ active: mode === 'coord' }"
          :disabled="disabled"
          :data-testid="`endpoint-${testId}-mode-coord`"
          @click="switchMode('coord')"
        >
          在地图上点
        </button>
      </div>

      <a-select
        v-if="mode === 'station'"
        :value="stationId"
        :placeholder="stationPlaceholder"
        :size="size"
        :loading="loadingStations"
        :disabled="disabled"
        show-search
        option-filter-prop="label"
        popup-class-name="mobile-order-select-dropdown"
        :options="groups"
        :data-testid="`endpoint-${testId}-station`"
        @update:value="onStationChange"
      />

      <div v-else class="endpoint-coord">
        <button
          v-if="pickedLabel"
          type="button"
          class="coord-clear"
          :data-testid="`endpoint-${testId}-clear`"
          @click="clear"
        >
          清除当前选点
        </button>

        <AmapPointPicker
          v-if="mapAvailable"
          :model-value="amapModel"
          :center="mapCenter"
          :zoom="mapZoom"
          :service-areas="serviceAreas"
          @update:model-value="onMapPick"
        />
        <p v-if="!mapAvailable" class="coord-hint">
          高德地图不可用，可直接填写 GCJ-02 经纬度。
        </p>

        <div class="coord-entry">
          <a-input
            v-model:value="lngText"
            size="small"
            input-mode="decimal"
            placeholder="经度，如 121.080354"
            :disabled="disabled"
            :data-testid="`endpoint-${testId}-lng`"
          />
          <a-input
            v-model:value="latText"
            size="small"
            input-mode="decimal"
            placeholder="纬度，如 31.961977"
            :disabled="disabled"
            :data-testid="`endpoint-${testId}-lat`"
          />
          <button
            type="button"
            class="coord-apply"
            :disabled="disabled"
            :data-testid="`endpoint-${testId}-apply`"
            @click="applyManualCoord"
          >
            用这个坐标
          </button>
        </div>
        <p v-if="entryError" class="coord-error" :data-testid="`endpoint-${testId}-error`">{{ entryError }}</p>
      </div>
    </div>
  </div>
</template>
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import AmapPointPicker from '@/components/infrastructure/AmapPointPicker.vue'
import { isAmapConfigured } from '@/maps'
import { isOrderablePoint, type ServiceAreaShape } from '@/maps/parkGeoMapLayers'
import type { MobileStationSelectGroup } from '@/maps/stationLayers'
import type { ParkOrderEndpoint } from '@/types/park'

const props = withDefaults(
  defineProps<{
    modelValue: ParkOrderEndpoint | null
    groups: MobileStationSelectGroup[]
    title: string
    testId: string
    stationPlaceholder?: string
    size?: 'large' | 'middle' | 'small'
    disabled?: boolean
    loadingStations?: boolean
    mapCenter?: [number, number]
    mapZoom?: number
    /** 可下单范围：透传给点选地图，让它把范围画出来并把范围外的点挡掉 */
    serviceAreas?: ServiceAreaShape | null
    /**
     * 受控展开：父级给 `true/false` 时本行开合由父级决定（顺丰那张卡一次只展开一行）。
     * 不给（`undefined`）就退回组件自己的开合 —— 别的页面（PC 下单弹窗）没接父级状态。
     */
    open?: boolean | null
    /**
     * 设施模型 v2 后送货侧已经没有可选站点（取货固定为总发货仓库，送货由用户任意点决定），
     * 所以送货侧初始就该停在"在地图上点"，否则会落在一个空的下拉上、看着像坏了。
     */
    defaultMode?: 'station' | 'coord'
  }>(),
  {
    stationPlaceholder: '选择服务点',
    size: 'large',
    disabled: false,
    loadingStations: false,
    mapCenter: undefined,
    mapZoom: 15,
    serviceAreas: null,
    open: undefined,
    defaultMode: 'station',
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: ParkOrderEndpoint | null]
  toggle: [open: boolean]
}>()

const mapAvailable = isAmapConfigured()
const mode = ref<'station' | 'coord'>(
  props.modelValue?.kind === 'coord' ? 'coord' : props.modelValue?.kind === 'station' ? 'station' : props.defaultMode,
)
const lngText = ref('')
const latText = ref('')
const entryError = ref('')

/**
 * 卡头是否展开。**初始一律展开**，折叠只由本组件自己的选择动作触发。
 *
 * 不按“有值就折”实现：页面会自己同步默认端点（`syncDefaultOrderStations`、路由带参），
 * 那些不是用户刚点的一下 —— 那样一进页面控件就全被藏起来（第一版就是这么把 e2e 打挂的）。
 *
 * ⚠ 声明必须在下面那个 `immediate: true` 的 watch 之前 —— 那个回调在 setup 期就会跑一次，
 *   放它后面会撞 TDZ（`ReferenceError: Cannot access 'expanded' before initialization`）。
 */
const localOpen = ref(true)
const expanded = computed(() => props.open ?? localOpen.value)

/** 开合只有一条出口：本地状态 + 通知父级，两边不会各说一套。 */
function setOpen(next: boolean) {
  localOpen.value = next
  emit('toggle', next)
}

watch(
  () => props.modelValue,
  (next) => {
    if (next?.kind === 'coord') {
      lngText.value = String(next.lng)
      latText.value = String(next.lat)
    }
  },
  { immediate: true },
)

/**
 * 选完收成一行；**不带 `immediate`** —— 首帧的端点是页面自己同步的默认值，
 * 若按"有值就折"处理，一进来控件就全被藏起来（e2e 与真实用户都会撞这个）。
 */
const stationId = computed(() =>
  props.modelValue?.kind === 'station' ? props.modelValue.stationId : undefined,
)

const amapModel = computed(() =>
  props.modelValue?.kind === 'coord'
    ? { lng: props.modelValue.lng, lat: props.modelValue.lat }
    : null,
)

const pickedLabel = computed(() =>
  props.modelValue?.kind === 'coord'
    ? `坐标 ${props.modelValue.lng.toFixed(6)}, ${props.modelValue.lat.toFixed(6)}`
    : '',
)

/** 摘要行：坐标给到 6 位小数（与提交给后端的是同一个数），服务点给它的名字。 */
const summaryMain = computed(() => {
  const value = props.modelValue
  if (!value) return ''
  if (value.kind === 'coord') return `坐标 ${value.lng.toFixed(6)}, ${value.lat.toFixed(6)}`
  for (const group of props.groups) {
    const hit = (group.options ?? []).find((option) => option.value === value.stationId)
    if (hit) return String(hit.label)
  }
  return `服务点 #${value.stationId}`
})

const emptyHint = computed(() =>
  mode.value === 'coord' ? '在地图上点一个位置，或直接填经纬度' : stationPlaceholderOr(props.stationPlaceholder),
)

function stationPlaceholderOr(fallback: string) {
  return fallback || '选择服务点'
}

/** 站点与坐标二选一：切模式即清空，不留"两个都填了后端按哪个"的悬案。 */
function switchMode(next: 'station' | 'coord') {
  if (mode.value === next) return
  mode.value = next
  entryError.value = ''
  setOpen(true)
  emit('update:modelValue', null)
}

function onStationChange(value: number) {
  emit('update:modelValue', { kind: 'station', stationId: value })
  setOpen(false)
}

/**
 * 范围挡点：地图点选与手输走**同一条**判据。
 * 只在地图上拦、手输能过，等于两套口径 —— 而手输恰恰是没高德 key 时唯一的路径。
 */
function outsideServiceArea(lng: number, lat: number): boolean {
  if (!props.serviceAreas) return false
  if (isOrderablePoint(props.serviceAreas, [lng, lat])) return false
  entryError.value = '这里不在可下单范围内：请在亮色的受理区里选点（当前值未改动）'
  return true
}

function onMapPick(point: { lng: number; lat: number } | null) {
  entryError.value = ''
  if (point && outsideServiceArea(point.lng, point.lat)) return
  emit('update:modelValue', point ? { kind: 'coord', lng: point.lng, lat: point.lat } : null)
  setOpen(!point)
}

function clear() {
  entryError.value = ''
  setOpen(true)
  emit('update:modelValue', null)
}

function applyManualCoord() {
  const lng = Number(lngText.value)
  const lat = Number(latText.value)
  if (!Number.isFinite(lng) || !Number.isFinite(lat)) {
    entryError.value = '请输入有效的经纬度数字'
    return
  }
  if (Math.abs(lng) > 180 || Math.abs(lat) > 90) {
    entryError.value = '经纬度超出取值范围（经度 ±180，纬度 ±90）'
    return
  }
  if (outsideServiceArea(lng, lat)) return
  entryError.value = ''
  emit('update:modelValue', { kind: 'coord', lng, lat })
  setOpen(false)
}
</script>

<style scoped lang="less">
.endpoint-input {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
}

.endpoint-mode {
  display: inline-flex;
  gap: 4px;
  margin-bottom: 6px;
  padding: 3px;
  border: 1px solid var(--fsd-border);
  border-radius: var(--fsd-radius-sm);
  background: var(--fsd-bg-deep);
}

/* ── 卡头那一行（顺丰的两点卡） ───────────────────────────────────────── */
.endpoint-row {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 10px 12px;
  text-align: left;
  background: transparent;
  border: 0;
  border-radius: 12px;
  color: inherit;
  cursor: pointer;
}
.endpoint-input--filled .endpoint-row {
  background: rgba(45, 224, 138, 0.06);
}
.endpoint-row:disabled {
  cursor: default;
  opacity: 0.6;
}
.row-dot {
  flex: none;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #94a3b8;
}
.row-dot--pickup {
  background: #2de08a;
}
.row-dot--dropoff {
  background: #ff5c7c;
}
.row-body {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1;
}
.row-title {
  font-size: 11px;
  letter-spacing: 0.4px;
  color: #8b98a8;
}
.row-main {
  font-size: 15px;
  font-weight: 600;
  line-height: 1.35;
  color: #e8edf3;
  overflow-wrap: anywhere;
}
.row-empty {
  font-size: 14px;
  color: #7d8898;
}
.row-mode-tag {
  flex: none;
  padding: 1px 6px;
  font-size: 11px;
  color: #9ba8b8;
  border: 1px solid rgba(148, 163, 184, 0.35);
  border-radius: 999px;
}
.row-chevron {
  flex: none;
  font-size: 16px;
  line-height: 1;
  color: #8b98a8;
  transform: rotate(-90deg);
  transition: transform 0.16s ease;
}
.row-chevron--open {
  transform: rotate(0deg);
}
.endpoint-editor {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 2px 12px 12px;
}

.mode-chip {
  min-height: 30px;
  padding: 0 10px;
  border: 1px solid transparent;
  border-radius: var(--fsd-radius-sm);
  background: transparent;
  color: var(--fsd-text-secondary);
  font-size: 12px;
  font-weight: var(--fsd-font-semibold);
  cursor: pointer;

  &.active {
    border-color: var(--fsd-accent-border);
    background: var(--fsd-accent-selected);
    color: var(--fsd-accent-strong);
  }

  &:disabled {
    opacity: 0.55;
    cursor: not-allowed;
  }
}

.endpoint-coord {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.coord-picked {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 6px 10px;
  border: 1px solid var(--fsd-accent-border);
  border-radius: var(--fsd-radius-sm);
  background: var(--fsd-accent-subtle);
  color: var(--fsd-accent-strong);
  font-family: var(--fsd-font-mono);
  font-size: 12px;
}

.coord-clear {
  border: 0;
  background: transparent;
  color: var(--fsd-text-secondary);
  font-size: 12px;
  text-decoration: underline;
  cursor: pointer;
}

.coord-entry {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) auto;
  gap: 6px;
  align-items: center;
}

.coord-apply {
  min-height: 32px;
  padding: 0 12px;
  border: 1px solid var(--fsd-accent-border);
  border-radius: var(--fsd-radius-sm);
  background: var(--fsd-accent-selected);
  color: var(--fsd-accent-strong);
  font-size: 12px;
  font-weight: var(--fsd-font-semibold);
  white-space: nowrap;
  cursor: pointer;

  &:disabled {
    opacity: 0.55;
    cursor: not-allowed;
  }
}

.coord-hint {
  margin: 0;
  color: var(--fsd-text-tertiary);
  font-size: 11px;
}

.coord-error {
  margin: 0;
  color: var(--fsd-error);
  font-size: 12px;
}
</style>
