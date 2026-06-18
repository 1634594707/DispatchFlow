<template>
  <a-modal
    :open="visible"
    :footer="null"
    :closable="false"
    width="640px"
    class="command-palette-modal"
    @cancel="emit('close')"
  >
    <div class="palette-shell">
      <div class="palette-input-row">
        <SearchOutlined />
        <input
          ref="inputRef"
          :value="keyword"
          class="palette-input"
          placeholder="查车、查单、派车、打开页面… (Ctrl+K)"
          @input="onInput"
          @keydown.stop
        />
        <span class="palette-kbd-group">
          <span class="palette-kbd palette-kbd--accent">⌘K</span>
          <span class="palette-kbd">ESC</span>
        </span>
      </div>
      <a-spin :spinning="loading">
        <div class="palette-list">
          <button
            v-for="(item, index) in items"
            :key="item.key"
            type="button"
            class="palette-item"
            :class="{ active: index === activeIndex }"
            @click="emit('run', item)"
            @mouseenter="emit('update:activeIndex', index)"
          >
            <span class="palette-group">{{ item.group }}</span>
            <span class="palette-label">{{ item.label }}</span>
            <span v-if="item.hint" class="palette-hint">{{ item.hint }}</span>
          </button>
          <div v-if="items.length === 0" class="palette-empty">输入关键词开始搜索，或浏览快捷导航</div>
        </div>
      </a-spin>
      <div class="palette-footer">
        <span><kbd>↑</kbd><kbd>↓</kbd> 选择</span>
        <span><kbd>Enter</kbd> 确认</span>
        <span><kbd>Ctrl</kbd>+<kbd>K</kbd> 唤起</span>
      </div>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { SearchOutlined } from '@ant-design/icons-vue'
import type { CommandPaletteItem } from '@/composables/useCommandPalette'

const props = defineProps<{
  visible: boolean
  keyword: string
  loading: boolean
  activeIndex: number
  items: CommandPaletteItem[]
}>()

const emit = defineEmits<{
  close: []
  run: [item: CommandPaletteItem]
  'update:keyword': [value: string]
  'update:activeIndex': [value: number]
  search: []
}>()

const inputRef = ref<HTMLInputElement | null>(null)
let searchTimer: ReturnType<typeof setTimeout> | null = null

watch(
  () => props.visible,
  (open) => {
    if (open) {
      setTimeout(() => inputRef.value?.focus(), 50)
    }
  }
)

function onInput(event: Event) {
  const value = (event.target as HTMLInputElement).value
  emit('update:keyword', value)
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => emit('search'), 280)
}
</script>

<style scoped>
.palette-shell {
  margin: -8px 0 0;
}

.palette-input-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 4px 2px 12px;
  border-bottom: 1px solid var(--fsd-border, rgba(255, 255, 255, 0.07));
}

.palette-input {
  flex: 1;
  border: none;
  outline: none;
  font-size: 15px;
  background: transparent;
}

.palette-kbd-group {
  display: flex;
  gap: 6px;
  align-items: center;
}

.palette-kbd {
  font-size: 11px;
  color: var(--fsd-text-tertiary);
  border: 1px solid var(--fsd-border);
  border-radius: 4px;
  padding: 2px 6px;
  white-space: nowrap;
}

.palette-kbd--accent {
  background: rgba(34, 199, 230, 0.12);
  border-color: rgba(34, 199, 230, 0.3);
  color: var(--fsd-accent);
  font-weight: 600;
}

.palette-list {
  max-height: 360px;
  overflow: auto;
  padding: 8px 0;
}

.palette-item {
  width: 100%;
  display: grid;
  grid-template-columns: 64px 1fr auto;
  gap: 8px;
  align-items: center;
  border: none;
  background: transparent;
  text-align: left;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
}

.palette-item.active,
.palette-item:hover {
  background: rgba(34, 199, 230, 0.08);
}

.palette-group {
  font-size: 11px;
  color: var(--fsd-text-tertiary);
}

.palette-label {
  font-weight: 500;
}

.palette-hint {
  font-size: 12px;
  color: var(--fsd-text-secondary);
}

.palette-empty {
  padding: 24px;
  text-align: center;
  color: var(--fsd-text-secondary);
}

.palette-footer {
  display: flex;
  gap: 16px;
  padding-top: 10px;
  border-top: 1px solid var(--fsd-border, rgba(255, 255, 255, 0.07));
  font-size: 12px;
  color: var(--fsd-text-secondary);
}

.palette-footer kbd {
  border: 1px solid var(--fsd-border);
  border-radius: 4px;
  padding: 1px 5px;
  margin: 0 2px;
}
</style>
