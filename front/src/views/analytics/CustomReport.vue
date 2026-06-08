<template>
  <PageContainer title="自定义报表" subtitle="选择指标、时间范围和对比维度生成运营报表">
    <a-card size="small" style="margin-bottom: 16px">
      <a-form layout="inline" :model="form">
        <a-form-item label="指标">
          <a-select
            v-model:value="form.metrics"
            mode="multiple"
            placeholder="选择指标"
            style="min-width: 300px"
            :options="metricOptions"
          />
        </a-form-item>
        <a-form-item label="时间范围">
          <a-range-picker v-model:value="form.dateRange" value-format="YYYY-MM-DD" />
        </a-form-item>
        <a-form-item label="对比维度">
          <a-select v-model:value="form.dimension" placeholder="不分组" allow-clear style="min-width: 140px">
            <a-select-option value="park">按园区</a-select-option>
            <a-select-option value="vehicleType">按车辆类型</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item>
          <a-button type="primary" :loading="loading" @click="handleGenerate">生成报表</a-button>
          <a-button style="margin-left: 8px" @click="handleReset">重置</a-button>
        </a-form-item>
      </a-form>
    </a-card>

    <a-spin :spinning="loading">
      <template v-if="report">
        <a-card title="汇总数据" size="small" style="margin-bottom: 16px">
          <a-descriptions :column="4" size="small" bordered>
            <a-descriptions-item v-for="(val, key) in report.summary" :key="key" :label="metricLabel(key)">
              {{ formatValue(val) }}
            </a-descriptions-item>
          </a-descriptions>
        </a-card>

        <a-card v-if="report.details && report.details.length > 0" title="维度明细" size="small">
          <a-table
            size="small"
            row-key="dimensionValue"
            :data-source="report.details"
            :columns="detailColumns"
            :pagination="{ pageSize: 20 }"
          />
        </a-card>
      </template>

      <a-empty v-else-if="!loading" description="选择指标后点击「生成报表」查看结果" />
    </a-spin>
  </PageContainer>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import PageContainer from '@/components/common/PageContainer.vue'
import { generateCustomReport, type CustomReportResponse } from '@/api/analytics'
import { useParkScopeStore } from '@/stores/parkScope'
import dayjs from 'dayjs'

const parkScope = useParkScopeStore()
const loading = ref(false)
const report = ref<CustomReportResponse | null>(null)

const metricOptions = [
  { value: 'orderTotal', label: '订单总量' },
  { value: 'orderCompleted', label: '已完成订单' },
  { value: 'orderCompletionRate', label: '订单完成率' },
  { value: 'taskTotal', label: '任务总量' },
  { value: 'taskSuccess', label: '成功任务' },
  { value: 'taskSuccessRate', label: '任务成功率' },
  { value: 'exceptionCount', label: '异常总数' },
  { value: 'openExceptionCount', label: '未关闭异常' },
  { value: 'resolvedExceptionCount', label: '已解决异常' },
  { value: 'avgResolutionMinutes', label: '平均处理时长(分)' },
  { value: 'avgTaskDurationMinutes', label: '平均任务时长(分)' },
  { value: 'avgCompletionMinutes', label: '完成均时(分)' },
  { value: 'vehicleUtilizationRate', label: '车辆利用率(%)' },
]

const labelMap = Object.fromEntries(metricOptions.map((o) => [o.value, o.label]))

function metricLabel(key: string): string {
  return labelMap[key] || key
}

const form = ref({
  metrics: ['orderTotal', 'orderCompleted', 'taskSuccess', 'exceptionCount'],
  dateRange: [dayjs().subtract(6, 'day').format('YYYY-MM-DD'), dayjs().format('YYYY-MM-DD')] as [string, string],
  dimension: undefined as string | undefined,
})

const detailColumns = computed(() => {
  const cols: any[] = [
    { title: form.value.dimension === 'park' ? '园区' : '车辆类型', dataIndex: 'dimensionName', width: 120, fixed: 'left' as const },
  ]
  for (const m of form.value.metrics) {
    cols.push({ title: metricLabel(m), dataIndex: m, width: 130 })
  }
  return cols
})

function formatValue(val: any): string {
  if (typeof val === 'number') {
    return Number.isInteger(val) ? String(val) : val.toFixed(1)
  }
  return String(val ?? '-')
}

async function handleGenerate() {
  if (!form.value.metrics.length) return
  loading.value = true
  try {
    const res = await generateCustomReport(
      {
        metrics: form.value.metrics,
        dateRange: form.value.dateRange,
        dimension: form.value.dimension || null,
      },
      parkScope.selectedParkId,
    )
    report.value = res.data
  } finally {
    loading.value = false
  }
}

function handleReset() {
  form.value = {
    metrics: ['orderTotal', 'orderCompleted', 'taskSuccess', 'exceptionCount'],
    dateRange: [dayjs().subtract(6, 'day').format('YYYY-MM-DD'), dayjs().format('YYYY-MM-DD')] as [string, string],
    dimension: undefined,
  }
  report.value = null
}
</script>