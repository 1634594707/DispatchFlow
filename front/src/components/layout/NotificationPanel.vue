<template>
  <div class="notify-panel">
    <div class="notify-header">
      <span>待处理异常</span>
      <div class="notify-header-actions">
        <a class="notify-link" @click="$emit('settings')">告警设置</a>
        <a v-if="items.length > 0" class="notify-link" @click="$emit('viewAll')">查看全部</a>
      </div>
    </div>

    <!-- Alert History -->
    <div v-if="alertHistory.length > 0" class="alert-history-block">
      <div class="alert-history-title">最近告警</div>
      <div
        v-for="item in alertHistory.slice(0, 5)"
        :key="item.id"
        class="alert-history-item"
        :class="{ unread: !item.read }"
      >
        <span class="alert-history-sev">{{ item.severity }}</span>
        <span>{{ item.message }}</span>
      </div>
    </div>

    <!-- Notification List -->
    <a-spin :spinning="loading">
      <div v-if="items.length > 0" class="notify-list">
        <button
          v-for="item in items"
          :key="item.id"
          type="button"
          class="notify-item"
          @click="$emit('clickItem', item)"
        >
          <span class="notify-type">{{ item.exceptionType }}</span>
          <span class="notify-msg">{{ item.exceptionMsg || '调度异常' }}</span>
          <span class="notify-time">{{ formatTime(item.occurTime) }}</span>
        </button>
      </div>
      <a-empty v-else :image="Empty.PRESENTED_IMAGE_SIMPLE" description="暂无待处理通知" />
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { Empty } from 'ant-design-vue'
import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import 'dayjs/locale/zh-cn'
import type { ExceptionAdminListItem } from '@/types/exception'

dayjs.extend(relativeTime)
dayjs.locale('zh-cn')

defineProps<{
  items: ExceptionAdminListItem[]
  loading: boolean
  alertHistory: Array<{ id: number | string; read: boolean; severity: string; message: string }>
}>()

defineEmits<{
  viewAll: []
  settings: []
  clickItem: [item: ExceptionAdminListItem]
}>()

function formatTime(value: string) {
  return dayjs(value).fromNow()
}
</script>

<style scoped lang="less">
.notify-panel {
  width: 320px;
  max-width: calc(100vw - 48px);
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

  &:hover {
    color: var(--fsd-accent);
  }
}

.notify-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 360px;
  overflow: auto;
}

.notify-item {
  width: 100%;
  padding: 12px 14px;
  border: 1px solid var(--fsd-border);
  border-radius: var(--fsd-radius-sm);
  background: rgba(18, 24, 33, 0.5);
  text-align: left;
  cursor: pointer;
  transition: border-color 0.2s var(--fsd-ease), background 0.2s var(--fsd-ease);
  min-height: 44px;

  &:hover {
    border-color: rgba(34, 199, 230, 0.35);
    background: rgba(34, 199, 230, 0.08);
  }
}

.notify-type {
  display: block;
  font-size: 11px;
  font-weight: 600;
  color: var(--fsd-warning);
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.notify-msg {
  display: block;
  margin-top: 4px;
  font-size: 13px;
  color: var(--fsd-text-primary);
  line-height: 1.4;
}

.notify-time {
  display: block;
  margin-top: 6px;
  font-size: 11px;
  color: var(--fsd-text-tertiary);
}

.alert-history-block {
  margin-bottom: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--fsd-border);
}

.alert-history-title {
  font-size: 12px;
  color: var(--fsd-text-tertiary);
  margin-bottom: 8px;
}

.alert-history-item {
  font-size: 12px;
  color: var(--fsd-text-secondary);
  padding: 6px 0;
  border-bottom: 1px dashed rgba(255, 255, 255, 0.06);

  &.unread {
    color: var(--fsd-text-primary);
  }
}

.alert-history-sev {
  display: inline-block;
  margin-right: 6px;
  font-size: 10px;
  font-weight: 700;
  color: var(--fsd-warning);
}
</style>
