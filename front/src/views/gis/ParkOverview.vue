<template>
  <div class="park-overview-page">
    <AmapGeoMap
      v-if="geoMapAvailable"
      class="overview-map"
      :center="mapCenter"
      :zoom="15"
      :markers="geoMarkers"
      :polygons="geoPolygons"
      :polylines="geoPolylines"
      :circles="geoCircles"
    />
    <div v-if="geoMapAvailable && routeWarning" class="route-anomaly-banner">
      {{ routeWarning }}
    </div>
    <div v-else class="overview-fallback">
      <p>高德 Key 未配置，已回退为列表视图。</p>
      <p class="hint">配置 <code>front/.env.local</code> 后可查看 L1 试点地图与道路轨迹。</p>
    </div>

    <aside class="overview-panel">
      <h1>找家纺试点总览</h1>
      <p class="subtitle">{{ ZJF_PILOT_GEO.label }} · L1 1570m×470m</p>
      <div class="fleet-stats">
        <span>全网 {{ ZJF_FLEET_STATS.fleetSize }} 辆</span>
        <span>试点 {{ geoVehicles.length }} 辆</span>
      </div>
      <a-spin :spinning="loading">
        <div v-for="park in overview" :key="park.parkId" class="park-card">
          <div class="park-card-head">
            <strong>{{ park.parkName }}</strong>
            <span class="park-code">{{ park.parkCode }}</span>
          </div>
          <div class="park-stats">
            <span>{{ park.vehicleCount }} 车</span>
            <span>{{ park.onlineCount }} 在线</span>
            <span>{{ park.busyCount }} 执行</span>
          </div>
        </div>
      </a-spin>
      <div class="panel-links">
        <router-link class="tracking-link" to="/vehicle-tracking">进入监控大屏 →</router-link>
        <router-link class="tracking-link secondary" to="/mobile/order">移动下单 →</router-link>
      </div>
    </aside>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import AmapGeoMap from '@/components/map/AmapGeoMap.vue'
import { getParkGeofences, getParkOverview, getParkOrders, getParkVehicles } from '@/api/park'
import {
  buildGeofencePolygons,
  buildGeoPolylines,
  buildL0CoverageCircles,
  buildVehicleGeoMarkers,
  isAmapConfigured,
  pilotMapCenter,
  ZJF_FLEET_STATS,
  ZJF_PILOT_GEO,
} from '@/maps'
import { routeAnomalyWarning } from '@/maps/routeValidation'
import { filterGeoDeliverySimVehicles } from '@/maps/stationLayers'
import type { ParkGeofence, ParkOrderSnapshot, ParkOverviewItem, ParkVehicleSnapshot } from '@/types/park'

const loading = ref(false)
const overview = ref<ParkOverviewItem[]>([])
const vehicles = ref<ParkVehicleSnapshot[]>([])
const parkOrders = ref<ParkOrderSnapshot[]>([])
const parkGeofences = ref<ParkGeofence[]>([])
const geoMapAvailable = isAmapConfigured()
let pollTimer: ReturnType<typeof setInterval> | null = null

const mapCenter = computed((): [number, number] => {
  const first = overview.value.find((park) => park.centerLng != null && park.centerLat != null)
  if (first?.centerLng != null && first?.centerLat != null) {
    return [Number(first.centerLng), Number(first.centerLat)]
  }
  return pilotMapCenter()
})

const geoVehicles = computed(() => filterGeoDeliverySimVehicles(vehicles.value))

const geoMarkers = computed(() => buildVehicleGeoMarkers(geoVehicles.value))

const geoPolygons = computed(() => buildGeofencePolygons(parkGeofences.value))

const geoPolylines = computed(() => buildGeoPolylines(geoVehicles.value, parkOrders.value))

const geoCircles = computed(() => buildL0CoverageCircles())

const routeWarning = computed(() => routeAnomalyWarning(geoVehicles.value))

async function refreshOverviewPanel() {
  const response = await getParkOverview()
  overview.value = response.data || []
}

async function refreshMapData() {
  const [vehicleRes, orderRes, fenceRes] = await Promise.all([
    getParkVehicles(),
    getParkOrders(),
    getParkGeofences(),
  ])
  vehicles.value = vehicleRes.data || []
  parkOrders.value = orderRes.data || []
  parkGeofences.value = fenceRes.data || []
}

async function refreshAll() {
  await Promise.all([refreshOverviewPanel(), refreshMapData()])
}

onMounted(async () => {
  loading.value = true
  try {
    await refreshAll()
  } finally {
    loading.value = false
  }
  pollTimer = setInterval(() => {
    void refreshAll()
  }, 3000)
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
})
</script>

<style scoped lang="less">
.park-overview-page {
  position: relative;
  width: 100%;
  height: calc(100vh - 64px);
  min-height: 520px;
  background: var(--fsd-bg-deep);
}

.overview-map {
  width: 100%;
  height: 100%;
}

.route-anomaly-banner {
  position: absolute;
  top: 12px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 20;
  padding: 8px 16px;
  border-radius: 6px;
  background: rgba(255, 77, 109, 0.92);
  color: #fff;
  font-size: 13px;
  pointer-events: none;
  max-width: min(92vw, 520px);
  text-align: center;
}

.overview-fallback {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: rgba(255, 255, 255, 0.72);

  .hint {
    font-size: 12px;
    color: rgba(255, 255, 255, 0.45);
  }

  code {
    color: var(--fsd-success);
  }
}

.overview-panel {
  position: absolute;
  top: 16px;
  right: 16px;
  width: 300px;
  padding: 16px;
  border-radius: 12px;
  background: rgba(11, 16, 24, 0.92);
  border: 1px solid rgba(255, 255, 255, 0.08);
  color: #fff;

  h1 {
    margin: 0;
    font-size: 18px;
  }

  .subtitle {
    margin: 4px 0 8px;
    font-size: 12px;
    color: rgba(255, 255, 255, 0.55);
  }
}

.fleet-stats {
  display: flex;
  gap: 10px;
  margin-bottom: 12px;
  font-size: 11px;
  color: rgba(100, 149, 237, 0.9);
}

.park-card {
  padding: 10px 0;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);

  &:last-child {
    border-bottom: none;
  }
}

.park-card-head {
  display: flex;
  justify-content: space-between;
  gap: 8px;
}

.park-code {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.45);
}

.park-stats {
  display: flex;
  gap: 10px;
  margin-top: 6px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.68);
}

.panel-links {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 12px;
}

.tracking-link {
  display: inline-block;
  font-size: 13px;
  color: var(--fsd-accent);
  text-decoration: none;

  &.secondary {
    color: rgba(255, 255, 255, 0.55);
  }
}
</style>
