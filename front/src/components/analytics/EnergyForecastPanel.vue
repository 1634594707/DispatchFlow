<template>
  <section class="energy-panel">
    <header class="panel-head">
      <div class="head-main">
        <h3>补能需求预测</h3>
        <span class="panel-sub">ALG-FC · 离线训练写入 t_energy_forecast，此处只读</span>
      </div>
      <div class="meta-chips">
        <span class="chip">预测日期<b>{{ forecast?.forecastDate || '-' }}</b></span>
        <span class="chip">阈值<b>{{ fmt(forecast?.pressureThreshold) }}</b></span>
        <span class="chip">有效期<b>{{ forecast?.maxDataAgeHours ?? '-' }}h</b></span>
      </div>
    </header>

    <a-spin :spinning="loading">
      <!-- 关闭 / 无数据 -->
      <div v-if="!forecast" class="state-block">
        <span>暂无预测数据。</span>
      </div>
      <div v-else-if="!forecast.enabled" class="state-block">
        <span>补能预测读取已关闭（fsd.energy-forecast.enabled=false），派单按纯阈值补能策略运行。</span>
      </div>
      <div v-else-if="!forecast.anyData" class="state-block">
        <span
          >{{ forecast.forecastDate }} 无预测数据；派单侧按纯阈值补能策略运行（缺失模型数据不改变安全语义）。</span
        >
      </div>

      <template v-else>
        <div v-if="forecast.anyStale" class="notice notice-warn">
          <strong>存在超期预测</strong>
          <span
            >至少一个站点最新数据生成时间已超出 {{ forecast.maxDataAgeHours }} 小时有效期。派单侧<b>不会</b>使用这些预测，补能决策已回退为纯阈值策略；需重新运行补能预测任务后才会重新生效。</span
          >
        </div>

        <div v-if="stations.length > 1" class="station-switch">
          <a-radio-group v-model:value="selectedId" size="small" button-style="solid">
            <a-radio-button v-for="s in stations" :key="s.stationId" :value="s.stationId">
              {{ s.stationCode || `站点 ${s.stationId}` }}
            </a-radio-button>
          </a-radio-group>
        </div>
        <div v-else-if="activeStation" class="station-single">
          站点 <b>{{ activeStation.stationCode || `#${activeStation.stationId}` }}</b>
        </div>

        <template v-if="activeStation">
          <div class="chart-grid">
            <HourlyForecastChart
              title="到站需求预测（次/小时）"
              unit="次/小时"
              :series="demandSeries"
              :current-hour="currentHour"
            />
            <HourlyForecastChart
              title="到站压力（滑动窗口 P95）"
              :series="pressureSeries"
              :reference="thresholdReference"
              :current-hour="currentHour"
            />
          </div>

          <p class="panel-note">
            需求（次/小时）与压力（压力计数值）量纲不同，故分图绘制；缺口处折线断开表示该小时无预测行，未做插补。
            压力图的虚线为配置阈值，仅作对照——派单侧使用该阈值判断是否错峰推迟返充。
          </p>

          <a-table
            size="small"
            row-key="stationId"
            :pagination="false"
            :data-source="stations"
            :columns="columns"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'station'">
                {{ record.stationCode || `#${record.stationId}` }}
              </template>
              <template v-else-if="column.key === 'coverage'">
                <span :class="{ dim: record.hourCount < 24 }">{{ record.hourCount }}/24</span>
              </template>
              <template v-else-if="column.key === 'peakDemand'">
                {{ fmt(record.peakDemandP90) }}
              </template>
              <template v-else-if="column.key === 'peakPressure'">
                {{ fmt(record.peakPressureP95) }}
                <span class="dim">@ {{ record.peakHourOfDay }}:00</span>
              </template>
              <template v-else-if="column.key === 'threshold'">
                <span class="dim">{{ thresholdLabel(record) }}</span>
              </template>
              <template v-else-if="column.key === 'generatedAt'">
                {{ formatTime(record.generatedAt) }}
              </template>
              <template v-else-if="column.key === 'state'">
                <span class="state-dot" :class="record.stale ? 'warn' : 'ok'"></span>
                <span :class="record.stale ? 'state-warn-text' : 'state-ok-text'">{{
                  record.stale ? '已超期' : '有效'
                }}</span>
              </template>
            </template>
          </a-table>
        </template>
      </template>
    </a-spin>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import HourlyForecastChart from '@/components/analytics/HourlyForecastChart.vue'
import type { HourlySeriesInput } from '@/components/analytics/HourlyForecastChart.vue'
import type {
  AnalyticsEnergyForecast,
  AnalyticsEnergyForecastHourPoint,
  AnalyticsEnergyForecastStation,
} from '@/types/analytics'

const props = withDefaults(
  defineProps<{
    forecast: AnalyticsEnergyForecast | null
    loading?: boolean
  }>(),
  { loading: false },
)

const selectedId = ref<number | null>(null)

const stations = computed<AnalyticsEnergyForecastStation[]>(() => props.forecast?.stations ?? [])

const activeStation = computed<AnalyticsEnergyForecastStation | null>(() => {
  const list = stations.value
  if (!list.length) return null
  return list.find((s) => s.stationId === selectedId.value) ?? list[0]
})

watch(
  () => props.forecast,
  () => {
    selectedId.value = null
  },
)

function fmt(v: number | null | undefined) {
  if (v == null || !Number.isFinite(Number(v))) return '-'
  const n = Number(v)
  const abs = Math.abs(n)
  if (abs >= 1000) return n.toFixed(0)
  if (abs >= 100) return n.toFixed(1).replace(/\.0$/, '')
  if (abs >= 10) return n.toFixed(2).replace(/\.?0+$/, '')
  return n.toFixed(3).replace(/\.?0+$/, '')
}

/** 从 ISO 字符串里直接取服务端小时，避免时区换算把小时搬错。 */
function hourFromIso(iso?: string | null) {
  if (!iso) return null
  const m = /T(\d{2}):/.exec(iso)
  if (m) return Number(m[1])
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? null : d.getHours()
}

function formatTime(iso?: string | null) {
  if (!iso) return '-'
  const m = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(iso)
  if (m) return `${m[2]}-${m[3]} ${m[4]}:${m[5]}`
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleString('zh-CN')
}

/** 按 hourOfDay 落位到 24 个槽位，缺测保持 null（不插补）。 */
function slot(
  station: AnalyticsEnergyForecastStation,
  pick: (point: AnalyticsEnergyForecastHourPoint) => number,
) {
  const out: (number | null)[] = new Array(24).fill(null)
  for (const point of station.hours ?? []) {
    const h = Number(point.hourOfDay)
    if (Number.isInteger(h) && h >= 0 && h <= 23) {
      const v = Number(pick(point))
      out[h] = Number.isFinite(v) ? v : null
    }
  }
  return out
}

const currentHour = computed(() => hourFromIso(props.forecast?.serverTime))

const demandSeries = computed<HourlySeriesInput[]>(() => {
  const st = activeStation.value
  if (!st) return []
  return [
    {
      key: 'demandP90',
      label: 'P90 上界',
      color: 'var(--fsd-accent)',
      kind: 'area',
      values: slot(st, (p) => p.demandP90),
    },
    {
      key: 'demandP50',
      label: 'P50 中位',
      color: 'var(--fsd-accent-strong)',
      kind: 'line',
      values: slot(st, (p) => p.demandP50),
    },
  ]
})

const pressureSeries = computed<HourlySeriesInput[]>(() => {
  const st = activeStation.value
  if (!st) return []
  return [
    {
      key: 'pressureP95',
      label: 'pressureP95',
      color: 'var(--fsd-warning)',
      kind: 'area',
      values: slot(st, (p) => p.pressureP95),
    },
  ]
})

const thresholdReference = computed(() => {
  const threshold = props.forecast?.pressureThreshold
  if (threshold == null || !Number.isFinite(Number(threshold)) || Number(threshold) <= 0) return null
  return { value: Number(threshold), label: `阈值 ${fmt(threshold)}` }
})

/** 中性对照文案：只陈述与配置阈值的大小关系，不代替派单侧判定。 */
function thresholdLabel(station: AnalyticsEnergyForecastStation) {
  const threshold = props.forecast?.pressureThreshold
  if (threshold == null) return '-'
  return station.pressureThresholdExceeded ? `峰值 ≥ 阈值 ${fmt(threshold)}` : `峰值 < 阈值 ${fmt(threshold)}`
}

const columns = [
  { title: '站点', key: 'station', width: 120 },
  { title: '小时覆盖', key: 'coverage', width: 90 },
  { title: 'P90 峰值', key: 'peakDemand', width: 100 },
  { title: '压力峰值', key: 'peakPressure', width: 150 },
  { title: '阈值对照', key: 'threshold', width: 140 },
  { title: '样本数', dataIndex: 'sampleCount', key: 'sampleCount', width: 90 },
  { title: '数据时间', key: 'generatedAt', width: 120 },
  { title: '状态', key: 'state', width: 90 },
]
</script>

<style scoped lang="less">
.energy-panel {
  background: var(--fsd-bg-base);
  border: 1px solid var(--fsd-border);
  border-radius: var(--fsd-radius-lg);
  padding: 20px;
  margin-bottom: 16px;
}

.panel-head {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.head-main {
  display: flex;
  flex-direction: column;
  gap: 4px;

  h3 {
    margin: 0;
    font-size: var(--fsd-text-md);
    color: var(--fsd-text-primary);
  }
}

.panel-sub {
  font-size: var(--fsd-text-xs);
  color: var(--fsd-text-tertiary);
}

.meta-chips {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.chip {
  font-size: var(--fsd-text-xs);
  color: var(--fsd-text-tertiary);
  background: var(--fsd-surface-raised);
  border: 1px solid var(--fsd-border);
  border-radius: var(--fsd-radius-sm);
  padding: 3px 8px;

  b {
    margin-left: 6px;
    color: var(--fsd-text-primary);
    font-family: var(--fsd-font-mono);
    font-weight: var(--fsd-font-semibold);
  }
}

.state-block {
  padding: 20px;
  border-radius: var(--fsd-radius-md);
  background: var(--fsd-surface-raised);
  border: 1px solid var(--fsd-border);
  color: var(--fsd-text-tertiary);
  font-size: var(--fsd-text-sm);
}

.notice {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  padding: 12px 14px;
  border-radius: var(--fsd-radius-md);
  font-size: var(--fsd-text-sm);
  margin-bottom: 14px;

  strong {
    flex: 0 0 auto;
  }

  span {
    color: var(--fsd-text-secondary);
    line-height: var(--fsd-leading-snug);
  }
}

.notice-warn {
  background: var(--fsd-warning-bg);
  border: 1px solid rgba(227, 182, 91, 0.32);
  color: var(--fsd-warning);
}

.station-switch {
  margin-bottom: 14px;
}

.station-single {
  margin-bottom: 14px;
  font-size: var(--fsd-text-sm);
  color: var(--fsd-text-secondary);

  b {
    color: var(--fsd-text-primary);
    font-family: var(--fsd-font-mono);
  }
}

.chart-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 18px;
  margin-bottom: 12px;

  @media (min-width: 1200px) {
    grid-template-columns: 1fr 1fr;
  }
}

.panel-note {
  margin: 0 0 16px;
  font-size: var(--fsd-text-xs);
  color: var(--fsd-text-tertiary);
  line-height: var(--fsd-leading-snug);
}

.dim {
  color: var(--fsd-text-tertiary);
  font-size: var(--fsd-text-xs);
}

.state-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  margin-right: 6px;
  vertical-align: middle;

  &.ok {
    background: var(--fsd-success);
  }

  &.warn {
    background: var(--fsd-warning);
  }
}

.state-ok-text {
  color: var(--fsd-success);
  font-size: var(--fsd-text-xs);
}

.state-warn-text {
  color: var(--fsd-warning);
  font-size: var(--fsd-text-xs);
}
</style>
