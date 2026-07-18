<template>
  <div class="amap-geo-map">
    <div ref="hostRef" class="amap-geo-map__host" />
    <div v-if="loading" class="amap-geo-map__overlay">地图加载中…</div>
    <div v-else-if="error" class="amap-geo-map__overlay amap-geo-map__overlay--error">
      <p>{{ error }}</p>
      <p class="amap-geo-map__hint">
        配置 <code>VITE_AMAP_KEY</code> 与 <code>VITE_AMAP_SECURITY_CODE</code>（构建时或
        <code>runtime-config.js</code>），并在高德控制台加入当前站点域名白名单。
      </p>
      <router-link class="amap-geo-map__check-link" to="/system/config-check"
        >打开试点配置自检 →</router-link
      >
    </div>
    <!-- 阶段七 7.5：L0/L1/L2 地图层级切换器 -->
    <div
      v-if="!loading && !error && showLevelSwitcher"
      class="amap-geo-map__level-switcher"
      role="group"
      aria-label="地图层级切换"
    >
      <button
        v-for="opt in LEVEL_OPTIONS"
        :key="opt.value"
        type="button"
        class="amap-geo-map__level-btn"
        :class="{ 'amap-geo-map__level-btn--active': currentLevel === opt.value }"
        :title="opt.description"
        @click="selectLevel(opt.value)"
      >
        <span class="amap-geo-map__level-code">{{ opt.value }}</span>
        <span class="amap-geo-map__level-label">{{ opt.label }}</span>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, shallowRef, watch } from 'vue'
import { getMapConfig, resolveGeoMapProvider } from '@/maps'
import type {
  GeoMapCircle,
  GeoMapHandle,
  GeoMapMarker,
  GeoMapPolygon,
  GeoMapPolyline,
} from '@/maps'

/**
 * 阶段七 7.5：地图层级 L0/L1/L2。
 * - L0 产业带：低缩放（~10），展示双中心 20km 覆盖圈，不显示站点/车辆细节
 * - L1 试点  ：中缩放（~14，默认），展示园区围栏多边形与车辆轨迹
 * - L2 站点  ：高缩放（~16），展示单个站点 marker 与详细路径
 * 父组件可通过 v-model:mapLevel 双向绑定，并根据层级自行过滤传入的 markers/polygons。
 */
type MapLevel = 'L0' | 'L1' | 'L2'

interface LevelPreset {
  zoom: number
  /** 是否显示车辆/站点 marker（L0 隐藏，避免产业带层级过于密集） */
  showMarkers: boolean
  /** 是否显示路径线条（L0 隐藏） */
  showPolylines: boolean
  /** 是否显示围栏多边形（L0 隐藏） */
  showPolygons: boolean
  /** 是否显示覆盖圈（仅 L0 显示） */
  showCircles: boolean
}

const LEVEL_PRESETS: Record<MapLevel, LevelPreset> = {
  L0: {
    zoom: 10,
    showMarkers: false,
    showPolylines: false,
    showPolygons: false,
    showCircles: true,
  },
  L1: { zoom: 14, showMarkers: true, showPolylines: true, showPolygons: true, showCircles: false },
  L2: { zoom: 16, showMarkers: true, showPolylines: true, showPolygons: true, showCircles: false },
}

const LEVEL_OPTIONS: Array<{ value: MapLevel; label: string; description: string }> = [
  { value: 'L0', label: '产业带', description: '产业带层级：展示 20km 覆盖范围' },
  { value: 'L1', label: '试点', description: '试点层级：展示园区围栏与车辆轨迹' },
  { value: 'L2', label: '站点', description: '站点层级：展示站点细节与路径' },
]

const props = withDefaults(
  defineProps<{
    center?: [number, number]
    zoom?: number
    markers?: GeoMapMarker[]
    polygons?: GeoMapPolygon[]
    polylines?: GeoMapPolyline[]
    circles?: GeoMapCircle[]
    /** When set, fit viewport to these route points instead of marker bbox. */
    fitViewPoints?: [number, number][]
    /** Re-fit when fitViewPoints change (e.g. follow-car). */
    fitViewOnChange?: boolean
    /** 阶段七 7.5：地图层级，支持 v-model:mapLevel */
    mapLevel?: MapLevel
    /** 是否显示右上角层级切换器（默认 true） */
    showLevelSwitcher?: boolean
  }>(),
  {
    markers: () => [],
    polygons: () => [],
    polylines: () => [],
    circles: () => [],
    fitViewPoints: () => [],
    fitViewOnChange: false,
    mapLevel: 'L1',
    showLevelSwitcher: true,
  },
)

const emit = defineEmits<{
  (event: 'update:mapLevel', level: MapLevel): void
  (event: 'levelChange', level: MapLevel): void
  (event: 'markerClick', marker: GeoMapMarker): void
}>()

const hostRef = ref<HTMLElement>()
const loading = ref(true)
const error = ref('')
const handle = shallowRef<GeoMapHandle | null>(null)
const currentLevel = ref<MapLevel>(props.mapLevel)

const preset = computed<LevelPreset>(() => LEVEL_PRESETS[currentLevel.value])

/** 按层级过滤后的可见元素 */
const visibleMarkers = computed<GeoMapMarker[]>(() =>
  preset.value.showMarkers ? props.markers : [],
)
const visiblePolylines = computed<GeoMapPolyline[]>(() =>
  preset.value.showPolylines ? props.polylines : [],
)
const visiblePolygons = computed<GeoMapPolygon[]>(() =>
  preset.value.showPolygons ? props.polygons : [],
)
const visibleCircles = computed<GeoMapCircle[]>(() =>
  preset.value.showCircles ? props.circles : [],
)

/** 当前生效的缩放级别：优先使用父组件显式传入的 zoom，否则使用层级预设 */
const effectiveZoom = computed(() => props.zoom ?? preset.value.zoom)

function selectLevel(level: MapLevel) {
  if (level === currentLevel.value) return
  currentLevel.value = level
  emit('update:mapLevel', level)
  emit('levelChange', level)
  // 切换层级后调整缩放（仅在父组件未显式锁定 zoom 时生效）
  if (props.zoom == null && handle.value) {
    handle.value.setZoom(preset.value.zoom)
  }
  // 立即应用可见性过滤
  handle.value?.setMarkers(visibleMarkers.value)
  handle.value?.setPolylines(visiblePolylines.value)
  handle.value?.setPolygons(visiblePolygons.value)
  handle.value?.setCircles(visibleCircles.value)
}

async function mountMap() {
  loading.value = true
  error.value = ''
  handle.value?.destroy()
  handle.value = null

  const provider = resolveGeoMapProvider()
  if (!provider || !hostRef.value) {
    loading.value = false
    error.value = provider ? '地图容器未就绪' : '高德 Key 未配置或 VITE_MAP_PROVIDER 不是 AMAP'
    return
  }

  const config = getMapConfig()
  try {
    handle.value = await provider.createMap({
      container: hostRef.value,
      center: props.center ?? config.defaultCenter,
      zoom: effectiveZoom.value,
      onMarkerClick: (marker) => emit('markerClick', marker),
    })
    // 应用层级可见性过滤
    handle.value.setCircles(visibleCircles.value)
    handle.value.setPolygons(visiblePolygons.value)
    handle.value.setPolylines(visiblePolylines.value)
    const initialFit = props.fitViewPoints?.length ? props.fitViewPoints : undefined
    handle.value.setMarkers(visibleMarkers.value, { fitView: !initialFit?.length })
    if (initialFit?.length) {
      handle.value.fitViewToPoints(initialFit)
    }
  } catch (err) {
    error.value = err instanceof Error ? err.message : '高德地图加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void mountMap()
})

onUnmounted(() => {
  handle.value?.destroy()
  handle.value = null
})

watch(
  () => [props.center, props.zoom] as const,
  ([center, zoom]) => {
    if (!handle.value) return
    if (center) handle.value.setCenter(center)
    if (zoom != null) handle.value.setZoom(zoom)
  },
)

watch(
  () => props.mapLevel,
  (level) => {
    if (level && level !== currentLevel.value) {
      currentLevel.value = level
      // 同步可见性与缩放
      handle.value?.setMarkers(visibleMarkers.value)
      handle.value?.setPolylines(visiblePolylines.value)
      handle.value?.setPolygons(visiblePolygons.value)
      handle.value?.setCircles(visibleCircles.value)
      if (props.zoom == null && handle.value) {
        handle.value.setZoom(preset.value.zoom)
      }
    }
  },
)

watch(
  () => visibleMarkers.value,
  (markers) => {
    handle.value?.setMarkers(markers)
  },
  { deep: true },
)

watch(
  () => visiblePolygons.value,
  (polygons) => {
    handle.value?.setPolygons(polygons)
  },
  { deep: true },
)

watch(
  () => visiblePolylines.value,
  (polylines) => {
    handle.value?.setPolylines(polylines)
  },
  { deep: true },
)

watch(
  () => visibleCircles.value,
  (circles) => {
    handle.value?.setCircles(circles)
  },
  { deep: true },
)

watch(
  () => props.fitViewPoints,
  (points) => {
    if (!props.fitViewOnChange || !handle.value || !points?.length) return
    handle.value.fitViewToPoints(points)
  },
  { deep: true },
)
</script>

<style scoped lang="less">
.amap-geo-map {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 360px;
  background: var(--fsd-bg-deep);
}

.amap-geo-map__host {
  width: 100%;
  height: 100%;
  min-height: inherit;
}

.amap-geo-map__overlay {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  background: rgba(11, 16, 24, 0.92);
  color: var(--fsd-text-primary);
  font-size: 14px;
  z-index: 2;
}

.amap-geo-map__overlay--error {
  color: var(--fsd-error);
}

.amap-geo-map__hint {
  font-size: 12px;
  color: var(--fsd-text-secondary);
  code {
    color: var(--fsd-accent);
  }
}

.amap-geo-map__check-link {
  font-size: 13px;
  color: var(--fsd-accent);
  text-decoration: none;

  &:hover {
    text-decoration: underline;
  }
}

/* 阶段七 7.5：L0/L1/L2 层级切换器样式 */
.amap-geo-map__level-switcher {
  position: absolute;
  top: 12px;
  right: 12px;
  z-index: 3;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 6px;
  background: rgba(11, 16, 24, 0.78);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 8px;
  backdrop-filter: blur(8px);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.35);
}

.amap-geo-map__level-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 108px;
  padding: 6px 10px;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 6px;
  color: var(--fsd-text-secondary);
  font-size: 12px;
  line-height: 1.2;
  cursor: pointer;
  transition: all 0.15s ease;

  &:hover {
    background: rgba(255, 255, 255, 0.05);
    color: var(--fsd-text-primary);
  }

  &--active {
    background: rgba(34, 199, 230, 0.16);
    border-color: rgba(34, 199, 230, 0.45);
    color: var(--fsd-accent);
  }
}

.amap-geo-map__level-code {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 18px;
  background: rgba(255, 255, 255, 0.08);
  border-radius: 3px;
  font-weight: 600;
  font-size: 11px;
  letter-spacing: 0.5px;
}

.amap-geo-map__level-btn--active .amap-geo-map__level-code {
  background: rgba(34, 199, 230, 0.3);
  color: #fff;
}

.amap-geo-map__level-label {
  flex: 1;
  text-align: left;
}
</style>

<style lang="less">
.amap-geo-map .amap-marker-label {
  max-width: 220px;
  padding: 6px 10px !important;
  overflow: hidden;
  border: 1px solid rgba(34, 199, 230, 0.58) !important;
  border-radius: 6px !important;
  background: rgba(7, 13, 19, 0.94) !important;
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.34) !important;
  color: #f3f8fa !important;
  font-size: 11px !important;
  font-weight: 700;
  line-height: 1.25 !important;
  letter-spacing: 0 !important;
  text-overflow: ellipsis;
  white-space: nowrap;
  pointer-events: none;
  backdrop-filter: blur(8px);
}
</style>
