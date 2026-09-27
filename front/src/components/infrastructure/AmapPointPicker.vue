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
    <p
      v-if="outOfRange"
      class="amap-point-picker__reject"
      role="alert"
      data-testid="picker-out-of-range"
    >
      这里不在可下单范围内，请在亮色区域里选点 —— 刚才那个点没有采用，当前选点未改动
    </p>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { getMapConfig } from '@/maps/config'
import { waitForAmapAuth } from '@/maps/amapAuth'
import { isOrderablePoint, type ServiceAreaShape } from '@/maps/parkGeoMapLayers'
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
    /**
     * 可下单范围（来自 `t_park_geofence`）。给了就在图上画出受理区、并把范围外的点选**挡下来**；
     * 为 null / 空表示没有范围数据 —— 那时不拦（取数故障不该把用户挡在门外，提交时后端还会判一次）。
     */
    serviceAreas?: ServiceAreaShape | null
  }>(),
  {
    zoom: 16,
    modelValue: null,
    serviceAreas: null,
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
let areaPolygons: any[] = []
let rejectPin: any = null
const outOfRange = ref(false)

/** 把可下单范围画出来。`bubble` 必须开，否则点在多边形上时 map 的 click 收不到。 */
function drawServiceAreas() {
  if (!AMapNs || !map) return
  areaPolygons.forEach((polygon) => polygon.setMap(null))
  areaPolygons = []
  const shape = props.serviceAreas
  if (!shape) return
  const add = (path: [number, number][], color: string, fillOpacity: number) => {
    const polygon = new AMapNs.Polygon({
      path,
      strokeColor: color,
      strokeWeight: 2,
      fillColor: color,
      fillOpacity,
      bubble: true,
      zIndex: 10,
    })
    polygon.setMap(map)
    areaPolygons.push(polygon)
  }
  shape.allowed.forEach((path) => add(path, '#2DE08A', 0.12))
  shape.excluded.forEach((path) => add(path, '#FF5C7C', 0.22))
}

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
    const center: [number, number] = props.center
      ?? (props.modelValue
        ? ([props.modelValue.lng, props.modelValue.lat] as [number, number])
        : defaultCenter)
    map = new AMapNs.Map(hostRef.value, {
      zoom: props.zoom ?? defaultZoom,
      center,
      viewMode: '2D',
    })
    map.addControl(new AMapNs.Scale())
    map.on('click', onMapClick)
    await waitForAmapAuth(map, hostRef.value)
    drawServiceAreas()
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
  const shape = props.serviceAreas
  if (shape && !isOrderablePoint(shape, [lng, lat])) {
    // 范围外**不改**已选点：只把这一针插在刚才点的地方，并说明它没被采用。
    // 原来这里是"照收，等提交时被后端拒"—— 用户要按一次提交才知道点错了。
    outOfRange.value = true
    placeRejectPin(lng, lat)
    return
  }
  outOfRange.value = false
  clearRejectPin()
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

function clearRejectPin() {
  if (rejectPin) {
    rejectPin.setMap(null)
    rejectPin = null
  }
}

function placeRejectPin(lng: number, lat: number) {
  if (!AMapNs || !map) return
  if (!rejectPin) {
    rejectPin = new AMapNs.Marker({
      position: [lng, lat],
      content: '<span style="display:inline-block;width:18px;height:18px;line-height:16px;text-align:center;'
        + 'border-radius:50%;background:#FF5C7C;color:#0b1018;font-weight:700">!</span>',
      offset: new AMapNs.Pixel(-9, -9),
    })
    rejectPin.setMap(map)
    return
  }
  rejectPin.setPosition([lng, lat])
}

onMounted(() => {
  void mountMap()
})

onUnmounted(() => {
  clearRejectPin()
  areaPolygons.forEach((polygon) => polygon.setMap(null))
  areaPolygons = []
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
  () => props.serviceAreas,
  () => drawServiceAreas(),
)

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
.amap-point-picker__reject {
  margin: 0;
  padding: 8px 12px;
  font-size: 12px;
  color: #ff7875;
  background: rgba(255, 92, 124, 0.12);
  border-top: 1px solid rgba(255, 92, 124, 0.4);
}
</style>
