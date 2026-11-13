
import { apiRequest } from './api'

// 영상 생성 요청
export function createVideo(sourceUrl) {
  return apiRequest('/api/videos', {
    method: 'POST',
    body: JSON.stringify({
      sourceUrl,
    }),
  })
}

// 영상 생성 상태 조회
export function getVideoStatus(jobId) {
  return apiRequest(`/api/videos/${jobId}`, {
    method: 'GET',
  })
}
