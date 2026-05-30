import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'
import type { AssistantResponse } from '@/types/phase10'

export function interpretAssistant(instruction: string, parkId?: number | null) {
  return request.post<any, ApiResponse<AssistantResponse>>('/admin/assistant/interpret', {
    instruction,
    parkId: parkId ?? undefined,
  })
}
