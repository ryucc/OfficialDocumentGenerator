const rawApiBaseUrl = import.meta.env.VITE_API_BASE_URL?.trim() ?? ''

export const API_BASE_URL = rawApiBaseUrl.replace(/\/+$/, '')
export const API_BASE_URL_ERROR = API_BASE_URL
  ? null
  : '缺少 VITE_API_BASE_URL，請設定前端要連線的後端 API 位址。'
