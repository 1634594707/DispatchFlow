/**
 * V5-S2: 角色权限复制 Composable
 *
 * 提供角色模板管理和权限复制功能。
 */
import { ref, computed } from 'vue'
import { fetchRoleTemplates, copyRolePermissions } from '@/api/auth'
import type { RoleTemplate, AdminUser, CopyRolePermissionsPayload } from '@/types/auth'

export function useRoleCopy() {
  const availableRoles = ref<RoleTemplate[]>([])
  const sourceRole = ref<RoleTemplate | null>(null)
  const copiedPermissions = ref<string[]>([])
  const loadingRoles = ref(false)
  const roleTemplatesReady = ref(false)
  const copying = ref(false)

  /** 加载所有角色模板 */
  async function loadRoleTemplates() {
    loadingRoles.value = true
    try {
      const res = await fetchRoleTemplates()
      availableRoles.value = res.data
      roleTemplatesReady.value = true
    } catch {
      availableRoles.value = []
      roleTemplatesReady.value = false
    } finally {
      loadingRoles.value = false
    }
  }

  /** 根据角色名选中模板并加载其权限 */
  function loadRolePermissions(role: AdminUser['role']) {
    const found = availableRoles.value.find((r) => r.role === role)
    if (found) {
      sourceRole.value = found
      copiedPermissions.value = [...found.permissions]
    } else {
      sourceRole.value = null
      copiedPermissions.value = []
    }
  }

  /** 将源用户的角色权限应用到目标用户 */
  async function applyRoleCopy(sourceUserId: number, targetUserId: number) {
    copying.value = true
    try {
      const payload: CopyRolePermissionsPayload = {
        sourceUserId,
        targetUserId,
      }
      await copyRolePermissions(payload)
      return true
    } finally {
      copying.value = false
    }
  }

  /** 获取某角色拥有的权限列表（用于可视化展示） */
  function getRolePermissions(role: AdminUser['role']): string[] {
    const found = availableRoles.value.find((r) => r.role === role)
    return found?.permissions ?? []
  }

  /** 角色描述 */
  function getRoleDescription(role: AdminUser['role']): string {
    const found = availableRoles.value.find((r) => r.role === role)
    return found?.description ?? ''
  }

  /** 角色模板的权限概览 */
  const roleSummary = computed(() => {
    return availableRoles.value.map((t) => ({
      role: t.role,
      label: t.label,
      permissionCount: t.permissions.length,
      description: t.description,
    }))
  })

  return {
    availableRoles,
    sourceRole,
    copiedPermissions,
    loadingRoles,
    roleTemplatesReady,
    copying,
    loadRoleTemplates,
    loadRolePermissions,
    applyRoleCopy,
    getRolePermissions,
    getRoleDescription,
    roleSummary,
  }
}