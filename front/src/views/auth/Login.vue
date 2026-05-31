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
          <svg viewBox="0 0 32 32" fill="none" xmlns="http://www.w3.org/2000/svg">
            <rect width="32" height="32" rx="8" fill="url(#login-grad)" />
            <path d="M8 22L16 10L24 22H8Z" fill="white" opacity="0.9" />
            <circle cx="16" cy="18" r="2" fill="white" />
            <defs>
              <linearGradient id="login-grad" x1="0" y1="0" x2="32" y2="32">
                <stop stop-color="#00B4D8" />
                <stop offset="1" stop-color="#0077B6" />
              </linearGradient>
            </defs>
          </svg>
        </div>
        <h1>DispatchFlow</h1>
        <p>无人车调度管理平台</p>
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
            placeholder="admin"
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

      <div class="login-hint">
        <span>默认账号：admin / admin123</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import { UserOutlined, LockOutlined } from '@ant-design/icons-vue'
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
    router.replace(redirect)
  } catch {
    // error handled by request interceptor
  }
}
</script>

<style scoped lang="less">
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #06090f;
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
    linear-gradient(rgba(0, 180, 216, 0.03) 1px, transparent 1px),
    linear-gradient(90deg, rgba(0, 180, 216, 0.03) 1px, transparent 1px);
  background-size: 48px 48px;
}

.glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.35;
}

.glow-1 {
  width: 400px;
  height: 400px;
  background: #00b4d8;
  top: -100px;
  right: 10%;
}

.glow-2 {
  width: 300px;
  height: 300px;
  background: #0077b6;
  bottom: -50px;
  left: 15%;
}

.login-card {
  position: relative;
  width: 400px;
  padding: 40px;
  background: rgba(13, 17, 23, 0.92);
  border: 1px solid #21262d;
  border-radius: 16px;
  box-shadow: 0 24px 64px rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(12px);
}

.login-brand {
  text-align: center;
  margin-bottom: 32px;

  .brand-icon svg {
    width: 48px;
    height: 48px;
    margin-bottom: 12px;
  }

  h1 {
    margin: 0;
    font-size: 24px;
    font-weight: 700;
    color: #f0f6fc;
    letter-spacing: -0.02em;
  }

  p {
    margin: 6px 0 0;
    font-size: 13px;
    color: #8b949e;
  }
}

.login-btn {
  margin-top: 8px;
  height: 44px !important;
  font-weight: 600;
}

.login-hint {
  margin-top: 20px;
  text-align: center;
  font-size: 12px;
  color: #6e7681;
}
</style>
