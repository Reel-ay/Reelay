
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

// 신규 블로그 본문 영상 생성 요청
export function createManualVideo({
  body,
  titleMode,
  title,
  titleHook,
  images = [],
}) {
  const formData = new FormData()

  formData.append('body', body)
  formData.append('titleMode', titleMode)

  if (titleMode === 'MANUAL') {
    formData.append('title', title)

    if (titleHook) {
      formData.append('titleHook', titleHook)
    }
  }

  images.forEach((image) => {
    formData.append('images', image)
  })

  return apiRequest('/api/videos/manual', {
    method: 'POST',
    body: formData,
  })
}