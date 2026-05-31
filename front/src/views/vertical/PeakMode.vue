<template>

  <PageContainer title="高峰预案" subtitle="手动/定时切换 · 家纺大促 vs 日常">

    <a-card>

      <a-form layout="vertical" style="max-width: 520px">

        <a-form-item label="园区">

          <a-select v-model:value="parkId" :options="parkOptions" @change="loadState" />

        </a-form-item>

        <a-form-item label="预案模板">

          <a-radio-group v-model:value="templateCode" :options="templateOptions" />

        </a-form-item>

        <a-form-item label="运行模式">

          <a-switch v-model:checked="peakEnabled" checked-children="高峰" un-checked-children="日常" />

        </a-form-item>

        <a-form-item label="高峰开始 cron">

          <a-input v-model:value="scheduleCron" placeholder="0 8 * * *" />

          <div class="form-hint">到达触发时间自动切换为高峰模式</div>

        </a-form-item>

        <a-form-item label="高峰结束 cron">

          <a-input v-model:value="scheduleEndCron" placeholder="0 22 * * *" />

          <div class="form-hint">到达触发时间自动切换为日常模式</div>

        </a-form-item>

        <a-button type="primary" :loading="saving" @click="save">保存</a-button>

      </a-form>

    </a-card>

  </PageContainer>

</template>



<script setup lang="ts">

import { onMounted, ref } from 'vue'

import { message } from 'ant-design-vue'

import PageContainer from '@/components/common/PageContainer.vue'

import { useParkOptions } from '@/composables/useParkOptions'

import * as verticalApi from '@/api/vertical'



const { parkOptions } = useParkOptions()

const parkId = ref<number>()

const peakEnabled = ref(false)

const templateCode = ref('DAILY')

const scheduleCron = ref('')

const scheduleEndCron = ref('')

const saving = ref(false)



const templateOptions = [

  { label: '日常', value: 'DAILY' },

  { label: '家纺大促', value: 'TEXTILE_PROMO' },

]



async function loadState() {

  if (!parkId.value) return

  const state = (await verticalApi.fetchPeakMode(parkId.value)).data

  peakEnabled.value = state.mode === 'PEAK'

  templateCode.value = state.templateCode || 'DAILY'

  scheduleCron.value = state.scheduleCron || ''

  scheduleEndCron.value = state.scheduleEndCron || ''

}



async function save() {

  if (!parkId.value) return

  saving.value = true

  try {

    await verticalApi.updatePeakMode({

      parkId: parkId.value,

      mode: peakEnabled.value ? 'PEAK' : 'NORMAL',

      templateCode: templateCode.value,

      scheduleCron: scheduleCron.value || undefined,

      scheduleEndCron: scheduleEndCron.value || undefined,

    })

    message.success('高峰模式已更新')

  } finally {

    saving.value = false

  }

}



onMounted(() => {

  parkId.value = parkOptions.value[0]?.value

  loadState()

})

</script>



<style scoped>

.form-hint {

  margin-top: 4px;

  color: rgba(0, 0, 0, 0.45);

  font-size: 12px;

}

</style>

