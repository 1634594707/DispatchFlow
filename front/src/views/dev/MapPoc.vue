<template>
  <div class="map-poc-page">
    <header class="map-poc-header">
      <div>
        <h1>高德地图 · 叠石桥家纺片区</h1>
        <p>
          仿找家纺产业带短驳场景（电商 ERP 下单 + AI 无人快递车）·
          {{ TEXTILE_PARK_GEO.label }}
        </p>
      </div>
      <div class="map-poc-tags">
        <a-tag color="blue">{{ vehicleCount }} 辆 mock 车</a-tag>
        <a-tag :color="configured ? 'success' : 'warning'">
          {{ configured ? 'Key 已配置' : 'Key 未配置' }}
        </a-tag>
      </div>
    </header>
    <AmapGeoMap class="map-poc-map" :markers="vehicleMarkers" :zoom="15" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import AmapGeoMap from '@/components/map/AmapGeoMap.vue'
import { getParkVehicles } from '@/api/park'
import { isAmapConfigured, parkXYToGcj02, TEXTILE_PARK_GEO } from '@/maps'
import type { GeoMapMarker } from '@/maps'
import type { ParkVehicleSnapshot } from '@/types/park'

const configured = isAmapConfigured()
const vehicleCount = ref(0)
const vehicleMarkers = ref<GeoMapMarker[]>([])
let pollTimer: ReturnType<typeof setInterval> | null = null

function toMarker(vehicle: ParkVehicleSnapshot): GeoMapMarker {
  const position: [number, number] =
    vehicle.longitude != null && vehicle.latitude != null
      ? [Number(vehicle.longitude), Number(vehicle.latitude)]
      : parkXYToGcj02(vehicle.x, vehicle.y)
  return {
    id: String(vehicle.vehicleId),
    position,
    label: `${vehicle.vehicleCode} · ${vehicle.batteryLevel}%`,
  }
}

async function refreshVehicles() {
  try {
    const res = await getParkVehicles()
    const list = res.data ?? []
    vehicleCount.value = list.length
    vehicleMarkers.value = list.map(toMarker)
  } catch {
    vehicleCount.value = 0
    vehicleMarkers.value = []
  }
}

onMounted(() => {
  void refreshVehicles()
  pollTimer = setInterval(() => {
    void refreshVehicles()
  }, 3000)
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
})
</script>

<style scoped lang="less">
.map-poc-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  height: calc(100vh - 120px);
  min-height: 480px;
}

.map-poc-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;

  h1 {
    margin: 0 0 4px;
    font-size: 20px;
  }

  p {
    margin: 0;
    color: rgba(255, 255, 255, 0.55);
    font-size: 13px;
  }
}

.map-poc-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.map-poc-map {
  flex: 1;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  overflow: hidden;
}
</style>
