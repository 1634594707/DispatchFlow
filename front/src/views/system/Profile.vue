<template>
  <PageContainer title="个人设置" subtitle="管理账号信息与密码">
    <div class="profile-grid">
      <a-card title="账号信息" class="profile-card">
        <a-descriptions :column="1" bordered size="small">
          <a-descriptions-item label="用户名">{{ authStore.user?.username }}</a-descriptions-item>
          <a-descriptions-item label="显示名称">{{ authStore.user?.displayName }}</a-descriptions-item>
          <a-descriptions-item label="角色">
            <a-tag :color="roleColor">{{ roleLabel }}</a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="最后登录">
            {{ lastLoginText }}
          </a-descriptions-item>
        </a-descriptions>
      </a-card>

      <a-card title="修改密码" class="profile-card">
        <a-form layout="vertical" :model="pwdForm" @finish="handleChangePassword">
          <a-form-item label="原密码" required>
            <a-input-password v-model:value="pwdForm.oldPassword" />
          </a-form-item>
          <a-form-item label="新密码" required>
            <a-input-password v-model:value="pwdForm.newPassword" placeholder="至少 6 位" />
          </a-form-item>
          <a-form-item label="确认新密码" required>
            <a-input-password v-model:value="pwdForm.confirmPassword" />
          </a-form-item>
          <a-button type="primary" html-type="submit" :loading="changing">
            更新密码
          </a-button>
        </a-form>
      </a-card>
    </div>
  </PageContainer>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import dayjs from 'dayjs'
import PageContainer from '@/components/common/PageContainer.vue'
import { useAuthStore } from '@/stores/auth'
import * as authApi from '@/api/auth'

const router = useRouter()
const authStore = useAuthStore()
const changing = ref(false)

const pwdForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const roleLabel = computed(() => {
  const map: Record<string, string> = {
    VIEWER: '观察员',
    OPERATOR: '调度员',
    ADMIN: '管理员',
  }
  return map[authStore.user?.role || ''] || authStore.user?.role
})

const roleColor = computed(() => {
  const map: Record<string, string> = {
    VIEWER: 'default',
    OPERATOR: 'processing',
    ADMIN: 'cyan',
  }
  return map[authStore.user?.role || ''] || 'default'
})

const lastLoginText = computed(() => {
  const t = authStore.user?.lastLoginAt
  return t ? dayjs(t).format('YYYY-MM-DD HH:mm:ss') : '-'
})

async function handleChangePassword() {
  if (pwdForm.newPassword !== pwdForm.confirmPassword) {
    message.warning('两次输入的新密码不一致')
    return
  }
  changing.value = true
  try {
    await authApi.changePassword({
      oldPassword: pwdForm.oldPassword,
      newPassword: pwdForm.newPassword,
    })
    message.success('密码已更新，请重新登录')
    await authStore.logout()
    router.replace('/login')
  } finally {
    changing.value = false
  }
}

onMounted(() => {
  authStore.fetchMe().catch(() => {})
})
</script>

<style scoped lang="less">
.profile-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;

  @media (max-width: 900px) {
    grid-template-columns: 1fr;
  }
}

.profile-card {
  background: var(--fsd-bg-container);
  border-color: var(--fsd-border);

  :deep(.ant-descriptions-bordered .ant-descriptions-item-label) {
    background: var(--fsd-bg-elevated);
    color: var(--fsd-text-secondary);
    border-color: var(--fsd-border);
  }

  :deep(.ant-descriptions-bordered .ant-descriptions-item-content) {
    background: var(--fsd-bg-base);
    color: var(--fsd-text-primary);
    border-color: var(--fsd-border);
  }

  :deep(.ant-descriptions-bordered .ant-descriptions-view) {
    border-color: var(--fsd-border);
  }
}
</style>
