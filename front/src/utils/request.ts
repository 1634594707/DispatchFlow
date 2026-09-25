import axios from 'axios'
import type { AxiosInstance, InternalAxiosRequestConfig, AxiosResponse } from 'axios'
import { message } from 'ant-design-vue'
import { getActivePinia } from 'pinia'
import { API_BASE, REQUEST_TIMEOUT } from '@/config'
import type { ApiResponse } from '@/types/api'
import { useParkScopeStore } from '@/stores/parkScope'
import { useApiErrorsStore } from '@/stores/apiErrors'
import router from '@/router'
import { TOKEN_KEY } from '@/stores/auth'

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

/**
 * 读取指定名称的 cookie 值（用于 CSRF 双提交 cookie 模式）。
 */
function getCookie(name: string): string | null {
  const match = document.cookie.match(new RegExp('(^|;\\s*)' + name + '=([^;]+)'))
  return match ? decodeURIComponent(match[2]) : null
}

/**
 * 顾客端（`/mobile/*`）是**匿名可下单**的页面：一次 401 不该把它弹回管理端登录页，
 * 也不该在页面上留一条"未授权，请重新登录"—— 那句话对一个没有账号可登的访客是误导。
 * 陈旧 token 仍然要清掉（留着只会一直 401），但**提示与跳转只留给管理端**。
 * 其余错误（例如任意点下单的三条受理判据）照旧显示，顾客端必须看得见拒因。
 */
function isCustomerRoute(): boolean {
  return router.currentRoute.value.path.startsWith('/mobile/')
}

const AUTH_FAILURE_CODES = new Set(['ADMIN_AUTH_REQUIRED', 'ADMIN_AUTH_FAILED'])

instance.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = sessionStorage.getItem(TOKEN_KEY)
    if (token) {
      config.headers['X-Admin-Token'] = token
    }
    // CSRF: 双提交 cookie 模式，从 cookie 读取 XSRF-TOKEN 并设置到请求头
    // 需后端在登录等响应中 Set-Cookie: XSRF-TOKEN=... 配合
    const xsrfToken = getCookie('XSRF-TOKEN')
    if (xsrfToken) {
      config.headers['X-XSRF-TOKEN'] = xsrfToken
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
    // P3-1: 提取 pinia 到函数顶部复用，避免在 if 分支内重复声明遮蔽外层变量
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
    } else {
      console.warn('[request] pinia 未初始化，跳过 API 错误记录')
    }
    if (data.code === 'PARK_NOT_FOUND') {
      localStorage.removeItem('fsd_selected_park_id')
      if (pinia) {
        useParkScopeStore(pinia).setParkId(undefined)
      } else {
        console.warn('[request] pinia 未初始化，跳过园区作用域重置')
      }
      return Promise.reject(new Error('PARK_NOT_FOUND'))
    }
    if (data.code === 'ADMIN_AUTH_REQUIRED' || data.code === 'ADMIN_AUTH_FAILED') {
      sessionStorage.removeItem(TOKEN_KEY)
      sessionStorage.removeItem('fsd_admin_user')
      if (!isCustomerRoute() && !router.currentRoute.value.path.startsWith('/login')) {
        router.replace('/login')
      }
    }
    const silentOnCustomerPage = isCustomerRoute() && AUTH_FAILURE_CODES.has(data.code || '')
    if (!response.config.skipErrorToast && !silentOnCustomerPage) {
      message.error(errMsg)
    }
    // 拒单原因码要能被调用方拿到并展示（任意点下单的三条受理判据靠 code 区分），
    // 只抛 Error 会把 code 丢掉，所以挂在 Error 上而不是换掉 reject 类型。
    const apiError = new Error(errMsg) as Error & { code?: string; rawMessage?: string }
    apiError.code = data.code
    apiError.rawMessage = data.message
    return Promise.reject(apiError)
  },
  (error) => {
    const skipToast = error.config?.skipErrorToast
    if (error.response) {
      const { status, data } = error.response
      const httpFriendlyMsg = friendlyHttpErrorMessage(status, data)
      // 401/403 在顾客端同样是"你没有登录这回事"，不该弹红条；管理端保持原样。
      const authHttpOnCustomerPage = isCustomerRoute() && (status === 401 || status === 403)
      if (!skipToast && !authHttpOnCustomerPage) {
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
      } else if (!pinia) {
        console.warn('[request] pinia 未初始化，跳过 API 错误记录')
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
