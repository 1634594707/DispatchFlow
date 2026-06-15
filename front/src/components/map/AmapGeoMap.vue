<template>
  <div class="amap-geo-map">
    <div ref="hostRef" class="amap-geo-map__host" />
    <div v-if="loading" class="amap-geo-map__overlay">地图加载中…</div>
    <div v-else-if="error" class="amap-geo-map__overlay amap-geo-map__overlay--error">
      <p>{{ error }}</p>
      <p class="amap-geo-map__hint">
        配置 <code>VITE_AMAP_KEY</code> 与 <code>VITE_AMAP_SECURITY_CODE</code>（构建时或 <code>runtime-config.js</code>），并在高德控制台加入当前站点域名白名单。
      </p>
      <router-link class="amap-geo-map__check-link" to="/system/config-check">打开试点配置自检 →</router-link>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref, shallowRef, watch } from 'vue'
import { getMapConfig, resolveGeoMapProvider } from '@/maps'
import type { GeoMapCircle, GeoMapHandle, GeoMapMarker, GeoMapPolygon, GeoMapPolyline } from '@/maps'

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
  }>(),
  {
    markers: () => [],
    polygons: () => [],
    polylines: () => [],
    circles: () => [],
    fitViewPoints: () => [],
    fitViewOnChange: false,
  },
)

const hostRef = ref<HTMLElement>()
const loading = ref(true)
const error = ref('')
const handle = shallowRef<GeoMapHandle | null>(null)

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
      zoom: props.zoom ?? config.defaultZoom,
    })
    handle.value.setCircles(props.circles ?? [])
    handle.value.setPolygons(props.polygons ?? [])
    handle.value.setPolylines(props.polylines ?? [])
    const initialFit = props.fitViewPoints?.length ? props.fitViewPoints : undefined
    handle.value.setMarkers(props.markers ?? [], { fitView: !initialFit?.length })
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
  () => props.markers,
  (markers) => {
    handle.value?.setMarkers(markers ?? [])
  },
  { deep: true },
)

watch(
  () => props.polygons,
  (polygons) => {
    handle.value?.setPolygons(polygons ?? [])
  },
  { deep: true },
)

watch(
  () => props.polylines,
  (polylines) => {
    handle.value?.setPolylines(polylines ?? [])
  },
  { deep: true },
)

watch(
  () => props.circles,
  (circles) => {
    handle.value?.setCircles(circles ?? [])
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
  background: #0d1117;
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
  background: rgba(13, 17, 23, 0.92);
  color: #e6edf3;
  font-size: 14px;
  z-index: 2;
}

.amap-geo-map__overlay--error {
  color: #ff8fa3;
}

.amap-geo-map__hint {
  font-size: 12px;
  color: #8b949e;
  code {
    color: #79c0ff;
  }
}

.amap-geo-map__check-link {
  font-size: 13px;
  color: #58a6ff;
  text-decoration: none;

  &:hover {
    text-decoration: underline;
  }
}
</style>
