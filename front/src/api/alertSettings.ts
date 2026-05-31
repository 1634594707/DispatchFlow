import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'

export function fetchAlertSettings() {
  return request.get<any, ApiResponse<{ rulesJson: string }>>('/admin/alert-settings')
}

export function saveAlertSettings(rulesJson: string) {
  return request.put<any, ApiResponse<{ rulesJson: string }>>('/admin/alert-settings', { rulesJson })
}
