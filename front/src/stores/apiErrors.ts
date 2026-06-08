import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface ApiErrorRecord {
  id: number
  code: string
  message: string
  rawMessage: string
  url: string
  method: string
  status: number
  timestamp: string
}

const MAX_RECORDS = 50

export const useApiErrorsStore = defineStore('apiErrors', () => {
  const records = ref<ApiErrorRecord[]>([])
  let nextId = 1

  function push(record: Omit<ApiErrorRecord, 'id' | 'timestamp'>) {
    records.value.unshift({ ...record, id: nextId++, timestamp: new Date().toISOString() })
    if (records.value.length > MAX_RECORDS) {
      records.value = records.value.slice(0, MAX_RECORDS)
    }
  }

  function clear() {
    records.value = []
  }

  function remove(id: number) {
    records.value = records.value.filter((r) => r.id !== id)
  }

  return { records, push, clear, remove }
})