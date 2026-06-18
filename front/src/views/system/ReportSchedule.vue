<template>
  <PageContainer title="定时报表" subtitle="配置 PDF 运营日报邮件 cron 与收件人">
    <div class="toolbar">
      <a-button type="primary" @click="openCreate">新建计划</a-button>
      <a-button @click="load">刷新</a-button>
    </div>
    <a-table :data-source="rows" row-key="id" size="small" :loading="loading" :pagination="false">
      <a-table-column title="Cron" data-index="cronExpression" />
      <a-table-column title="收件人" data-index="recipients" />
      <a-table-column key="parkId" title="园区">
        <template #default="{ record }">{{ record.parkId ?? '全园区' }}</template>
      </a-table-column>
      <!-- V5-S4: 执行状态指示 -->
      <a-table-column key="statusIndicator" title="状态" width="80">
        <template #default="{ record }">
          <span class="status-dot" :class="statusDotClass(record)" :title="statusDotTitle(record)" />
        </template>
      </a-table-column>
      <!-- V5-S4: 执行历史列 -->
      <a-table-column key="lastExecutedAt" title="上次执行" width="170">
        <template #default="{ record }">
          <span v-if="record.lastSentAt">{{ formatTime(record.lastSentAt) }}</span>
          <span v-else class="text-muted">-</span>
        </template>
      </a-table-column>
      <a-table-column key="lastResult" title="结果" width="80">
        <template #default="{ record }">
          <a-tag v-if="getExecutionStats(record)?.lastResult" :color="executionResultColor(getExecutionStats(record)!.lastResult!)">
            {{ executionResultLabel(getExecutionStats(record)!.lastResult!) }}
          </a-tag>
          <span v-else class="text-muted">待执行</span>
        </template>
      </a-table-column>
      <a-table-column key="nextExecutionTime" title="下次执行" width="170">
        <template #default="{ record }">
          <span v-if="getExecutionStats(record)?.nextExecutionTime">{{ formatTime(getExecutionStats(record)!.nextExecutionTime!) }}</span>
          <span v-else class="text-muted">-</span>
        </template>
      </a-table-column>
      <a-table-column key="enabled" title="启用" width="60">
        <template #default="{ record }">{{ record.enabled ? '是' : '否' }}</template>
      </a-table-column>
      <!-- V5-S4: 操作列扩展 -->
      <a-table-column key="actions" title="操作" width="240">
        <template #default="{ record }">
          <a-space>
            <a-button type="link" size="small" @click="edit(record)">编辑</a-button>
            <a-button type="link" size="small" @click="showExecutionHistory(record)">历史</a-button>
            <a-button type="link" size="small" @click="handleTrigger(record)">立即执行</a-button>
            <a-button type="link" size="small" danger @click="remove(record)">删除</a-button>
          </a-space>
        </template>
      </a-table-column>
    </a-table>

    <!-- 编辑/新建表单 -->
    <a-modal v-model:open="modalOpen" title="报表计划" @ok="save">
      <a-form layout="vertical">
        <a-form-item label="Cron 表达式"><a-input v-model:value="form.cronExpression" placeholder="0 0 8 * * MON-FRI" /></a-form-item>
        <a-form-item v-if="form.cronExpression" label="Cron 预览（最近 5 次执行时间）">
          <ul class="cron-preview-list">
            <li v-for="(t, idx) in cronPreviews" :key="idx">{{ formatTime(t) }}</li>
          </ul>
        </a-form-item>
        <a-form-item label="收件人（逗号分隔）"><a-input v-model:value="form.recipients" /></a-form-item>
        <a-form-item label="园区 ID（留空=全园区）"><a-input-number v-model:value="form.parkId" style="width:100%" /></a-form-item>
        <a-form-item label="启用"><a-switch v-model:checked="form.enabled" /></a-form-item>
      </a-form>
    </a-modal>

    <!-- V5-S4: 执行历史抽屉 -->
    <a-drawer
      v-model:open="historyDrawerOpen"
      title="执行历史"
      placement="right"
      width="520"
    >
      <template v-if="historyRecords.length">
        <a-timeline>
          <a-timeline-item
            v-for="rec in historyRecords"
            :key="rec.id"
            :color="executionTimelineColor(rec.result)"
          >
            <div class="history-item">
              <div class="history-item-header">
                <a-tag :color="executionResultColor(rec.result)" size="small">
                  {{ executionResultLabel(rec.result) }}
                </a-tag>
                <span class="history-time">{{ formatTime(rec.executedAt) }}</span>
              </div>
              <div class="history-item-detail">
                <span v-if="rec.durationMs">耗时: {{ rec.durationMs }}ms</span>
                <span v-if="rec.errorMessage" class="history-error">{{ rec.errorMessage }}</span>
                <span v-if="rec.nextExecutionTime">下次: {{ formatTime(rec.nextExecutionTime) }}</span>
              </div>
            </div>
          </a-timeline-item>
        </a-timeline>
      </template>
      <a-empty v-else description="暂无执行记录" />
    </a-drawer>
  </PageContainer>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, computed } from 'vue'
import { message } from 'ant-design-vue'
import dayjs from 'dayjs'
import utc from 'dayjs/plugin/utc'
import PageContainer from '@/components/common/PageContainer.vue'
import { deleteReportSchedule, fetchReportSchedules, upsertReportSchedule, fetchScheduleExecutionHistory, triggerScheduleExecution } from '@/api/reportSchedule'
import type { AdminReportSchedule, ScheduleExecutionRecord, ScheduleExecutionStats } from '@/types/reportSchedule'

dayjs.extend(utc)

const loading = ref(false)
const rows = ref<AdminReportSchedule[]>([])
const modalOpen = ref(false)
const form = reactive<AdminReportSchedule>({ cronExpression: '0 0 8 * * MON-FRI', recipients: '', enabled: false })

/* ---- V5-S4: 执行历史 ---- */
const historyDrawerOpen = ref(false)
const historyRecords = ref<ScheduleExecutionRecord[]>([])
const historyLoading = ref(false)

/** 每个计划对应的执行统计缓存 */
const executionStatsMap = ref<Record<number, ScheduleExecutionStats>>({})

function getExecutionStats(schedule: AdminReportSchedule): ScheduleExecutionStats | undefined {
  if (!schedule.id) return undefined
  return executionStatsMap.value[schedule.id]
}

/** 最近 5 次 Cron 预览 */
const cronPreviews = computed(() => {
  if (!form.cronExpression) return []
  try {
    // 简单模拟：从当前时间起每隔 N 秒生成 5 个时间点
    // 真正实现可引入 cron-parser 库，这里使用 dayjs 简单推算
    const times: string[] = []
    let base = dayjs().add(1, 'minute').startOf('minute')
    // 解析一些简单的 cron 模式
    const parts = form.cronExpression.trim().split(/\s+/)
    if (parts.length >= 5) {
      const minute = parts[0] === '*' ? undefined : parseInt(parts[0])
      const hour = parts[1] === '*' ? undefined : parseInt(parts[1])
      const dayOfMonth = parts[2] === '*' ? undefined : parseInt(parts[2])
      const month = parts[3] === '*' ? undefined : parseInt(parts[3]) - 1
      const dayOfWeek = parts[4] === '*' ? undefined : parseInt(parts[4])
      for (let i = 0; i < 5; i++) {
        let candidate = base.add(i, 'day')
        if (hour !== undefined) candidate = candidate.hour(hour).minute(minute ?? 0)
        else if (minute !== undefined) candidate = candidate.minute(minute)
        if (dayOfMonth !== undefined) candidate = candidate.date(dayOfMonth)
        if (month !== undefined) candidate = candidate.month(month)
        if (dayOfWeek !== undefined) {
          const diff = (dayOfWeek - candidate.day() + 7) % 7
          candidate = candidate.add(diff, 'day')
        }
        times.push(candidate.format('YYYY-MM-DD HH:mm:ss'))
      }
    } else {
      // fallback
      for (let i = 0; i < 5; i++) {
        times.push(base.add(i * 30, 'minute').format('YYYY-MM-DD HH:mm:ss'))
      }
    }
    return times
  } catch {
    return []
  }
})

async function load() {
  loading.value = true
  try {
    rows.value = (await fetchReportSchedules()).data
    // 加载每个计划的执行统计
    await Promise.all(rows.value.map(loadExecutionStats))
  } finally {
    loading.value = false
  }
}

async function loadExecutionStats(schedule: AdminReportSchedule) {
  if (!schedule.id) return
  try {
    const res = await fetchScheduleExecutionHistory(schedule.id)
    const records = res.data.records
    const stats: ScheduleExecutionStats = {
      lastExecutedAt: records[0]?.executedAt,
      lastResult: records[0]?.result,
      nextExecutionTime: records[0]?.nextExecutionTime,
      totalRuns: records.length,
      successCount: records.filter((r) => r.result === 'SUCCESS').length,
      failureCount: records.filter((r) => r.result === 'FAILURE').length,
    }
    executionStatsMap.value[schedule.id] = stats
  } catch {
    // ignore
  }
}

function openCreate() {
  form.id = undefined
  form.cronExpression = '0 0 8 * * MON-FRI'
  form.recipients = ''
  form.parkId = undefined
  form.enabled = false
  modalOpen.value = true
}

function edit(record: AdminReportSchedule) {
  Object.assign(form, record)
  modalOpen.value = true
}

async function save() {
  await upsertReportSchedule(form)
  message.success('已保存')
  modalOpen.value = false
  await load()
}

async function remove(record: AdminReportSchedule) {
  if (!record.id) return
  await deleteReportSchedule(record.id)
  await load()
}

/** V5-S4: 查看执行历史 */
async function showExecutionHistory(record: AdminReportSchedule) {
  if (!record.id) return
  historyLoading.value = true
  historyRecords.value = []
  historyDrawerOpen.value = true
  try {
    const res = await fetchScheduleExecutionHistory(record.id)
    historyRecords.value = res.data.records
  } finally {
    historyLoading.value = false
  }
}

/** V5-S4: 立即执行 */
async function handleTrigger(record: AdminReportSchedule) {
  if (!record.id) return
  try {
    await triggerScheduleExecution(record.id)
    message.success('已触发执行')
    // 刷新状态
    await load()
  } catch {
    message.error('触发执行失败')
  }
}

/** V5-S4: 状态点样式 */
function statusDotClass(record: AdminReportSchedule) {
  const stats = getExecutionStats(record)
  if (!stats?.lastResult) return 'dot-pending'
  if (stats.lastResult === 'SUCCESS') return 'dot-success'
  if (stats.lastResult === 'FAILURE') return 'dot-failure'
  return 'dot-pending'
}

function statusDotTitle(record: AdminReportSchedule) {
  const stats = getExecutionStats(record)
  if (!stats?.lastResult) return '待执行'
  if (stats.lastResult === 'SUCCESS') return '上次成功'
  if (stats.lastResult === 'FAILURE') return '上次失败'
  return '待执行'
}

function executionResultColor(result: string) {
  if (result === 'SUCCESS') return 'success'
  if (result === 'FAILURE') return 'error'
  return 'default'
}

function executionResultLabel(result: string) {
  if (result === 'SUCCESS') return '成功'
  if (result === 'FAILURE') return '失败'
  return '待执行'
}

/** V5-S4: 时间线颜色 */
function executionTimelineColor(result: string) {
  if (result === 'SUCCESS') return 'green'
  if (result === 'FAILURE') return 'red'
  return 'gray'
}

function formatTime(value: string) {
  return dayjs(value).format('YYYY-MM-DD HH:mm:ss')
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; gap: 8px; margin-bottom: 12px; }

.text-muted {
  color: var(--fsd-text-tertiary, #6B7787);
}

/* V5-S4: 状态指示点 */
.status-dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  margin-right: 4px;
}

.status-dot.dot-success {
  background-color: var(--fsd-success);
  box-shadow: 0 0 4px var(--fsd-success);
}

.status-dot.dot-failure {
  background-color: var(--fsd-error);
  box-shadow: 0 0 4px var(--fsd-error);
}

.status-dot.dot-pending {
  background-color: var(--fsd-border, rgba(255, 255, 255, 0.07));
}

/* V5-S4: Cron 预览 */
.cron-preview-list {
  margin: 0;
  padding-left: 16px;
  font-size: 12px;
  color: var(--fsd-text-secondary, #9BA8B8);
  line-height: 1.8;
}

/* V5-S4: 历史抽屉条目 */
.history-item {
  font-size: 13px;
}

.history-item-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.history-time {
  color: var(--fsd-text-tertiary, #6B7787);
  font-size: 12px;
}

.history-item-detail {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  font-size: 12px;
  color: var(--fsd-text-secondary, #9BA8B8);
}

.history-error {
  color: var(--fsd-error);
}

@media (max-width: 768px) {
  .toolbar {
    flex-direction: column;
  }
}
</style>