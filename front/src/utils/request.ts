import axios from 'axios'
import type { AxiosInstance, InternalAxiosRequestConfig, AxiosResponse } from 'axios'
import { message } from 'ant-design-vue'
import { API_BASE, REQUEST_TIMEOUT } from '@/config'
import type { ApiResponse } from '@/types/api'

const instance: AxiosInstance = axios.create({
  baseURL: API_BASE,
  timeout: REQUEST_TIMEOUT,
  headers: {
    'Content-Type': 'application/json',
  },
})

instance.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
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
    message.error(data.message || '请求失败')
    return Promise.reject(new Error(data.message || '请求失败'))
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

export default instance
