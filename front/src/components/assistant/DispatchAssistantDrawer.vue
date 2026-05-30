<template>
  <a-drawer
    v-model:open="open"
    title="调度快捷指令"
    placement="right"
    width="420"
    :mask="false"
  >
    <p class="assistant-tip">规则型快捷指令（非大模型），解析自然语言并给出派车建议与快捷操作。</p>
    <div class="assistant-input-row">
      <a-textarea
        v-model:value="instruction"
        :rows="3"
        placeholder='例如："刷新工作台"、"批量自动派车"、"查看低电量车辆"'
        @pressEnter.prevent="submit"
      />
      <div class="assistant-actions">
        <a-button type="primary" :loading="loading" @click="submit">解析指令</a-button>
        <a-button :disabled="!speechSupported" @click="toggleVoice">
          {{ listening ? '停止语音' : '语音输入' }}
        </a-button>
      </div>
    </div>
    <div v-if="response" class="assistant-result">
      <a-tag color="blue">{{ response.intent }}</a-tag>
      <p class="assistant-reply">{{ response.reply }}</p>
      <ul v-if="response.suggestions?.length" class="assistant-suggestions">
        <li v-for="(s, i) in response.suggestions" :key="i">{{ s }}</li>
      </ul>
      <div v-if="response.actions?.length" class="assistant-action-list">
        <a-button
          v-for="(action, i) in response.actions"
          :key="i"
          size="small"
          @click="runAction(action)"
        >
          {{ action.label }}
        </a-button>
      </div>
    </div>
  </a-drawer>
</template>

<script setup lang="ts">
import { computed, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { interpretAssistant } from '@/api/assistant'
import { batchAutoAssign } from '@/api/task'
import { useParkScopeStore } from '@/stores/parkScope'
import { useWorkbenchStore } from '@/stores/workbench'
import type { AssistantAction, AssistantResponse } from '@/types/phase10'

const open = defineModel<boolean>('open', { default: false })
const router = useRouter()
const parkScope = useParkScopeStore()
const workbenchStore = useWorkbenchStore()

const instruction = ref('')
const loading = ref(false)
const response = ref<AssistantResponse | null>(null)
const listening = ref(false)

const speechSupported = computed(() => {
  if (typeof window === 'undefined') return false
  return 'webkitSpeechRecognition' in window || 'SpeechRecognition' in window
})

// eslint-disable-next-line @typescript-eslint/no-explicit-any
let recognition: any = null

async function submit() {
  const text = instruction.value.trim()
  if (!text) return
  loading.value = true
  try {
    const res = await interpretAssistant(text, parkScope.selectedParkId)
    response.value = res.data
  } finally {
    loading.value = false
  }
}

function toggleVoice() {
  if (listening.value) {
    recognition?.stop()
    listening.value = false
    return
  }
  const win = window as Window & { SpeechRecognition?: new () => any; webkitSpeechRecognition?: new () => any }
  const SpeechRecognitionCtor = win.SpeechRecognition || win.webkitSpeechRecognition
  if (!SpeechRecognitionCtor) {
    message.warning('当前浏览器不支持语音输入')
    return
  }
  recognition = new SpeechRecognitionCtor()
  recognition.lang = 'zh-CN'
  recognition.interimResults = false
  recognition.onresult = (event: { results: ArrayLike<{ 0: { transcript: string } }> }) => {
    instruction.value = event.results[0][0].transcript
    listening.value = false
  }
  recognition.onerror = () => {
    listening.value = false
    message.error('语音识别失败')
  }
  recognition.onend = () => {
    listening.value = false
  }
  listening.value = true
  recognition.start()
}

async function runAction(action: AssistantAction) {
  const path = action.payload?.path as string | undefined
  if (action.actionType === 'NAVIGATE' && path) {
    open.value = false
    await router.push(path)
    return
  }
  if (action.actionType === 'REFRESH_WORKBENCH') {
    await workbenchStore.fetchQueue()
    message.success('工作台已刷新')
    return
  }
  if (action.actionType === 'BATCH_AUTO_ASSIGN') {
    const taskIds = workbenchStore.taskPool.map((t) => t.taskId)
    if (taskIds.length === 0) {
      message.info('当前无待处理任务')
      return
    }
    await batchAutoAssign(taskIds)
    await workbenchStore.fetchQueue()
    message.success('已触发批量自动派车')
    return
  }
  if (action.actionType === 'AUTO_ASSIGN_TASK' && action.payload?.taskId) {
    await workbenchStore.dispatchAuto(Number(action.payload.taskId))
    message.success('已触发自动派车')
    return
  }
}

onUnmounted(() => recognition?.stop())
</script>

<style scoped>
.assistant-tip {
  color: #6b7c8f;
  font-size: 13px;
  margin-bottom: 12px;
}

.assistant-input-row {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.assistant-actions {
  display: flex;
  gap: 8px;
}

.assistant-result {
  margin-top: 16px;
}

.assistant-reply {
  margin: 10px 0;
  line-height: 1.6;
}

.assistant-suggestions {
  padding-left: 18px;
  color: #4a5b6c;
}

.assistant-action-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}
</style>
