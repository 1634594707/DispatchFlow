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
            <a-button v-if="roleTemplatesReady && !editingUser" type="link" size="small" @click="openRoleCopy(record)">复制角色</a-button>
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

    <!-- 编辑 / 新建用户弹窗 -->
    <a-modal
      v-model:open="modalOpen"
      :title="editingUser ? '编辑用户' : '新建用户'"
      :confirm-loading="saving"
      width="600px"
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
          <a-select v-model:value="form.role" :options="roleOptions" @change="onRoleChange" />
        </a-form-item>

        <!-- V5-S2: 基于已有角色复制 -->
        <a-form-item v-if="!editingUser && roleTemplatesReady" label="基于已有角色复制（可选）">
          <a-select
            v-model:value="copySourceUserId"
            placeholder="选择源用户"
            allow-clear
            :options="userCopyOptions"
            @change="onCopySourceChange"
          />
        </a-form-item>

        <!-- 角色模板可视化 -->
        <a-form-item v-if="!editingUser && roleSummary.length > 0" label="角色权限概览">
          <div class="role-template-grid">
            <div
              v-for="tmpl in roleSummary"
              :key="tmpl.role"
              class="role-template-card"
              :class="{ active: tmpl.role === form.role }"
            >
              <div class="role-template-header">
                <a-tag :color="roleColor(tmpl.role)">{{ tmpl.label }}</a-tag>
                <small>{{ tmpl.permissionCount }} 项权限</small>
              </div>
              <p class="role-template-desc">{{ tmpl.description }}</p>
            </div>
          </div>
        </a-form-item>

        <a-form-item v-if="editingUser" label="状态">
          <a-select v-model:value="form.status" :options="statusOptions" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- V5-S2: 角色复制弹窗 -->
    <a-modal
      v-model:open="roleCopyModalOpen"
      title="复制角色权限"
      :confirm-loading="roleCopying"
      @ok="handleRoleCopyConfirm"
    >
      <a-form layout="vertical">
        <a-form-item label="目标用户">
          <strong>{{ roleCopyTargetUser?.displayName }} ({{ roleCopyTargetUser?.username }})</strong>
        </a-form-item>
        <a-form-item label="从以下用户复制" required>
          <a-select
            v-model:value="roleCopySourceUserId"
            placeholder="选择源用户"
            :options="roleCopyUserOptions"
          />
        </a-form-item>
        <a-form-item v-if="selectedSourceRole" label="源用户角色">
          <a-tag :color="roleColor(selectedSourceRole)">{{ roleLabel(selectedSourceRole) }}</a-tag>
        </a-form-item>
        <a-form-item label="角色模板参考">
          <div class="role-template-grid">
            <div
              v-for="tmpl in roleSummary"
              :key="tmpl.role"
              class="role-template-card"
            >
              <div class="role-template-header">
                <a-tag :color="roleColor(tmpl.role)">{{ tmpl.label }}</a-tag>
                <small>{{ tmpl.permissionCount }} 项权限</small>
              </div>
              <p class="role-template-desc">{{ tmpl.description }}</p>
            </div>
          </div>
        </a-form-item>
      </a-form>
    </a-modal>
  </PageContainer>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import dayjs from 'dayjs'
import PageContainer from '@/components/common/PageContainer.vue'
import * as authApi from '@/api/auth'
import type { AdminUser } from '@/types/auth'
import { useRoleCopy } from '@/composables/useRoleCopy'

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

/* ---- V5-S2: 角色复制 ---- */
const {
  copying: roleCopying,
  loadRoleTemplates,
  loadRolePermissions,
  applyRoleCopy,
  roleSummary,
  roleTemplatesReady,
} = useRoleCopy()

const copySourceUserId = ref<number | undefined>(undefined)
const roleCopyModalOpen = ref(false)
const roleCopyTargetUser = ref<AdminUser | null>(null)
const roleCopySourceUserId = ref<number | undefined>(undefined)

/** 新建时，作为复制来源的用户列表 */
const userCopyOptions = computed(() => {
  return users.value
    .filter((u) => u.status === 'ACTIVE')
    .map((u) => ({
      label: `${u.displayName} (${u.username}) - ${roleLabel(u.role)}`,
      value: u.id,
    }))
})

/** 角色复制弹窗中的源用户列表（排除目标用户自己） */
const roleCopyUserOptions = computed(() => {
  return users.value
    .filter((u) => u.id !== roleCopyTargetUser.value?.id && u.status === 'ACTIVE')
    .map((u) => ({
      label: `${u.displayName} (${u.username}) - ${roleLabel(u.role)}`,
      value: u.id,
    }))
})

/** 选中源用户后，获取其角色 */
const selectedSourceRole = computed(() => {
  if (!roleCopySourceUserId.value) return null
  const user = users.value.find((u) => u.id === roleCopySourceUserId.value)
  return user?.role ?? null
})

const columns = [
  { title: '用户名', dataIndex: 'username', key: 'username' },
  { title: '显示名称', dataIndex: 'displayName', key: 'displayName' },
  { title: '角色', key: 'role', width: 120 },
  { title: '状态', key: 'status', width: 100 },
  { title: '最后登录', key: 'lastLoginAt', width: 180 },
  { title: '操作', key: 'actions', width: 200 },
]

const roleOptions = [
  { label: '观察员 (VIEWER)', value: 'VIEWER' },
  { label: '调度员 (OPERATOR)', value: 'OPERATOR' },
  { label: '现场运维 (FIELD_OPS)', value: 'FIELD_OPS' },
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
    FIELD_OPS: '现场运维',
    ADMIN: '管理员',
  }
  return map[role] || role
}

function roleColor(role: string) {
  const map: Record<string, string> = {
    VIEWER: 'default',
    OPERATOR: 'processing',
    FIELD_OPS: 'orange',
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
  copySourceUserId.value = undefined
  modalOpen.value = true
}

function openEdit(user: AdminUser) {
  editingUser.value = user
  form.displayName = user.displayName
  form.role = user.role
  form.status = user.status
  modalOpen.value = true
}

/** V5-S2: 打开角色复制弹窗 */
function openRoleCopy(user: AdminUser) {
  roleCopyTargetUser.value = user
  roleCopySourceUserId.value = undefined
  roleCopyModalOpen.value = true
}

/** V5-S2: 确认执行角色复制 */
async function handleRoleCopyConfirm() {
  if (!roleCopyTargetUser.value || !roleCopySourceUserId.value) {
    message.warning('请选择源用户')
    return
  }
  const ok = await applyRoleCopy(roleCopySourceUserId.value, roleCopyTargetUser.value.id)
  if (ok) {
    message.success(`已将角色权限复制到 ${roleCopyTargetUser.value.displayName}`)
  }
  roleCopyModalOpen.value = false
  await loadUsers()
}

/** V5-S2: 当表单中的角色改变时，更新角色模板高亮 */
function onRoleChange(role: AdminUser['role']) {
  loadRolePermissions(role)
}

/** V5-S2: 当选择复制来源用户时，填充角色 */
function onCopySourceChange(userId: number | undefined) {
  if (!userId) return
  const user = users.value.find((u) => u.id === userId)
  if (user) {
    form.role = user.role
    loadRolePermissions(user.role)
    message.info(`已加载 ${user.displayName} 的角色：${roleLabel(user.role)}`)
  }
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

onMounted(() => {
  loadUsers()
  loadRoleTemplates()
})
</script>

<style scoped lang="less">
.role-template-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.role-template-card {
  flex: 1;
  min-width: 140px;
  padding: 10px 12px;
  border: 1px solid var(--fsd-border);
  border-radius: var(--fsd-radius-md);
  background: var(--fsd-bg-elevated);
  transition: border-color 0.2s;
}

.role-template-card.active {
  border-color: var(--fsd-accent);
  box-shadow: 0 0 0 1px var(--fsd-accent);
}

.role-template-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;

  small {
    color: var(--fsd-text-tertiary);
    font-size: 12px;
  }
}

.role-template-desc {
  margin: 0;
  font-size: 12px;
  color: var(--fsd-text-secondary);
}

@media (max-width: 768px) {
  .role-template-grid {
    flex-direction: column;
  }
}
</style>