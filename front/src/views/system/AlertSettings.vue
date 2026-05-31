<template>
  <PageContainer title="告警设置" subtitle="声音告警 · 浏览器通知 · 静默时段">
    <a-form layout="vertical" class="settings-form">
      <a-form-item label="声音告警">
        <a-switch v-model:checked="form.soundEnabled" />
      </a-form-item>
      <a-form-item label="音量">
        <a-slider v-model:value="form.soundVolume" :min="0" :max="1" :step="0.1" :disabled="!form.soundEnabled" />
      </a-form-item>
      <a-form-item label="严重异常特殊告警音">
        <a-switch v-model:checked="form.criticalSoundEnabled" :disabled="!form.soundEnabled" />
      </a-form-item>
      <a-form-item label="浏览器通知">
        <a-switch v-model:checked="form.browserNotifyEnabled" />
        <a-button size="small" style="margin-left: 12px" @click="requestPermission">申请通知权限</a-button>
      </a-form-item>
      <a-form-item label="静默时段（小时，留空表示不启用）">
        <a-space>
          <a-input-number v-model:value="form.silentStartHour" :min="0" :max="23" placeholder="开始" />
          <span>至</span>
          <a-input-number v-model:value="form.silentEndHour" :min="0" :max="23" placeholder="结束" />
        </a-space>
      </a-form-item>
      <a-form-item label="按级别配置">
        <div class="level-rules">
          <div v-for="level in levels" :key="level" class="level-row">
            <span>{{ level }}</span>
            <a-checkbox v-model:checked="form.levelRules[level].sound">声音</a-checkbox>
            <a-checkbox v-model:checked="form.levelRules[level].notify">通知</a-checkbox>
          </div>
        </div>
      </a-form-item>
      <a-space>
        <a-button type="primary" @click="save">保存</a-button>
        <a-button @click="reset">恢复默认</a-button>
        <a-button @click="testSound">测试提示音</a-button>
      </a-space>
    </a-form>
  </PageContainer>
</template>

<script setup lang="ts">
import { onMounted, reactive } from 'vue'
import { message } from 'ant-design-vue'
import PageContainer from '@/components/common/PageContainer.vue'
import { useAlertStore } from '@/stores/alert'
import { playAlertTone, requestBrowserNotifyPermission } from '@/utils/alertSound'
import * as alertSettingsApi from '@/api/alertSettings'

const alertStore = useAlertStore()
const levels = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW']
const form = reactive(JSON.parse(JSON.stringify(alertStore.rules)))

async function requestPermission() {
  const ok = await requestBrowserNotifyPermission()
  message[ok ? 'success' : 'warning'](ok ? '通知权限已开启' : '通知权限未授予')
}

async function save() {
  const json = JSON.stringify(form)
  try {
    await alertSettingsApi.saveAlertSettings(json)
  } catch {
    message.warning('服务端保存失败，已写入本地')
  }
  alertStore.updateRules(JSON.parse(JSON.stringify(form)))
  message.success('告警设置已保存')
}

onMounted(async () => {
  try {
    const res = await alertSettingsApi.fetchAlertSettings()
    const parsed = JSON.parse(res.data.rulesJson)
    Object.assign(form, parsed)
    alertStore.updateRules(parsed)
  } catch {
    Object.assign(form, JSON.parse(JSON.stringify(alertStore.rules)))
  }
})

function reset() {
  alertStore.resetRules()
  Object.assign(form, JSON.parse(JSON.stringify(alertStore.rules)))
  message.success('已恢复默认设置')
}

function testSound() {
  playAlertTone(true)
}
</script>

<style scoped lang="less">
.settings-form {
  max-width: 560px;
}

.level-rules {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.level-row {
  display: grid;
  grid-template-columns: 80px 1fr 1fr;
  align-items: center;
  padding: 8px 12px;
  border: 1px solid var(--fsd-border);
  border-radius: 8px;
}
</style>
