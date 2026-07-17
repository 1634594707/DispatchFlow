import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import * as authApi from '@/api/auth'
import type { AdminUser } from '@/types/auth'
import { ADMIN_AUTH_ENABLED } from '@/config'

export const TOKEN_KEY = 'fsd_admin_token'
const USER_KEY = 'fsd_admin_user'

/**
 * 检查 JWT token 是否已过期（解析 exp 字段）。
 * 若 token 不是 JWT 或无 exp 字段，则不做过期判断，交由后端校验。
 */
function isTokenExpired(token: string | null): boolean {
  if (!token) return true
  const parts = token.split('.')
  if (parts.length !== 3) return false
  try {
    const payload = JSON.parse(atob(parts[1]))
    if (!payload.exp) return false
    return Date.now() >= payload.exp * 1000
  } catch {
    return false
  }
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(sessionStorage.getItem(TOKEN_KEY))
  const user = ref<AdminUser | null>(readStoredUser())
  const loading = ref(false)

  const isAuthenticated = computed(() => !!token.value && !isTokenExpired(token.value))
  const isAdmin = computed(() => user.value?.role === 'ADMIN')
  const isFieldOps = computed(() => user.value?.role === 'FIELD_OPS')
  const isOperator = computed(() => user.value?.role === 'OPERATOR' || isAdmin.value)
  const canWrite = computed(() => user.value?.role !== 'VIEWER')
  const fieldOpsCanWrite = computed(() => isFieldOps.value || isOperator.value)
  const displayName = computed(() => user.value?.displayName || user.value?.username || '管理员')

  function readStoredUser(): AdminUser | null {
    const raw = sessionStorage.getItem(USER_KEY)
    if (!raw) return null
    try {
      return JSON.parse(raw) as AdminUser
    } catch {
      return null
    }
  }

  function persistSession(newToken: string, newUser: AdminUser) {
    token.value = newToken
    user.value = newUser
    sessionStorage.setItem(TOKEN_KEY, newToken)
    sessionStorage.setItem(USER_KEY, JSON.stringify(newUser))
  }

  function clearSession() {
    token.value = null
    user.value = null
    sessionStorage.removeItem(TOKEN_KEY)
    sessionStorage.removeItem(USER_KEY)
  }

  async function login(username: string, password: string, totpCode?: string) {
    loading.value = true
    try {
      const result = await authApi.login(username, password, totpCode)
      if (result.data.requiresTotp && !result.data.token) {
        return result
      }
      persistSession(result.data.token, result.data.user)
      return result
    } finally {
      loading.value = false
    }
  }

  async function logout() {
    try {
      if (token.value) {
        await authApi.logout()
      }
    } catch {
      // ignore logout errors
    } finally {
      clearSession()
    }
  }

  async function fetchMe() {
    if (!token.value) return null
    const me = await authApi.fetchCurrentUser()
    user.value = me.data
    sessionStorage.setItem(USER_KEY, JSON.stringify(me.data))
    return me
  }

  async function ensureAuth() {
    if (!ADMIN_AUTH_ENABLED) return true
    if (!token.value) return false
    try {
      await fetchMe()
      return true
    } catch {
      clearSession()
      return false
    }
  }

  return {
    token,
    user,
    loading,
    isAuthenticated,
    isAdmin,
    isFieldOps,
    isOperator,
    canWrite,
    fieldOpsCanWrite,
    displayName,
    login,
    logout,
    fetchMe,
    ensureAuth,
    clearSession,
  }
})
