export interface ApiResponse<T> {
  success: boolean
  code: string
  message: string
  data: T
}

export interface PageResponse<T> {
  total: number
  pageNo: number
  pageSize: number
  records: T[]
}
