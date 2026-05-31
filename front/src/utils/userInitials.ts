export type UserAvatarRole = 'ADMIN' | 'OPERATOR' | 'FIELD_OPS' | 'VIEWER' | string | undefined

const PRESET_INITIALS: Record<string, string> = {
  系统管理员: '管理',
}

/** 参考 GitHub / 飞书：优先账号名，中文取末两字更易辨认 */
export function userInitials(name?: string | null, username?: string | null): string {
  const display = (name ?? '').trim()
  const account = (username ?? '').trim()

  if (display && PRESET_INITIALS[display]) {
    return PRESET_INITIALS[display]
  }

  if (account && /^[a-zA-Z][a-zA-Z0-9._-]*$/.test(account)) {
    return account.slice(0, 2).toUpperCase()
  }

  if (!display) return 'U'

  if (/[\u4e00-\u9fff]/.test(display)) {
    if (display.length === 1) return display
    if (display.length === 2) return display
    return display.slice(-2)
  }

  const parts = display.split(/\s+/).filter(Boolean)
  if (parts.length >= 2) {
    return `${parts[0][0] ?? ''}${parts[1][0] ?? ''}`.toUpperCase()
  }
  return display.slice(0, 2).toUpperCase()
}

const AVATAR_PALETTE: Array<[string, string]> = [
  ['#1E88E5', '#1565C0'],
  ['#5C6BC0', '#3949AB'],
  ['#26A69A', '#00897B'],
  ['#43A047', '#2E7D32'],
  ['#7E57C2', '#5E35B1'],
  ['#EC407A', '#C2185B'],
  ['#FB8C00', '#EF6C00'],
  ['#00ACC1', '#00838F'],
]

function hashName(name: string): number {
  let hash = 0
  for (let i = 0; i < name.length; i += 1) {
    hash = name.charCodeAt(i) + ((hash << 5) - hash)
  }
  return Math.abs(hash)
}

export function userAvatarBackground(name?: string | null, role?: UserAvatarRole): string {
  if (role === 'VIEWER') {
    return 'linear-gradient(145deg, #78909C 0%, #546E7A 100%)'
  }
  const key = (name ?? '').trim() || 'guest'
  const [from, to] = AVATAR_PALETTE[hashName(key) % AVATAR_PALETTE.length]
  return `linear-gradient(145deg, ${from} 0%, ${to} 100%)`
}

export function isAdminRole(role?: UserAvatarRole): boolean {
  return role === 'ADMIN'
}

export function avatarLabelSize(initials: string, size: number): number {
  const len = [...initials].length
  if (len <= 1) return Math.round(size * 0.44)
  return Math.round(size * 0.34)
}
