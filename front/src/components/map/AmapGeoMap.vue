<template>
  <div class="amap-geo-map">
    <div ref="hostRef" class="amap-geo-map__host" />
    <div v-if="loading" class="amap-geo-map__overlay">地图加载中…</div>
    <div v-else-if="error" class="amap-geo-map__overlay amap-geo-map__overlay--error">
      <p>{{ error }}</p>
      <p class="amap-geo-map__hint">在 <code>front/.env.local</code> 配置 <code>VITE_AMAP_KEY</code> 与 <code>VITE_AMAP_SECURITY_CODE</code></p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref, shallowRef, watch } from 'vue'
import { getMapConfig, resolveGeoMapProvider } from '@/maps'
import type { GeoMapHandle, GeoMapMarker, GeoMapPolygon } from '@/maps'

const props = withDefaults(
  defineProps<{
    center?: [number, number]
    zoom?: number
    markers?: GeoMapMarker[]
    polygons?: GeoMapPolygon[]
  }>(),
  {
    markers: () => [],
    polygons: () => [],
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
    handle.value.setMarkers(props.markers ?? [], { fitView: true })
    handle.value.setPolygons(props.polygons ?? [])
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
  padding: 24px;
  text-align: center;
  color: rgba(255, 255, 255, 0.78);
  background: rgba(13, 17, 23, 0.92);
}

.amap-geo-map__overlay--error {
  color: #ffb4b4;
}

.amap-geo-map__hint {
  margin: 0;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.55);

  code {
    color: #7ee787;
  }
}
</style>
