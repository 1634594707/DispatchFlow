<template>
  <PageContainer title="历史报表" subtitle="浏览、搜索和重新下载已生成的报表">
    <template #actions>
      <a-button @click="router.push('/analytics')">返回运营分析</a-button>
    </template>

    <a-card size="small" style="margin-bottom: 16px">
      <a-space>
        <a-select v-model:value="filterType" placeholder="报表类型" allow-clear style="min-width: 120px" @change="loadHistory">
          <a-select-option value="PDF">PDF</a-select-option>
          <a-select-option value="CSV">CSV</a-select-option>
        </a-select>
        <a-button type="primary" @click="loadHistory">刷新</a-button>
      </a-space>
    </a-card>

    <a-table
      size="small"
      row-key="id"
      :loading="loading"
      :data-source="list"
      :columns="columns"
      :pagination="{ pageSize: 20, showSizeChanger: true, showTotal: (total: number) => `共 ${total} 条` }"
      :scroll="{ x: 900 }"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'reportType'">
          <a-tag :color="record.reportType === 'PDF' ? 'red' : 'green'">{{ record.reportType }}</a-tag>
        </template>
        <template v-if="column.key === 'reportName'">
          <span class="mono">{{ record.reportName }}</span>
        </template>
        <template v-if="column.key === 'generatedAt'">
          <span class="mono">{{ dayjs(record.generatedAt).format('YYYY-MM-DD HH:mm') }}</span>
        </template>
        <template v-if="column.key === 'fileSize'">
          <span>{{ formatFileSize(record.fileSizeBytes) }}</span>
        </template>
        <template v-if="column.key === 'action'">
          <a-button type="link" size="small" @click="handleDownload(record)">重新下载</a-button>
        </template>
      </template>
    </a-table>
  </PageContainer>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import PageContainer from '@/components/common/PageContainer.vue'
import { getAnalyticsPdfUrl, getAnalyticsExportUrl, getReportHistory } from '@/api/analytics'
import type { ReportHistoryItem } from '@/api/analytics'
import { useAuthStore } from '@/stores/auth'
import { ADMIN_AUTH_ENABLED } from '@/config'
import dayjs from 'dayjs'

const router = useRouter()
const authStore = useAuthStore()
const loading = ref(false)
const list = ref<ReportHistoryItem[]>([])
const filterType = ref<string | undefined>(undefined)

const columns = [
  { title: '类型', dataIndex: 'reportType', key: 'reportType', width: 80 },
  { title: '报表名称', dataIndex: 'reportName', key: 'reportName', width: 260 },
  { title: '数据集', dataIndex: 'dataset', key: 'dataset', width: 100 },
  { title: '周期', dataIndex: 'period', key: 'period', width: 80 },
  { title: '生成人', dataIndex: 'generatedBy', key: 'generatedBy', width: 120 },
  { title: '生成时间', dataIndex: 'generatedAt', key: 'generatedAt', width: 160 },
  { title: '大小', dataIndex: 'fileSizeBytes', key: 'fileSize', width: 100 },
  { title: '操作', key: 'action', width: 100, fixed: 'right' as const },
]

function formatFileSize(bytes: number | null | undefined): string {
  if (bytes == null) return '-'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function handleDownload(record: any) {
  let url: string
  if (record.reportType === 'PDF') {
    url = getAnalyticsPdfUrl(record.date, record.parkId)
  } else {
    url = getAnalyticsExportUrl(record.dataset || 'orders', record.period || 'week', record.parkId)
  }
  const tokenQuery = ADMIN_AUTH_ENABLED && authStore.token
    ? `${url.includes('?') ? '&' : '?'}token=${encodeURIComponent(authStore.token)}`
    : ''
  window.open(`${url}${tokenQuery}`, '_blank')
}

async function loadHistory() {
  loading.value = true
  try {
    const res = await getReportHistory(filterType.value, 50, 0)
    list.value = res.data ?? []
  } finally {
    loading.value = false
  }
}

onMounted(loadHistory)
</script>

<style scoped lang="less">
.mono {
  font-family: 'JetBrains Mono', monospace;
  font-size: 13px;
}
</style>