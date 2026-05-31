<template>

  <PageContainer title="现场运维工单" subtitle="FIELD_OPS 待处理任务">

    <a-table :data-source="tickets" row-key="id" size="small" :loading="loading">

      <a-table-column title="异常类型" data-index="exceptionType" />

      <a-table-column title="描述" data-index="exceptionMsg" ellipsis />

      <a-table-column title="状态" key="status">

        <template #default="{ record }">

          <a-tag :color="statusColor[record.status] || 'default'">

            {{ statusLabel(record.status) }}

          </a-tag>

        </template>

      </a-table-column>

      <a-table-column title="创建时间" data-index="createdAt" width="180" />

      <a-table-column title="操作" key="actions">

        <template #default="{ record }">

          <a-space v-if="record.status !== 'DONE'">

            <a-button

              v-if="record.status === 'OPEN'"

              type="link"

              size="small"

              @click="updateStatus(record.id, 'IN_PROGRESS')"

            >

              开始处理

            </a-button>

            <a-button type="link" size="small" @click="updateStatus(record.id, 'DONE')">完成</a-button>

          </a-space>

        </template>

      </a-table-column>

    </a-table>

  </PageContainer>

</template>



<script setup lang="ts">

import { onMounted, ref } from 'vue'

import { message } from 'ant-design-vue'

import PageContainer from '@/components/common/PageContainer.vue'

import { fetchFieldOpsTickets, updateFieldOpsTicketStatus, type FieldOpsTicket } from '@/api/fieldOps'

import { useAuthStore } from '@/stores/auth'



const authStore = useAuthStore()

const loading = ref(false)

const tickets = ref<FieldOpsTicket[]>([])



const statusColor: Record<string, string> = {

  OPEN: 'blue',

  IN_PROGRESS: 'processing',

  DONE: 'success',

}



function statusLabel(status: string) {

  if (status === 'IN_PROGRESS') return '处理中'

  if (status === 'DONE') return '已完成'

  return '待处理'

}



async function load() {

  loading.value = true

  try {

    const params: { assigneeUserId?: number; status?: string } = {}

    if (authStore.isFieldOps) {

      params.assigneeUserId = authStore.user?.id

    }

    tickets.value = (await fetchFieldOpsTickets(params)).data

  } finally {

    loading.value = false

  }

}



async function updateStatus(ticketId: number, status: string) {

  await updateFieldOpsTicketStatus(ticketId, status)

  message.success(status === 'DONE' ? '已标记完成' : '已开始处理')

  await load()

}



onMounted(load)

</script>

