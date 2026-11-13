import './CreatePage.css'
import { useEffect, useState } from 'react'
import Hero from '../components/Hero'
import BlogInput from '../components/BlogInput'
import ExampleVideo from '../components/ExampleVideo'
import ProcessFlow from '../components/ProcessFlow'
import GenerationModal from '../components/GenerationModal'
import ResultModal from '../components/ResultModal'
import {createVideo,getVideoStatus} from '../api/videoApi'
import { resolveMediaUrl } from '../api/api'


function CreatePage() {
  // 입력 방식
  const [mode, setMode] = useState('url')

  // 입력 데이터
  const [url, setUrl] = useState('')
  const [text, setText] = useState('')

  // 화면 상태
  const [status, setStatus] = useState('empty')

  // 영상 제작 진행 단계
  const [, setCurrentStep] = useState(0)

  // 생성된 영상 정보
  const [videoUrl, setVideoUrl] = useState('')
  const [videoTitle, setVideoTitle] = useState('')

  // 백엔드 작업 ID
  const [jobId, setJobId] = useState(null)

// 백엔드에서 전달받는 6단계 진행 정보
  const [steps, setSteps] = useState([])

  // 영상 생성 오류 메시지
  const [generationError, setGenerationError] = useState('')
  
  // 영상 생성 요청
  
  async function handleSubmit(e) {
    e.preventDefault()

    const value = mode === 'url' ? url.trim() : text.trim()

    if (!value) {
      alert('블로그 주소 또는 본문을 입력해 주세요.')
      return
    }

    // 현재 백엔드 API는 sourceUrl 방식으로 협의됨
    if (mode !== 'url') {
      alert('현재는 블로그 URL 입력 방식만 지원합니다.')
      return
    }

    try {
      const parsedUrl = new URL(value)

      if (!['http:', 'https:'].includes(parsedUrl.protocol)) {
        alert('올바른 블로그 주소를 입력해 주세요.')
        return
      }
    } catch {
      alert('올바른 블로그 주소를 입력해 주세요.')
      return
    }

    setCurrentStep(0)
    setSteps([])
    setJobId(null)
    setVideoUrl('')
    setVideoTitle('')
    setStatus('loading')

    try {
      // 백엔드에 영상 생성 요청
      const data = await createVideo(value)

      if (!data?.jobId) {
        throw new Error('영상 작업 ID를 전달받지 못했습니다.')
      }

      // 서버에서 받은 작업 ID 저장
      setJobId(data.jobId)

    } catch (error) {
      alert(error.message || '영상 생성 요청에 실패했습니다.')
      setStatus('empty')
    }
  }


  useEffect(() => {
    if (status !== 'loading' || !jobId) return

    let cancelled = false
    let failureCount = 0
    let timer

    async function pollStatus() {
      try {
        // 백엔드 작업 상태 조회
        const data = await getVideoStatus(jobId)

        if (cancelled) return

        failureCount = 0

        // 백엔드에서 전달받은 단계 정보 저장
        const receivedSteps = data.steps || []
        setSteps(receivedSteps)

        // 현재 실행 중인 단계 인덱스 계산
        const runningIndex = receivedSteps.findIndex(
          (step) => step.status === 'RUNNING'
        )

        const doneCount = receivedSteps.filter(
          (step) => step.status === 'DONE'
        ).length

        setCurrentStep(
          runningIndex >= 0
            ? runningIndex
            : Math.max(doneCount - 1, 0)
        )

        // 영상 생성 실패
        if (data.status === 'FAILED') {
          const errorCode = data.error?.code
          const errorMessage = data.error?.message

          setGenerationError(
            errorMessage ||
            (errorCode === 'CAPTION_FAILED'
              ? '캡션 생성에 실패했습니다.'
              : '영상 생성에 실패했습니다.')
          )

          setSteps(receivedSteps)
          setStatus('failed')
          return
        }


        // 영상 생성 완료
        // 실제 백엔드의 성공 상태값은 응답 샘플에 맞춰 확인
        if (data.status === 'DONE') {
          if (!data.videoUrl) {
            throw new Error('완성된 영상 주소가 없습니다.')
          }

          setVideoUrl(resolveMediaUrl(data.videoUrl))
          setVideoTitle(data.title || '블로그 홍보 쇼츠')
          setStatus('completed')
          return
        }

      } catch (error) {
        if (cancelled) return

        failureCount += 1

        // 연속 3회 조회 실패 시 중단
        if (failureCount >= 3) {
          alert(
            error.message || '서버와 통신할 수 없습니다.'
          )
          setStatus('empty')
          return
        }
      }

      // 2초 후 다시 상태 조회
      if (!cancelled) {
        timer = setTimeout(pollStatus, 2000)
      }
    }

    // 첫 상태 조회
    pollStatus()

    return () => {
      cancelled = true
      clearTimeout(timer)
    }
  }, [status, jobId])

  

  // 결과 모달 닫기
  function handleClose() {
    setStatus('empty')
    setCurrentStep(0)
    setSteps([])
    setJobId(null)
    setVideoUrl('')
    setVideoTitle('')
    setGenerationError('')
  }

  // 새 쇼츠 만들기
  function handleRetry() {
    setUrl('')
    setText('')
    setMode('url')
    setCurrentStep(0)
    setSteps([])
    setJobId(null)
    setVideoUrl('')
    setVideoTitle('')
    setStatus('empty')
    setGenerationError('')
  }

  return (
    <>
      <main>
        <div className="wrap">
          <Hero />

          <div className="grid">
            <section>
              <BlogInput
                mode={mode}
                setMode={setMode}
                url={url}
                setUrl={setUrl}
                text={text}
                setText={setText}
                status={status}
                onSubmit={handleSubmit}
              />

            </section>

            <ExampleVideo />
          </div>
        </div>

        <ProcessFlow />
      </main>

      {/* 영상 생성 진행 모달 */}
      {(status === 'loading' || status === 'failed') && (
        <GenerationModal
          steps={steps}
          errorMessage={generationError}
          onClose={handleClose}
        />
      )}

      {/* 영상 생성 결과 모달 */}
      {status === 'completed' && (
        <ResultModal
          videoUrl={videoUrl}
          videoTitle={videoTitle}
          onClose={handleClose}
          onRetry={handleRetry}
        />
      )}
    </>
  )
}

export default CreatePage

//메인페이지