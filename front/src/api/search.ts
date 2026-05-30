import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'
import type { GlobalSearchResponse } from '@/types/phase10'

export function globalSearch(keyword: string, limit = 20) {
  return request.get<any, ApiResponse<GlobalSearchResponse>>('/admin/search', {
    params: { keyword, limit },
  })
}
