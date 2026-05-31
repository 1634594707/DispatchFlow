<template>
  <span
    class="user-avatar-wrap"
    :class="{ 'user-avatar-wrap--sm': size <= 28 }"
    :style="wrapStyle"
  >
    <span class="user-avatar" :style="avatarStyle" role="img" :aria-label="ariaLabel">
      <span class="user-avatar__label" :style="labelStyle">{{ initials }}</span>
    </span>
    <span
      v-if="showAdminBadge"
      class="user-avatar__role-badge"
      title="系统管理员"
      aria-hidden="true"
    />
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import {
  avatarLabelSize,
  isAdminRole,
  userAvatarBackground,
  userInitials,
  type UserAvatarRole,
} from '@/utils/userInitials'

const props = withDefaults(
  defineProps<{
    name?: string | null
    username?: string | null
    role?: UserAvatarRole
    size?: number
  }>(),
  {
    name: '',
    username: '',
    size: 32,
  },
)

const initials = computed(() => userInitials(props.name, props.username))
const showAdminBadge = computed(() => isAdminRole(props.role))
const ariaLabel = computed(() => `${props.name || props.username || '用户'} 头像`)

const wrapStyle = computed(() => ({
  width: `${props.size}px`,
  height: `${props.size}px`,
}))

const avatarStyle = computed(() => ({
  width: `${props.size}px`,
  height: `${props.size}px`,
  background: userAvatarBackground(props.name, props.role),
}))

const labelStyle = computed(() => {
  const text = initials.value
  const len = [...text].length
  const fontSize = avatarLabelSize(text, props.size)
  return {
    fontSize: `${fontSize}px`,
    letterSpacing: len > 1 ? '-0.06em' : '0',
  }
})
</script>

<style scoped lang="less">
.user-avatar-wrap {
  position: relative;
  display: inline-flex;
  flex-shrink: 0;
  vertical-align: middle;
}

.user-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  border: 1px solid rgba(255, 255, 255, 0.16);
  box-shadow:
    0 1px 3px rgba(0, 0, 0, 0.22),
    inset 0 1px 0 rgba(255, 255, 255, 0.14);
  color: #ffffff;
  overflow: hidden;
  user-select: none;
}

.user-avatar__label {
  display: block;
  font-family: 'PingFang SC', 'Microsoft YaHei', 'Noto Sans SC', Inter, sans-serif;
  font-weight: 600;
  line-height: 1;
  transform: translateY(0.5px);
  white-space: nowrap;
}

.user-avatar__role-badge {
  position: absolute;
  right: 0;
  bottom: 0;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #42a5f5;
  border: 1.5px solid #161b22;
  box-shadow: 0 0 0 1px rgba(30, 136, 229, 0.4);
  pointer-events: none;
}

.user-avatar-wrap--sm .user-avatar__role-badge {
  width: 6px;
  height: 6px;
  border-width: 1px;
}
</style>
