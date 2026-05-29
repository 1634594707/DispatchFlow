import axios from 'axios'
import type { AxiosInstance, InternalAxiosRequestConfig, AxiosResponse } from 'axios'
import { message } from 'ant-design-vue'
import { API_BASE, REQUEST_TIMEOUT } from '@/config'
import type { ApiResponse } from '@/types/api'

const TOKEN_KEY = 'fsd_admin_token'

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
    const { data } = response
    if (data.success) {
      return data as any
    }
    const errMsg = friendlyApiMessage(data.code, data.message)
    if (data.code === 'ADMIN_AUTH_REQUIRED' || data.code === 'ADMIN_AUTH_FAILED') {
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem('fsd_admin_user')
      if (!window.location.pathname.startsWith('/login')) {
        window.location.href = '/login'
      }
    }
    message.error(errMsg)
    return Promise.reject(new Error(errMsg))
  },
  (error) => {
    if (error.response) {
      const { status, data } = error.response
      switch (status) {
        case 401:
          message.error('未授权，请重新登录')
          break
        case 403:
          message.error('无权限访问')
          break
        case 404:
          message.error('数据不存在或已被删除')
          break
        case 409:
          message.error('数据已被修改，请刷新后重试')
          break
        case 422:
          message.error(data?.message || '参数校验失败')
          break
        case 500:
          message.error('服务器繁忙，请稍后重试')
          break
        default:
          message.error(data?.message || `请求失败 (${status})`)
      }
    } else if (error.code === 'ECONNABORTED') {
      message.error('网络超时，请检查网络后重试')
    } else {
      message.error('网络异常，请检查网络后重试')
    }
    return Promise.reject(error)
  }
)

function friendlyApiMessage(code?: string, raw?: string): string {
  if (!raw) return '请求失败'
  if (code === 'ADMIN_AUTH_REQUIRED') return '请先登录'
  if (code === 'ADMIN_AUTH_FAILED') return '登录已失效，请重新登录'
  if (raw.includes('No static resource') || code === 'NOT_FOUND') {
    return '后端接口未找到，请在 back/fsd-bootstrap 目录执行 mvn spring-boot:run 并重启后端'
  }
  return raw
}

export default instance
