export interface AdminUser {
  id: number
  username: string
  displayName: string
  role: 'VIEWER' | 'OPERATOR' | 'FIELD_OPS' | 'ADMIN'
  status: 'ACTIVE' | 'DISABLED'
  totpEnabled?: boolean
  lastLoginAt?: string
  createdAt?: string
}

export interface AdminLoginResult {
  token: string
  requiresTotp?: boolean
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
