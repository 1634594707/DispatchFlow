<template>
  <div class="endpoint-input" :data-testid="`endpoint-${testId}`">
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
      <div v-if="pickedLabel" class="coord-picked" :data-testid="`endpoint-${testId}-picked`">
        <span>已选 {{ pickedLabel }}</span>
        <button type="button" class="coord-clear" :data-testid="`endpoint-${testId}-clear`" @click="clear">
          清除
        </button>
      </div>

      <AmapPointPicker
        v-if="mapAvailable"
        :model-value="amapModel"
        :center="mapCenter"
        :zoom="mapZoom"
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
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import AmapPointPicker from '@/components/infrastructure/AmapPointPicker.vue'
import { isAmapConfigured } from '@/maps'
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
    defaultMode: 'station',
  },
)

const emit = defineEmits<{ 'update:modelValue': [value: ParkOrderEndpoint | null] }>()

const mapAvailable = isAmapConfigured()
const mode = ref<'station' | 'coord'>(
  props.modelValue?.kind === 'coord' ? 'coord' : props.modelValue?.kind === 'station' ? 'station' : props.defaultMode,
)
const lngText = ref('')
const latText = ref('')
const entryError = ref('')

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

/** 站点与坐标二选一：切模式即清空，不留"两个都填了后端按哪个"的悬案。 */
function switchMode(next: 'station' | 'coord') {
  if (mode.value === next) return
  mode.value = next
  entryError.value = ''
  emit('update:modelValue', null)
}

function onStationChange(value: number) {
  emit('update:modelValue', { kind: 'station', stationId: value })
}

function onMapPick(point: { lng: number; lat: number } | null) {
  entryError.value = ''
  emit('update:modelValue', point ? { kind: 'coord', lng: point.lng, lat: point.lat } : null)
}

function clear() {
  entryError.value = ''
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
  entryError.value = ''
  emit('update:modelValue', { kind: 'coord', lng, lat })
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
