<template>
  <div class="notify-panel">
    <div class="notify-header">
      <span>待处理异常</span>
      <div class="notify-header-actions">
        <a class="notify-link" @click="$emit('settings')">告警设置</a>
        <a v-if="items.length > 0" class="notify-link" @click="$emit('viewAll')">查看全部</a>
      </div>
    </div>

    <div v-if="visibleHistory.length > 0" class="alert-history-block">
      <div class="alert-history-title">最近告警</div>
      <div
        v-for="item in visibleHistory"
        :key="item.id"
        class="alert-history-item"
        :class="{ unread: !item.read }"
      >
        <span class="alert-history-sev">{{ item.severity }}</span>
        <span class="alert-history-message">{{ safeAlertMessage(item.message, item.eventType) }}</span>
      </div>
    </div>

    <a-spin :spinning="loading">
      <div v-if="visibleItems.length > 0" class="notify-list">
        <button
          v-for="item in visibleItems"
          :key="item.id"
          type="button"
          class="notify-item"
          @click="$emit('clickItem', item)"
        >
          <div class="notify-item-head">
            <span class="notify-type">{{ exceptionLabel(item.exceptionType) }}</span>
            <span v-if="item.taskNo" class="notify-ref">{{ item.taskNo }}</span>
          </div>
          <span class="notify-msg">{{ safeAlertMessage(item.exceptionMsg, item.exceptionType) }}</span>
          <span class="notify-time">{{ formatTime(item.occurTime) }}</span>
        </button>
      </div>
      <a-empty v-else :image="Empty.PRESENTED_IMAGE_SIMPLE" description="暂无待处理异常" />
    </a-spin>

    <div v-if="hiddenCount > 0" class="notify-overflow">
      已收起 {{ hiddenCount }} 条重复或较早异常，可进入异常任务查看全部记录
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Empty } from 'ant-design-vue'
import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import 'dayjs/locale/zh-cn'
import type { ExceptionAdminListItem } from '@/types/exception'
import { alertDedupKey, exceptionDedupKey, exceptionLabel, safeAlertMessage } from '@/utils/notificationDisplay'

dayjs.extend(relativeTime)
dayjs.locale('zh-cn')

const props = defineProps<{
  items: ExceptionAdminListItem[]
  loading: boolean
  alertHistory: Array<{
    id: number | string
    read: boolean
    severity: string
    message: string
    eventType?: string
    createdAt?: string
  }>
}>()

defineEmits<{
  viewAll: []
  settings: []
  clickItem: [item: ExceptionAdminListItem]
}>()

const visibleHistory = computed(() => {
  const cutoff = dayjs().subtract(24, 'hour')
  const seen = new Set<string>()
  return props.alertHistory
    .filter((item) => !item.createdAt || !dayjs(item.createdAt).isBefore(cutoff))
    .sort((a, b) => Number(b.id) - Number(a.id))
    .filter((item) => {
      const key = alertDedupKey(item.message, item.eventType)
      if (seen.has(key)) return false
      seen.add(key)
      return true
    })
    .slice(0, 3)
})

const visibleItems = computed(() => {
  const seen = new Set<string>()
  return [...props.items]
    .filter((item) => !item.exceptionStatus || item.exceptionStatus === 'OPEN')
    .sort((a, b) => Date.parse(b.occurTime || b.createdAt) - Date.parse(a.occurTime || a.createdAt))
    .filter((item) => {
      const key = exceptionDedupKey(item)
      if (seen.has(key)) return false
      seen.add(key)
      return true
    })
    .slice(0, 5)
})

const hiddenCount = computed(() => Math.max(0, props.items.length - visibleItems.value.length))

function formatTime(value: string) {
  const parsed = dayjs(value)
  return parsed.isValid() ? parsed.fromNow() : '时间未知'
}
</script>

<style scoped lang="less">
.notify-panel {
  box-sizing: border-box;
  width: 360px;
  max-width: calc(100vw - 32px);
  max-height: min(560px, calc(100vh - 96px));
  overflow-y: auto;
  padding-right: 2px;
}

.notify-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
  font-size: 14px;
  color: var(--fsd-text-primary);
  margin-bottom: 12px;
}

.notify-header-actions {
  display: flex;
  gap: 12px;
}

.notify-link {
  font-size: 12px;
  font-weight: 500;
  color: var(--fsd-accent);
  text-decoration: none;
  cursor: pointer;
}

.alert-history-block {
  display: grid;
  gap: 6px;
  margin-bottom: 10px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--fsd-border);
}

.alert-history-title {
  color: var(--fsd-text-tertiary);
  font-size: 11px;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.alert-history-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 7px 9px;
  border-radius: var(--fsd-radius-sm);
  background: rgba(255, 176, 32, 0.07);
  color: var(--fsd-text-secondary);
  font-size: 12px;
  line-height: 1.45;
}

.alert-history-item.unread {
  border-left: 2px solid var(--fsd-warning);
}

.alert-history-sev {
  flex: 0 0 auto;
  color: var(--fsd-warning);
  font-size: 10px;
  font-weight: 700;
}

.alert-history-message,
.notify-msg {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
}

.notify-list {
  display: grid;
  gap: 7px;
  max-height: 300px;
  overflow-y: auto;
}

.notify-item {
  box-sizing: border-box;
  width: 100%;
  min-height: 0;
  display: grid;
  gap: 5px;
  padding: 10px 11px;
  border: 1px solid var(--fsd-border);
  border-radius: var(--fsd-radius-sm);
  background: rgba(18, 24, 33, 0.5);
  text-align: left;
  cursor: pointer;
  transition: border-color 0.2s var(--fsd-ease), background 0.2s var(--fsd-ease);
  appearance: none;
  font: inherit;
  overflow: hidden;
  flex-shrink: 0;
}

.notify-item:hover {
  border-color: rgba(34, 199, 230, 0.35);
  background: rgba(34, 199, 230, 0.06);
}

.notify-item-head {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.notify-type {
  flex: 0 0 auto;
  color: var(--fsd-warning);
  font-size: 11px;
  font-weight: 700;
}

.notify-ref {
  min-width: 0;
  overflow: hidden;
  color: var(--fsd-text-tertiary);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notify-msg {
  color: var(--fsd-text-secondary);
  font-size: 12px;
  line-height: 1.45;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.notify-time {
  color: var(--fsd-text-tertiary);
  font-size: 11px;
}

@media (max-width: 480px) {
  .notify-panel {
    width: min(360px, calc(100vw - 28px));
    max-height: calc(100vh - 92px);
  }

  .notify-list {
    max-height: 270px;
  }

  .notify-ref {
    display: none;
  }
}

.notify-overflow {
  margin-top: 10px;
  color: var(--fsd-text-tertiary);
  font-size: 11px;
  line-height: 1.45;
}
</style>
