import axios from 'axios'
import type { AxiosInstance, InternalAxiosRequestConfig, AxiosResponse } from 'axios'
import { message } from 'ant-design-vue'
import { getActivePinia } from 'pinia'
import { API_BASE, REQUEST_TIMEOUT } from '@/config'
import type { ApiResponse } from '@/types/api'
import { useParkScopeStore } from '@/stores/parkScope'
import { useApiErrorsStore } from '@/stores/apiErrors'

const TOKEN_KEY = 'fsd_admin_token'

declare module 'axios' {
  interface AxiosRequestConfig {
    skipErrorToast?: boolean
  }
}

const instance: AxiosInstance = axios.create({
  baseURL: API_BASE,
  timeout: REQUEST_TIMEOUT,
  headers: {
    'Content-Type': 'application/json;charset=UTF-8',
    Accept: 'application/json;charset=UTF-8',
  },
})

instance.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = localStorage.getItem(TOKEN_KEY)
    if (token) {
      config.headers['X-Admin-Token'] = token
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

instance.interceptors.response.use(
  (response: AxiosResponse<ApiResponse<any>>) => {
    if (response.config.responseType === 'blob') {
      return response.data as any
    }
    const { data } = response
    if (data.success) {
      return data as any
    }
    const errMsg = friendlyApiMessage(data.code, data.message)
    const pinia = getActivePinia()
    if (pinia) {
      useApiErrorsStore(pinia).push({
        code: data.code || 'UNKNOWN',
        message: errMsg,
        rawMessage: data.message || '',
        url: response.config.url || '',
        method: (response.config.method || 'GET').toUpperCase(),
        status: response.status,
      })
    }
    if (data.code === 'PARK_NOT_FOUND') {
      localStorage.removeItem('fsd_selected_park_id')
      const pinia = getActivePinia()
      if (pinia) {
        useParkScopeStore(pinia).setParkId(undefined)
      }
      return Promise.reject(new Error('PARK_NOT_FOUND'))
    }
    if (data.code === 'ADMIN_AUTH_REQUIRED' || data.code === 'ADMIN_AUTH_FAILED') {
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem('fsd_admin_user')
      if (!window.location.pathname.startsWith('/login')) {
        window.location.href = '/login'
      }
    }
    if (!response.config.skipErrorToast) {
      message.error(errMsg)
    }
    return Promise.reject(new Error(errMsg))
  },
  (error) => {
    const skipToast = error.config?.skipErrorToast
    if (error.response) {
      const { status, data } = error.response
      const httpFriendlyMsg = friendlyHttpErrorMessage(status, data)
      if (!skipToast) {
        message.error(httpFriendlyMsg)
      }
      const pinia = getActivePinia()
      if (pinia && !skipToast) {
        useApiErrorsStore(pinia).push({
          code: data?.code || `HTTP_${status}`,
          message: httpFriendlyMsg,
          rawMessage: data?.message || error.message || '',
          url: error.config?.url || '',
          method: (error.config?.method || 'GET').toUpperCase(),
          status,
        })
      }
    } else if (error.code === 'ECONNABORTED') {
      if (!error.config?.skipErrorToast) message.error('网络超时，请检查网络后重试')
    } else if (!error.config?.skipErrorToast) {
      message.error('网络异常，请检查网络后重试')
    }
    return Promise.reject(error)
  }
)

function friendlyApiMessage(code?: string, raw?: string): string {
  if (!raw) return '请求失败'
  if (code === 'ADMIN_AUTH_REQUIRED') return '请先登录'
  if (code === 'ADMIN_AUTH_FAILED') return '登录已失效，请重新登录'
  if (code === 'ADMIN_FORBIDDEN') return '当前账号无写操作权限，请联系管理员'
  if (code === 'PARK_NOT_FOUND') return '所选园区无效或已停用，已重置为全部园区'
  if (code === 'PARK_STATION_NOT_FOUND') {
    return '站点已失效（可能为旧厂内示意站），请刷新页面后重试'
  }
  if (code === 'DISPATCH_TASK_STATUS_INVALID' || code === 'INVALID_STATUS') {
    return raw || '当前任务状态不允许自动派车，请先取消老任务或重启后端后再试'
  }
  if (code === 'DISPATCH_TASK_LOCKED') return '任务正在处理中，请几秒后重试'
  if (raw.includes('No static resource') || code === 'NOT_FOUND') {
    return '后端接口未找到，请在 back/fsd-bootstrap 目录执行 mvn spring-boot:run 并重启后端'
  }
  return raw
}

function friendlyHttpErrorMessage(status: number, data?: { code?: string; message?: string }): string {
  switch (status) {
    case 401:
      return '未授权，请重新登录'
    case 403:
      return '无权限访问'
    case 404:
      return '数据不存在或已被删除'
    case 409:
      return '数据已被修改，请刷新后重试'
    case 422:
      return data?.message || '参数校验失败'
    case 500:
      return '服务器繁忙，请稍后重试'
    default:
      return friendlyApiMessage(data?.code, data?.message) || data?.message || `请求失败 (${status})`
  }
}

export default instance
