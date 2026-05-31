<template>
  <div class="park-overview-page">
    <AmapGeoMap
      v-if="geoMapAvailable"
      class="overview-map"
      :center="mapCenter"
      :zoom="11"
      :markers="markers"
    />
    <div v-else class="overview-fallback">
      <p>高德 Key 未配置，已回退为列表视图。</p>
      <p class="hint">配置 <code>front/.env.local</code> 后可查看区域地图。</p>
    </div>

    <aside class="overview-panel">
      <h1>多园区总览</h1>
      <p class="subtitle">区域尺度车辆分布</p>
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
      <router-link class="tracking-link" to="/vehicle-tracking">进入车辆监控大屏 →</router-link>
    </aside>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AmapGeoMap from '@/components/map/AmapGeoMap.vue'
import { getParkOverview } from '@/api/park'
import { defaultMapCenter, isAmapConfigured } from '@/maps'
import type { ParkOverviewItem } from '@/types/park'
import type { GeoMapMarker } from '@/maps'

const loading = ref(false)
const overview = ref<ParkOverviewItem[]>([])
const geoMapAvailable = isAmapConfigured()

const mapCenter = computed((): [number, number] => {
  const first = overview.value.find((park) => park.centerLng != null && park.centerLat != null)
  if (first?.centerLng != null && first.centerLat != null) {
    return [Number(first.centerLng), Number(first.centerLat)]
  }
  return defaultMapCenter()
})

const markers = computed((): GeoMapMarker[] =>
  overview.value
    .filter((park) => park.centerLng != null && park.centerLat != null)
    .map((park) => ({
      id: String(park.parkId),
      position: [Number(park.centerLng), Number(park.centerLat)] as [number, number],
      label: `${park.parkName} · ${park.vehicleCount}车`,
    })),
)

onMounted(async () => {
  loading.value = true
  try {
    const response = await getParkOverview()
    overview.value = response.data || []
  } finally {
    loading.value = false
  }
})
</script>

<style scoped lang="less">
.park-overview-page {
  position: relative;
  width: 100%;
  height: calc(100vh - 64px);
  min-height: 520px;
  background: #0d1117;
}

.overview-map {
  width: 100%;
  height: 100%;
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
    color: #7ee787;
  }
}

.overview-panel {
  position: absolute;
  top: 16px;
  right: 16px;
  width: 280px;
  padding: 16px;
  border-radius: 12px;
  background: rgba(13, 17, 23, 0.92);
  border: 1px solid rgba(255, 255, 255, 0.08);
  color: #fff;

  h1 {
    margin: 0;
    font-size: 18px;
  }

  .subtitle {
    margin: 4px 0 12px;
    font-size: 12px;
    color: rgba(255, 255, 255, 0.55);
  }
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

.tracking-link {
  display: inline-block;
  margin-top: 12px;
  font-size: 13px;
  color: #58a6ff;
}
</style>
