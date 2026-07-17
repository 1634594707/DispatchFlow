<template>
  <div class="amap-point-picker">
    <div ref="hostRef" class="amap-point-picker__host" />
    <div v-if="loading" class="amap-point-picker__overlay">地图加载中…</div>
    <div v-else-if="error" class="amap-point-picker__overlay amap-point-picker__overlay--error">
      <p>{{ error }}</p>
    </div>
    <div v-if="picked" class="amap-point-picker__info">
      <span>经纬度: {{ picked.lng.toFixed(6) }}, {{ picked.lat.toFixed(6) }}</span>
      <span v-if="picked.x != null && picked.y != null"> | 园区坐标: ({{ picked.x }}, {{ picked.y }})</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { getMapConfig } from '@/maps/config'
import { waitForAmapAuth } from '@/maps/amapAuth'
import { transformGeoCoordinates } from '@/api/park'

interface PickedPoint {
  lng: number
  lat: number
  x?: number
  y?: number
}

const props = withDefaults(
  defineProps<{
    /** 初始中心点 [lng, lat] */
    center?: [number, number]
    zoom?: number
    /** 已选中的点（用于回显） */
    modelValue?: PickedPoint | null
  }>(),
  {
    zoom: 16,
    modelValue: null,
  },
)

const emit = defineEmits<{
  'update:modelValue': [point: PickedPoint | null]
}>()

const hostRef = ref<HTMLElement>()
const loading = ref(true)
const error = ref('')
const picked = ref<PickedPoint | null>(props.modelValue)

let map: any = null
let marker: any = null
let AMapNs: any = null

async function mountMap() {
  loading.value = true
  error.value = ''
  const { amapKey, amapSecurityCode, defaultCenter, defaultZoom } = getMapConfig()
  if (!amapKey || !amapSecurityCode) {
    loading.value = false
    error.value = '高德 Key 或安全密钥未配置'
    return
  }
  if (!hostRef.value) {
    loading.value = false
    return
  }

  ;(window as any)._AMapSecurityConfig = { securityJsCode: amapSecurityCode }
  try {
    const { default: AMapLoader } = await import('@amap/amap-jsapi-loader')
    AMapNs = await AMapLoader.load({
      key: amapKey,
      version: '2.0',
      plugins: ['AMap.Scale'],
    })
    const center = props.center ?? props.modelValue
      ? ([props.modelValue!.lng, props.modelValue!.lat] as [number, number])
      : defaultCenter
    map = new AMapNs.Map(hostRef.value, {
      zoom: props.zoom ?? defaultZoom,
      center,
      viewMode: '2D',
    })
    map.addControl(new AMapNs.Scale())
    map.on('click', onMapClick)
    await waitForAmapAuth(map, hostRef.value)
    // 回显已选点
    if (props.modelValue) {
      placeMarker(props.modelValue.lng, props.modelValue.lat)
    }
  } catch (err) {
    error.value = err instanceof Error ? err.message : '高德地图加载失败'
  } finally {
    loading.value = false
  }
}

function onMapClick(e: any) {
  if (!e || !e.lnglat) return
  const lng = e.lnglat.getLng()
  const lat = e.lnglat.getLat()
  handlePick(lng, lat)
}

async function handlePick(lng: number, lat: number) {
  placeMarker(lng, lat)
  picked.value = { lng, lat }
  emit('update:modelValue', picked.value)
  // 调用后端 transform 接口获取 schematic x/y
  try {
    const res = await transformGeoCoordinates({ longitude: lng, latitude: lat })
    if (res.data && res.data.parkX != null && res.data.parkY != null) {
      picked.value = { lng, lat, x: res.data.parkX, y: res.data.parkY }
      emit('update:modelValue', picked.value)
    }
  } catch {
    // transform 失败时仅保留 lng/lat
  }
}

function placeMarker(lng: number, lat: number) {
  if (!AMapNs || !map) return
  if (marker) {
    marker.setPosition([lng, lat])
  } else {
    marker = new AMapNs.Marker({
      position: [lng, lat],
      offset: new AMapNs.Pixel(-8, -8),
    })
    marker.setMap(map)
  }
}

onMounted(() => {
  void mountMap()
})

onUnmounted(() => {
  if (marker) {
    marker.setMap(null)
    marker = null
  }
  if (map) {
    map.destroy()
    map = null
  }
})

watch(
  () => props.modelValue,
  (next) => {
    if (!next) return
    picked.value = next
    if (map && AMapNs) {
      placeMarker(next.lng, next.lat)
    }
  },
)
</script>

<style scoped>
.amap-point-picker {
  width: 100%;
  border-radius: 8px;
  border: 1px solid var(--fsd-border, #1f2937);
  overflow: hidden;
  position: relative;
}
.amap-point-picker__host {
  width: 100%;
  height: 320px;
}
.amap-point-picker__overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(11, 16, 24, 0.85);
  color: #9ba8b8;
  font-size: 13px;
}
.amap-point-picker__overlay--error {
  color: #ff7875;
}
.amap-point-picker__info {
  padding: 6px 12px;
  font-size: 12px;
  color: #9ba8b8;
  background: rgba(11, 16, 24, 0.6);
  border-top: 1px solid var(--fsd-border, #1f2937);
}
</style>
