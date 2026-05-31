<template>
  <PageContainer title="定时报表" subtitle="配置 PDF 运营日报邮件 cron 与收件人">
    <div class="toolbar">
      <a-button type="primary" @click="openCreate">新建计划</a-button>
      <a-button @click="load">刷新</a-button>
    </div>
    <a-table :data-source="rows" row-key="id" size="small" :loading="loading" :pagination="false">
      <a-table-column title="Cron" data-index="cronExpression" />
      <a-table-column title="收件人" data-index="recipients" />
      <a-table-column title="园区" key="parkId">
        <template #default="{ record }">{{ record.parkId ?? '全园区' }}</template>
      </a-table-column>
      <a-table-column title="启用" key="enabled">
        <template #default="{ record }">{{ record.enabled ? '是' : '否' }}</template>
      </a-table-column>
      <a-table-column title="操作" key="actions">
        <template #default="{ record }">
          <a-space>
            <a-button type="link" size="small" @click="edit(record)">编辑</a-button>
            <a-button type="link" size="small" danger @click="remove(record)">删除</a-button>
          </a-space>
        </template>
      </a-table-column>
    </a-table>

    <a-modal v-model:open="modalOpen" title="报表计划" @ok="save">
      <a-form layout="vertical">
        <a-form-item label="Cron 表达式"><a-input v-model:value="form.cronExpression" placeholder="0 0 8 * * MON-FRI" /></a-form-item>
        <a-form-item label="收件人（逗号分隔）"><a-input v-model:value="form.recipients" /></a-form-item>
        <a-form-item label="园区 ID（留空=全园区）"><a-input-number v-model:value="form.parkId" style="width:100%" /></a-form-item>
        <a-form-item label="启用"><a-switch v-model:checked="form.enabled" /></a-form-item>
      </a-form>
    </a-modal>
  </PageContainer>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import PageContainer from '@/components/common/PageContainer.vue'
import { deleteReportSchedule, fetchReportSchedules, upsertReportSchedule } from '@/api/reportSchedule'
import type { AdminReportSchedule } from '@/types/reportSchedule'

const loading = ref(false)
const rows = ref<AdminReportSchedule[]>([])
const modalOpen = ref(false)
const form = reactive<AdminReportSchedule>({ cronExpression: '0 0 8 * * MON-FRI', recipients: '', enabled: false })

async function load() {
  loading.value = true
  try {
    rows.value = (await fetchReportSchedules()).data
  } finally {
    loading.value = false
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

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; gap: 8px; margin-bottom: 12px; }
</style>
