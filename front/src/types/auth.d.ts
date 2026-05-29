export interface AdminUser {
  id: number
  username: string
  displayName: string
  role: 'VIEWER' | 'OPERATOR' | 'ADMIN'
  status: 'ACTIVE' | 'DISABLED'
  lastLoginAt?: string
  createdAt?: string
}

export interface AdminLoginResult {
  token: string
  user: AdminUser
}

export interface AdminUserCreatePayload {
  username: string
  password: string
  displayName: string
  role: AdminUser['role']
}

export interface AdminUserUpdatePayload {
  displayName?: string
  role?: AdminUser['role']
  status?: AdminUser['status']
}

export interface ChangePasswordPayload {
  oldPassword: string
  newPassword: string
}
