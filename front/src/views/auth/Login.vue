<template>
  <div class="login-page">
    <div class="login-bg">
      <div class="grid-overlay" />
      <div class="glow glow-1" />
      <div class="glow glow-2" />
    </div>

    <div class="login-card animate-fade-in-up">
      <div class="login-brand">
        <div class="brand-icon">
          <DispatchFlowLogo :size="52" />
        </div>
        <h1>DispatchFlow</h1>
        <p>找家纺网 · 叠石桥 L1 无人车短驳调度</p>
      </div>

      <a-form
        layout="vertical"
        :model="form"
        @finish="handleLogin"
      >
        <a-form-item
          label="用户名"
          name="username"
          :rules="[{ required: true, message: '请输入用户名' }]"
        >
          <a-input
            v-model:value="form.username"
            size="large"
            placeholder="请输入用户名"
            autocomplete="username"
          >
            <template #prefix><UserOutlined /></template>
          </a-input>
        </a-form-item>

        <a-form-item
          label="密码"
          name="password"
          :rules="[{ required: true, message: '请输入密码' }]"
        >
          <a-input-password
            v-model:value="form.password"
            size="large"
            placeholder="请输入密码"
            autocomplete="current-password"
          >
            <template #prefix><LockOutlined /></template>
          </a-input-password>
        </a-form-item>

        <a-form-item
          v-if="needsTotp"
          label="验证码"
          name="totpCode"
          :rules="[{ required: true, message: '请输入 6 位验证码' }]"
        >
          <a-input v-model:value="form.totpCode" size="large" maxlength="6" placeholder="Authenticator 验证码" />
        </a-form-item>

        <a-button
          type="primary"
          html-type="submit"
          size="large"
          block
          :loading="authStore.loading"
          class="login-btn"
        >
          登录
        </a-button>
      </a-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import { UserOutlined, LockOutlined } from '@ant-design/icons-vue'
import DispatchFlowLogo from '@/components/brand/DispatchFlowLogo.vue'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const form = reactive({
  username: '',
  password: '',
  totpCode: '',
})

const needsTotp = ref(false)

/**
 * 校验 redirect 参数是否安全：仅允许相对路径（以 / 开头且不以 // 开头）或同源 URL，
 * 拒绝外部域名 URL，防止开放重定向漏洞。
 */
function isSafeRedirect(redirect: string): boolean {
  if (!redirect) return false
  if (redirect.startsWith('/') && !redirect.startsWith('//')) return true
  try {
    const url = new URL(redirect, window.location.origin)
    return url.origin === window.location.origin
  } catch {
    return false
  }
}

async function handleLogin() {
  try {
    const result = await authStore.login(form.username, form.password, needsTotp.value ? form.totpCode : undefined)
    if (result.data.requiresTotp && !result.data.token) {
      needsTotp.value = true
      message.info('请输入 Authenticator 验证码')
      return
    }
    message.success('登录成功')
    const redirect = (route.query.redirect as string) || '/workbench'
    router.replace(isSafeRedirect(redirect) ? redirect : '/workbench')
  } catch {
    message.error('登录失败，请检查账号密码或验证码')
    if (needsTotp.value) {
      form.totpCode = ''
    }
  }
}
</script>

<style scoped lang="less">
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--fsd-bg-deep);
  position: relative;
  overflow: hidden;
}

.login-bg {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.grid-overlay {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(34, 199, 230, 0.035) 1px, transparent 1px),
    linear-gradient(90deg, rgba(34, 199, 230, 0.035) 1px, transparent 1px);
  background-size: 48px 48px;
  mask-image: radial-gradient(ellipse 80% 70% at 50% 40%, #000 30%, transparent 100%);
  -webkit-mask-image: radial-gradient(ellipse 80% 70% at 50% 40%, #000 30%, transparent 100%);
}

.glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(90px);
  opacity: 0.4;
}

.glow-1 {
  width: 420px;
  height: 420px;
  background: var(--fsd-accent);
  top: -120px;
  right: 8%;
}

.glow-2 {
  width: 320px;
  height: 320px;
  background: var(--fsd-accent);
  bottom: -60px;
  left: 12%;
}

.login-card {
  position: relative;
  width: 408px;
  padding: 44px 40px 40px;
  background: rgba(18, 24, 33, 0.86);
  border: 1px solid var(--fsd-border-active);
  border-radius: var(--fsd-radius-xl);
  box-shadow: var(--fsd-shadow-elevated);
  backdrop-filter: blur(16px) saturate(140%);
}

.login-brand {
  text-align: center;
  margin-bottom: 32px;

  .brand-icon {
    display: inline-flex;
    margin-bottom: 16px;
    border-radius: 14px;
    box-shadow: 0 8px 24px rgba(34, 199, 230, 0.32);
  }

  .brand-icon svg {
    width: 52px;
    height: 52px;
    display: block;
    border-radius: 14px;
  }

  h1 {
    margin: 0;
    font-size: 26px;
    font-weight: 700;
    letter-spacing: -0.02em;
    background: linear-gradient(90deg, var(--fsd-text-primary) 0%, var(--fsd-accent) 100%);
    -webkit-background-clip: text;
    background-clip: text;
    -webkit-text-fill-color: transparent;
  }

  p {
    margin: 8px 0 0;
    font-size: 13px;
    color: var(--fsd-text-tertiary);
    letter-spacing: 0.02em;
  }
}

.login-btn {
  margin-top: 8px;
  height: 46px !important;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 0.04em;
}
</style>
