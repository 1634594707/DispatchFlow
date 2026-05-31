<template>
  <a-modal v-model:open="open" title="新园区配置向导" width="640px" :footer="null" destroy-on-close>
    <a-steps :current="step" size="small" style="margin-bottom: 20px;">
      <a-step title="园区" />
      <a-step title="站点" />
      <a-step title="路网" />
      <a-step title="充电" />
      <a-step title="试派单" />
    </a-steps>
    <div v-if="step === 0">
      <p>在「园区管理」创建园区并设为启用，记录园区 ID：<strong>{{ parkId || '—' }}</strong></p>
      <a-button type="link" @click="go('/infrastructure/parks')">打开园区管理</a-button>
      <a-input-number v-model:value="parkId" placeholder="输入园区 ID" style="width: 100%; margin-top: 8px;" />
    </div>
    <div v-else-if="step === 1">
      <p>配置取货/卸货站点坐标（可在站点表单中使用地图点选）。</p>
      <a-button type="link" @click="go('/infrastructure/stations')">打开站点管理</a-button>
    </div>
    <div v-else-if="step === 2">
      <p>配置路网节点与路段；禁用路段前系统会提示影响任务数与可达性。</p>
      <a-button type="link" @click="go('/infrastructure/road-network')">打开路网管理</a-button>
    </div>
    <div v-else-if="step === 3">
      <p>配置充电桩与停车位。</p>
      <a-space>
        <a-button type="link" @click="go('/infrastructure/charging-piles')">充电桩</a-button>
        <a-button type="link" @click="go('/infrastructure/parking-slots')">停车位</a-button>
      </a-space>
    </div>
    <div v-else>
      <p>返回工作台创建测试订单，验证自动派车与路网可达。</p>
      <a-button type="primary" @click="go('/workbench')">前往工作台试派单</a-button>
    </div>
    <div class="wizard-footer">
      <a-button v-if="step > 0" @click="step--">上一步</a-button>
      <a-button v-if="step < 4" type="primary" @click="step++">下一步</a-button>
      <a-button v-else @click="open = false">完成</a-button>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'

const open = defineModel<boolean>('open', { default: false })
const step = ref(0)
const parkId = ref<number>()
const router = useRouter()

function go(path: string) {
  open.value = false
  void router.push(path)
}
</script>

<style scoped>
.wizard-footer {
  margin-top: 20px;
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}
</style>
