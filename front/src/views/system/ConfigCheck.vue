<template>
  <PageContainer title="试点配置自检" subtitle="高德 JS · Web 服务 · 移动 Key · 贴路 health">
    <template #actions>
      <a-button :loading="loading" @click="load">
        <ReloadOutlined /> 刷新
      </a-button>
    </template>

    <a-spin :spinning="loading">
      <a-alert
        v-if="overallReady"
        type="success"
        show-icon
        message="演示关键项已就绪"
        description="可打开车辆监控（短驳地理）与移动下单进行录屏验收。"
        class="overall-alert"
      />
      <a-alert
        v-else
        type="warning"
        show-icon
        message="部分配置缺失"
        description="请按下方卡片补齐 front/.env.local 或后端环境变量后刷新。"
        class="overall-alert"
      />

      <div class="check-grid">
        <article v-for="item in checks" :key="item.id" class="check-card" :class="`status-${item.status}`">
          <div class="card-head">
            <h3>{{ item.title }}</h3>
            <a-tag :color="tagColor(item.status)">{{ statusLabel(item.status) }}</a-tag>
          </div>
          <p class="card-detail">{{ item.detail }}</p>
          <p v-if="item.hint" class="card-hint">{{ item.hint }}</p>
        </article>
      </div>

      <a-card size="small" title="配置指引" class="guide-card">
        <ul class="guide-list">
          <li>前端地图：复制 <code>front/.env.example</code> → <code>front/.env.local</code>，填写 <code>VITE_AMAP_KEY</code> 与 <code>VITE_AMAP_SECURITY_CODE</code></li>
          <li>后端驾车路径：设置环境变量 <code>FSD_AMAP_WEB_SERVICE_KEY</code>（或依赖本地 OSM 路网）</li>
          <li>移动下单：<code>VITE_MOBILE_API_KEY</code> 与 Flyway V25 种子 Key 一致</li>
          <li>贴路验收：<code>.\scripts\m8-r7-accept.ps1 -JsonReport</code></li>
        </ul>
      </a-card>
    </a-spin>
  </PageContainer>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ReloadOutlined } from '@ant-design/icons-vue'
import PageContainer from '@/components/common/PageContainer.vue'
import { getRoadRouteHealth, type RoadRouteHealth } from '@/api/park'
import { getMapConfig, isAmapConfigured } from '@/maps'

type CheckStatus = 'ok' | 'warn' | 'fail'

interface ConfigCheckItem {
  id: string
  title: string
  status: CheckStatus
  detail: string
  hint?: string
}

const loading = ref(false)
const roadHealth = ref<RoadRouteHealth | null>(null)
const roadHealthError = ref('')

const mapConfig = getMapConfig()
const mobileKey = (import.meta.env.VITE_MOBILE_API_KEY as string | undefined)?.trim() || ''

const checks = computed<ConfigCheckItem[]>(() => {
  const jsKeyOk = !!mapConfig.amapKey
  const secOk = !!mapConfig.amapSecurityCode
  const mobileOk = mobileKey.length >= 8
  const routeOk = roadHealth.value != null
    && (roadHealth.value.amapDriving || roadHealth.value.localGraph)

  return [
    {
      id: 'amap-js',
      title: '高德 JS API Key',
      status: jsKeyOk ? 'ok' : 'fail',
      detail: jsKeyOk ? `已配置（${maskSecret(mapConfig.amapKey)}）` : '未配置 VITE_AMAP_KEY',
      hint: 'Web 端地图 Marker / 短驳地理场景',
    },
    {
      id: 'amap-sec',
      title: '高德安全密钥',
      status: secOk ? 'ok' : 'fail',
      detail: secOk ? '已配置 VITE_AMAP_SECURITY_CODE' : '未配置安全密钥',
    },
    {
      id: 'amap-bundle',
      title: '前端地图可用',
      status: isAmapConfigured() ? 'ok' : 'fail',
      detail: isAmapConfigured() ? 'AMAP 双 Key 就绪' : 'Key 或安全码缺失',
    },
    {
      id: 'mobile-key',
      title: '移动下单 API Key',
      status: mobileOk ? 'ok' : 'warn',
      detail: mobileOk ? `VITE_MOBILE_API_KEY=${maskSecret(mobileKey)}` : '未配置，移动页需手动输入 Key',
      hint: '演示默认 ZJF-MOBILE-DEMO-2026（见 V25 迁移）',
    },
    {
      id: 'road-health',
      title: '贴路路径 health',
      status: routeOk ? 'ok' : roadHealthError.value ? 'fail' : 'warn',
      detail: roadHealth.value
        ? [
            roadHealth.value.amapDriving ? 'AMAP 驾车' : null,
            roadHealth.value.localGraph ? `本地路网 ${roadHealth.value.localGraphSegments ?? 0} 段` : null,
            `fallback=${roadHealth.value.fallbackCount ?? 0}`,
          ].filter(Boolean).join(' · ') || roadHealth.value.detail || '—'
        : roadHealthError.value || '等待检测…',
      hint: '需管理端登录；无 Web Key 时应 localGraph=true',
    },
  ]
})

const overallReady = computed(() =>
  checks.value.every(item => item.status === 'ok' || (item.id === 'mobile-key' && item.status === 'warn')),
)

function maskSecret(value: string) {
  if (value.length <= 6) return '***'
  return `${value.slice(0, 4)}…${value.slice(-2)}`
}

function statusLabel(status: CheckStatus) {
  if (status === 'ok') return '通过'
  if (status === 'warn') return '可选'
  return '缺失'
}

function tagColor(status: CheckStatus) {
  if (status === 'ok') return 'success'
  if (status === 'warn') return 'warning'
  return 'error'
}

async function load() {
  loading.value = true
  roadHealthError.value = ''
  try {
    const res = await getRoadRouteHealth()
    roadHealth.value = res.data
  } catch (e) {
    roadHealth.value = null
    roadHealthError.value = e instanceof Error ? e.message : 'health 接口不可用'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped lang="less">
.overall-alert {
  margin-bottom: 16px;
}

.check-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 14px;
  margin-bottom: 16px;
}

.check-card {
  padding: 16px;
  border-radius: var(--fsd-radius-lg);
  border: 1px solid var(--fsd-border);
  background: var(--fsd-bg-elevated);
}

.check-card.status-fail {
  border-color: rgba(255, 61, 113, 0.4);
}

.check-card.status-warn {
  border-color: rgba(255, 176, 32, 0.4);
}

.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;

  h3 {
    margin: 0;
    font-size: 15px;
  }
}

.card-detail {
  margin: 0;
  font-size: 13px;
  color: var(--fsd-text-secondary);
}

.card-hint {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--fsd-text-tertiary);
}

.guide-list {
  margin: 0;
  padding-left: 18px;
  font-size: 13px;
  color: var(--fsd-text-secondary);
  line-height: 1.7;

  code {
    font-size: 12px;
    color: var(--fsd-accent);
  }
}
</style>
