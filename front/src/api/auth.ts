import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'
import type {
  AdminLoginResult,
  AdminUser,
  AdminUserCreatePayload,
  AdminUserUpdatePayload,
  ChangePasswordPayload,
} from '@/types/auth'

export function login(username: string, password: string, totpCode?: string) {
  return request.post<any, ApiResponse<AdminLoginResult>>('/admin/auth/login', {
    username,
    password,
    totpCode,
  })
}

export function enrollTotp() {
  return request.post<any, ApiResponse<{ secret: string; otpauthUrl: string }>>('/admin/auth/totp/enroll')
}

export function enableTotp(code: string) {
  return request.post<any, ApiResponse<void>>('/admin/auth/totp/enable', { code })
}

export function disableTotp(code: string) {
  return request.post<any, ApiResponse<void>>('/admin/auth/totp/disable', { code })
}

export function logout() {
  return request.post<any, ApiResponse<void>>('/admin/auth/logout')
}

export function fetchCurrentUser() {
  return request.get<any, ApiResponse<AdminUser>>('/admin/auth/me')
}

export function changePassword(payload: ChangePasswordPayload) {
  return request.post<any, ApiResponse<void>>('/admin/auth/change-password', payload)
}

export function fetchUsers() {
  return request.get<any, ApiResponse<AdminUser[]>>('/admin/users')
}

export function createUser(payload: AdminUserCreatePayload) {
  return request.post<any, ApiResponse<AdminUser>>('/admin/users', payload)
}

export function updateUser(userId: number, payload: AdminUserUpdatePayload) {
  return request.put<any, ApiResponse<AdminUser>>(`/admin/users/${userId}`, payload)
}

export function disableUser(userId: number) {
  return request.post<any, ApiResponse<void>>(`/admin/users/${userId}/disable`)
}
