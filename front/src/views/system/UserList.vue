<template>
  <PageContainer title="用户管理" subtitle="管理系统后台账号与角色权限">
    <template #actions>
      <a-button type="primary" @click="openCreate">
        <PlusOutlined /> 新建用户
      </a-button>
    </template>

    <a-table
      :columns="columns"
      :data-source="users"
      :loading="loading"
      row-key="id"
      :pagination="false"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'role'">
          <a-tag :color="roleColor(record.role)">{{ roleLabel(record.role) }}</a-tag>
        </template>
        <template v-else-if="column.key === 'status'">
          <a-tag :color="record.status === 'ACTIVE' ? 'success' : 'default'">
            {{ record.status === 'ACTIVE' ? '正常' : '已禁用' }}
          </a-tag>
        </template>
        <template v-else-if="column.key === 'lastLoginAt'">
          {{ record.lastLoginAt ? formatTime(record.lastLoginAt) : '-' }}
        </template>
        <template v-else-if="column.key === 'actions'">
          <a-space>
            <a-button type="link" size="small" @click="openEdit(record)">编辑</a-button>
            <a-popconfirm
              v-if="record.status === 'ACTIVE' && record.username !== 'admin'"
              title="确定禁用该用户？"
              @confirm="handleDisable(record.id)"
            >
              <a-button type="link" size="small" danger>禁用</a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>

    <a-modal
      v-model:open="modalOpen"
      :title="editingUser ? '编辑用户' : '新建用户'"
      :confirm-loading="saving"
      @ok="handleSave"
    >
      <a-form layout="vertical">
        <a-form-item v-if="!editingUser" label="用户名" required>
          <a-input v-model:value="form.username" placeholder="3-64 位字符" />
        </a-form-item>
        <a-form-item v-if="!editingUser" label="初始密码" required>
          <a-input-password v-model:value="form.password" placeholder="至少 6 位" />
        </a-form-item>
        <a-form-item label="显示名称" required>
          <a-input v-model:value="form.displayName" />
        </a-form-item>
        <a-form-item label="角色" required>
          <a-select v-model:value="form.role" :options="roleOptions" />
        </a-form-item>
        <a-form-item v-if="editingUser" label="状态">
          <a-select v-model:value="form.status" :options="statusOptions" />
        </a-form-item>
      </a-form>
    </a-modal>
  </PageContainer>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import dayjs from 'dayjs'
import PageContainer from '@/components/common/PageContainer.vue'
import * as authApi from '@/api/auth'
import type { AdminUser } from '@/types/auth'

const loading = ref(false)
const saving = ref(false)
const users = ref<AdminUser[]>([])
const modalOpen = ref(false)
const editingUser = ref<AdminUser | null>(null)

const form = reactive({
  username: '',
  password: '',
  displayName: '',
  role: 'OPERATOR' as AdminUser['role'],
  status: 'ACTIVE' as AdminUser['status'],
})

const columns = [
  { title: '用户名', dataIndex: 'username', key: 'username' },
  { title: '显示名称', dataIndex: 'displayName', key: 'displayName' },
  { title: '角色', key: 'role', width: 120 },
  { title: '状态', key: 'status', width: 100 },
  { title: '最后登录', key: 'lastLoginAt', width: 180 },
  { title: '操作', key: 'actions', width: 140 },
]

const roleOptions = [
  { label: '观察员 (VIEWER)', value: 'VIEWER' },
  { label: '调度员 (OPERATOR)', value: 'OPERATOR' },
  { label: '管理员 (ADMIN)', value: 'ADMIN' },
]

const statusOptions = [
  { label: '正常', value: 'ACTIVE' },
  { label: '已禁用', value: 'DISABLED' },
]

function roleLabel(role: string) {
  const map: Record<string, string> = {
    VIEWER: '观察员',
    OPERATOR: '调度员',
    ADMIN: '管理员',
  }
  return map[role] || role
}

function roleColor(role: string) {
  const map: Record<string, string> = {
    VIEWER: 'default',
    OPERATOR: 'processing',
    ADMIN: 'cyan',
  }
  return map[role] || 'default'
}

function formatTime(value: string) {
  return dayjs(value).format('YYYY-MM-DD HH:mm:ss')
}

async function loadUsers() {
  loading.value = true
  try {
    users.value = (await authApi.fetchUsers()).data
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingUser.value = null
  form.username = ''
  form.password = ''
  form.displayName = ''
  form.role = 'OPERATOR'
  form.status = 'ACTIVE'
  modalOpen.value = true
}

function openEdit(user: AdminUser) {
  editingUser.value = user
  form.displayName = user.displayName
  form.role = user.role
  form.status = user.status
  modalOpen.value = true
}

async function handleSave() {
  saving.value = true
  try {
    if (editingUser.value) {
      await authApi.updateUser(editingUser.value.id, {
        displayName: form.displayName,
        role: form.role,
        status: form.status,
      })
      message.success('用户已更新')
    } else {
      if (!form.username || !form.password || !form.displayName) {
        message.warning('请填写完整信息')
        return
      }
      await authApi.createUser({
        username: form.username,
        password: form.password,
        displayName: form.displayName,
        role: form.role,
      })
      message.success('用户已创建')
    }
    modalOpen.value = false
    await loadUsers()
  } finally {
    saving.value = false
  }
}

async function handleDisable(userId: number) {
  await authApi.disableUser(userId)
  message.success('用户已禁用')
  await loadUsers()
}

onMounted(loadUsers)
</script>
