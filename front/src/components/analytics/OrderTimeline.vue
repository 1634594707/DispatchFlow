<template>
  <a-timeline>
    <a-timeline-item
      v-for="event in events"
      :key="event.stage + (event.time || '')"
      :color="timelineColor(event.status)"
    >
      <template #dot v-if="event.status === 'error'">
        <WarningFilled style="color: #FF5C7C" />
      </template>
      <template #dot v-else-if="event.status === 'active'">
        <SyncOutlined spin style="color: #22C7E6" />
      </template>
      <div class="timeline-event">
        <div class="event-header">
          <span class="event-label">{{ event.label }}</span>
          <span v-if="event.time" class="event-time">{{ formatTime(event.time) }}</span>
        </div>
        <div v-if="event.remark" class="event-remark">{{ event.remark }}</div>
        <div v-if="durationText(event)" class="event-duration">{{ durationText(event) }}</div>
      </div>
    </a-timeline-item>
    <a-timeline-item v-if="events.length === 0" color="gray">
      <span class="text-muted">暂无时间线数据</span>
    </a-timeline-item>
  </a-timeline>
</template>

<script setup lang="ts">
import { WarningFilled, SyncOutlined } from '@ant-design/icons-vue'
import type { TimelineEvent } from '@/api/analytics'
import dayjs from 'dayjs'

const props = defineProps<{
  events: TimelineEvent[]
}>()

function timelineColor(status: string): string {
  switch (status) {
    case 'completed': return 'green'
    case 'active': return 'blue'
    case 'error': return 'red'
    default: return 'gray'
  }
}

function formatTime(t: string) {
  return dayjs(t).format('MM-DD HH:mm')
}

function durationText(event: TimelineEvent): string {
  return ''
}
</script>

<style scoped lang="less">
.timeline-event {
  .event-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 12px;
  }
  .event-label {
    font-weight: 600;
    font-size: 14px;
    color: var(--fsd-text-primary);
  }
  .event-time {
    font-family: 'JetBrains Mono', monospace;
    font-size: 12px;
    color: var(--fsd-text-tertiary);
    white-space: nowrap;
  }
  .event-remark {
    margin-top: 4px;
    font-size: 13px;
    color: var(--fsd-text-secondary);
  }
  .event-duration {
    margin-top: 2px;
    font-size: 12px;
    color: var(--fsd-text-tertiary);
  }
}
.text-muted {
  color: var(--fsd-text-tertiary);
}
</style>