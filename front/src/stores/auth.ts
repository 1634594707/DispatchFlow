import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import * as authApi from '@/api/auth'
import type { AdminUser } from '@/types/auth'
import { ADMIN_AUTH_ENABLED } from '@/config'

const TOKEN_KEY = 'fsd_admin_token'
const USER_KEY = 'fsd_admin_user'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem(TOKEN_KEY))
  const user = ref<AdminUser | null>(readStoredUser())
  const loading = ref(false)

  const isAuthenticated = computed(() => !!token.value)
  const isAdmin = computed(() => user.value?.role === 'ADMIN')
  const isOperator = computed(() => user.value?.role === 'OPERATOR' || isAdmin.value)
  const canWrite = computed(() => user.value?.role !== 'VIEWER')
  const displayName = computed(() => user.value?.displayName || user.value?.username || '管理员')

  function readStoredUser(): AdminUser | null {
    const raw = localStorage.getItem(USER_KEY)
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
    localStorage.setItem(TOKEN_KEY, newToken)
    localStorage.setItem(USER_KEY, JSON.stringify(newUser))
  }

  function clearSession() {
    token.value = null
    user.value = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
  }

  async function login(username: string, password: string) {
    loading.value = true
    try {
      const result = await authApi.login(username, password)
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
    localStorage.setItem(USER_KEY, JSON.stringify(me.data))
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
    isOperator,
    canWrite,
    displayName,
    login,
    logout,
    fetchMe,
    ensureAuth,
    clearSession,
  }
})
