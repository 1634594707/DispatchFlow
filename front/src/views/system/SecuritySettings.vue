<template>
  <PageContainer title="安全设置" subtitle="ADMIN 可选 TOTP 双因素认证">
    <a-alert type="info" show-icon message="启用后登录需输入 Authenticator 验证码" style="margin-bottom: 16px" />
    <a-space direction="vertical">
      <a-button :loading="loading" @click="startEnroll">生成密钥</a-button>
      <div v-if="secret">Secret: <code>{{ secret }}</code></div>
      <div v-if="otpauthUrl"><a :href="otpauthUrl" target="_blank">otpauth 链接</a></div>
      <a-input v-model:value="code" placeholder="6 位验证码" style="max-width: 200px" />
      <a-space>
        <a-button type="primary" @click="enable">启用 2FA</a-button>
        <a-button danger @click="disable">关闭 2FA</a-button>
      </a-space>
    </a-space>
  </PageContainer>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { message } from 'ant-design-vue'
import PageContainer from '@/components/common/PageContainer.vue'
import { disableTotp, enableTotp, enrollTotp } from '@/api/auth'

const loading = ref(false)
const secret = ref('')
const otpauthUrl = ref('')
const code = ref('')

async function startEnroll() {
  loading.value = true
  try {
    const res = await enrollTotp()
    secret.value = res.data.secret
    otpauthUrl.value = res.data.otpauthUrl
  } finally {
    loading.value = false
  }
}

async function enable() {
  await enableTotp(code.value)
  message.success('2FA 已启用')
}

async function disable() {
  await disableTotp(code.value)
  message.success('2FA 已关闭')
}
</script>
