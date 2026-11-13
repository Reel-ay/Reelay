const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'

// 공통 API 요청 함수
export async function apiRequest(path, options = {}) {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...options.headers,
    },
  })

  const data = await response.json().catch(() => null)

  if (!response.ok) {
    throw new Error(
      data?.message || `서버 요청 실패 (${response.status})`
    )
  }

  return data
}

// 상대 경로로 전달된 영상 URL을 절대 경로로 변환
export function resolveMediaUrl(path) {
  if (!path) return ''

  return new URL(path, API_BASE_URL).toString()
}
