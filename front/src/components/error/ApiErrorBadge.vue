<template>
  <a-tooltip title="API 错误记录">
    <a-button type="text" class="header-icon-btn" @click="drawerOpen = true">
      <a-badge :count="store.records.length" :show-zero="false" :overflow-count="99" :offset="[-2, 4]">
        <ExclamationCircleOutlined />
      </a-badge>
    </a-button>
  </a-tooltip>

  <a-drawer
    v-model:open="drawerOpen"
    title="API 错误详情"
    placement="right"
    width="520"
  >
    <div v-if="store.records.length === 0" class="empty-state">
      <a-empty description="暂无 API 错误记录" :image="simpleImage" />
    </div>

    <div v-else class="error-list">
      <div class="error-list-header">
        <span class="error-count">共 {{ store.records.length }} 条</span>
        <a-button size="small" @click="store.clear()">清空</a-button>
      </div>

      <div
        v-for="record in store.records"
        :key="record.id"
        class="error-card"
      >
        <div class="error-card-header" @click="toggleExpand(record.id)">
          <div class="error-card-summary">
            <a-tag :color="statusColor(record.status)" class="error-status-tag">
              {{ record.status }}
            </a-tag>
            <span class="error-method">{{ record.method }}</span>
            <span class="error-url">{{ record.url }}</span>
          </div>
          <div class="error-card-meta">
            <span class="error-time">{{ formatTime(record.timestamp) }}</span>
            <CaretRightOutlined
              class="expand-icon"
              :class="{ expanded: expandedIds.has(record.id) }"
            />
          </div>
        </div>

        <div class="error-message">{{ record.message }}</div>

        <div v-if="expandedIds.has(record.id)" class="error-raw">
          <div class="raw-label">原始错误信息：</div>
          <pre class="raw-json">{{ formatRaw(record) }}</pre>
        </div>
      </div>
    </div>
  </a-drawer>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Empty } from 'ant-design-vue'
import { ExclamationCircleOutlined, CaretRightOutlined } from '@ant-design/icons-vue'
import { useApiErrorsStore } from '@/stores/apiErrors'

const store = useApiErrorsStore()
const simpleImage = Empty.PRESENTED_IMAGE_SIMPLE
const drawerOpen = ref(false)
const expandedIds = ref(new Set<number>())

function toggleExpand(id: number) {
  if (expandedIds.value.has(id)) {
    expandedIds.value.delete(id)
  } else {
    expandedIds.value.add(id)
  }
}

function statusColor(status: number): string {
  if (status >= 500) return 'red'
  if (status >= 400) return 'orange'
  if (status >= 300) return 'blue'
  return 'default'
}

function formatTime(iso: string): string {
  const d = new Date(iso)
  return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

function formatRaw(record: { code: string; rawMessage: string; status: number; url: string; method: string }): string {
  return JSON.stringify(
    {
      status: record.status,
      code: record.code,
      rawMessage: record.rawMessage,
      url: record.url,
      method: record.method,
    },
    null,
    2
  )
}
</script>

<style scoped lang="less">
.empty-state {
  margin-top: 60px;
}

.error-list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.error-count {
  font-size: 12px;
  color: var(--fsd-text-tertiary);
}

.error-card {
  border: 1px solid var(--fsd-border);
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 8px;
  background: rgba(22, 27, 34, 0.5);
}

.error-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  cursor: pointer;
  user-select: none;
}

.error-card-summary {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex: 1;
  overflow: hidden;
}

.error-status-tag {
  flex-shrink: 0;
}

.error-method {
  font-size: 11px;
  font-weight: 600;
  color: var(--fsd-text-secondary);
  flex-shrink: 0;
}

.error-url {
  font-size: 12px;
  color: var(--fsd-text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.error-card-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  margin-left: 8px;
}

.error-time {
  font-size: 11px;
  color: var(--fsd-text-tertiary);
  white-space: nowrap;
}

.expand-icon {
  font-size: 12px;
  color: var(--fsd-text-tertiary);
  transition: transform 0.2s;

  &.expanded {
    transform: rotate(90deg);
  }
}

.error-message {
  margin-top: 8px;
  font-size: 13px;
  color: var(--fsd-text-primary);
  line-height: 1.4;
  word-break: break-all;
}

.error-raw {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed rgba(255, 255, 255, 0.08);
}

.raw-label {
  font-size: 11px;
  color: var(--fsd-text-tertiary);
  margin-bottom: 6px;
}

.raw-json {
  font-size: 11px;
  line-height: 1.6;
  background: rgba(0, 0, 0, 0.3);
  border-radius: 6px;
  padding: 10px;
  overflow-x: auto;
  white-space: pre-wrap;
  word-break: break-all;
  color: var(--fsd-text-secondary);
  margin: 0;
}
</style>