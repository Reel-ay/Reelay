import './CreatePage.css'
import { useEffect, useState } from 'react'
import Hero from '../components/Hero'
import BlogInput from '../components/BlogInput'
import ExampleVideo from '../components/ExampleVideo'
import ProcessFlow from '../components/ProcessFlow'
import GenerationModal from '../components/GenerationModal'
import ResultModal from '../components/ResultModal'
<<<<<<< HEAD
import {createVideo,createManualVideo,getVideoStatus} from '../api/videoApi'
=======
import {createVideo,getVideoStatus} from '../api/videoApi'
>>>>>>> origin/develop
import { resolveMediaUrl } from '../api/api'


function CreatePage() {
  // 입력 방식
  const [mode, setMode] = useState('url')

  // 입력 데이터
  const [url, setUrl] = useState('')
  const [text, setText] = useState('')

  // 화면 상태
  const [status, setStatus] = useState('empty')

<<<<<<< HEAD
=======
  // 영상 제작 진행 단계
  const [, setCurrentStep] = useState(0)

>>>>>>> origin/develop
  // 생성된 영상 정보
  const [videoUrl, setVideoUrl] = useState('')
  //결과 모달에 표시할 블로그 제목
  const [videoTitle, setVideoTitle] = useState('')
  // 영상 내부에 표시되는 제목 정보
  const [videoOverlayTitle, setVideoOverlayTitle] = useState(null)

  // 백엔드 작업 ID
  const [jobId, setJobId] = useState(null)

// 백엔드에서 전달받는 6단계 진행 정보
  const [steps, setSteps] = useState([])

  // 영상 생성 오류 메시지
  const [generationError, setGenerationError] = useState('')
  
  // 본문 직접 입력 시 제목 설정
  const [titleMode, setTitleMode] = useState('AUTO')
  const [manualTitle, setManualTitle] = useState('')
  const [titleHook, setTitleHook] = useState('')

  // 업로드 이미지 파일
  const [images, setImages] = useState([])

  const [videoDownloadUrl, setVideoDownloadUrl] = useState('')

  // 백엔드 작업 ID
  const [jobId, setJobId] = useState(null)

// 백엔드에서 전달받는 6단계 진행 정보
  const [steps, setSteps] = useState([])

  // 영상 생성 오류 메시지
  const [generationError, setGenerationError] = useState('')
  
  // 영상 생성 요청
<<<<<<< HEAD
  async function handleSubmit(e) {
      e.preventDefault()
=======
  
  async function handleSubmit(e) {
    e.preventDefault()
>>>>>>> origin/develop

      const value = mode === 'url' ? url.trim() : text.trim()

      // 공통 입력값 검사
      if (!value) {
        alert('블로그 주소 또는 본문을 입력해 주세요.')
        return
      }

<<<<<<< HEAD
      // URL 입력 방식
      if (mode === 'url') {
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
      }

      // 본문 입력 방식
      if (mode === 'text') {
        const bodyLength = value.replace(/\s/g, '').length

        if (bodyLength < 100) {
          alert('블로그 본문은 공백 제외 100자 이상 입력해 주세요.')
          return
        }

        if (bodyLength > 20000) {
          alert('블로그 본문은 공백 제외 20,000자 이하로 입력해 주세요.')
          return
        }

        if (titleMode === 'MANUAL' && !manualTitle.trim()) {
          alert('영상 제목을 입력해 주세요.')
          return
        }

        if (titleMode === 'MANUAL' && manualTitle.trim().length > 20) {
          alert('영상 제목은 20자 이하로 입력해 주세요.')
          return
        }
      }

      // 기존 생성 정보 초기화
      setSteps([])
      setJobId(null)
      setVideoUrl('')
      setVideoDownloadUrl('')
      setVideoTitle('')
      setVideoOverlayTitle(null)
      setGenerationError('')
      setStatus('loading')

      try {
        let data

        if (mode === 'url') {
          // 기존 URL 방식 API
          data = await createVideo(value)
        } else {
          // 신규 본문 입력 방식 API
          data = await createManualVideo({
            body: value,
            titleMode,
            title: manualTitle.trim(),
            titleHook: titleHook.trim(),
            images,
          })
        }

        if (!data?.jobId) {
          throw new Error('영상 작업 ID를 전달받지 못했습니다.')
        }

        setJobId(data.jobId)
      } catch (error) {
        alert(error.message || '영상 생성 요청에 실패했습니다.')
        setStatus('empty')
=======
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
>>>>>>> origin/develop
      }
    } catch {
      alert('올바른 블로그 주소를 입력해 주세요.')
      return
    }

<<<<<<< HEAD

=======
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


>>>>>>> origin/develop
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

<<<<<<< HEAD
=======
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

>>>>>>> origin/develop
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
<<<<<<< HEAD

          // 백엔드 다운로드 전용 주소
          setVideoDownloadUrl(
            resolveMediaUrl(`/api/videos/${jobId}/file?download=true`)
          )

          setVideoTitle(data.title || '블로그 홍보 쇼츠')
          setVideoOverlayTitle(data.videoTitle || null)
=======
          setVideoTitle(data.title || '블로그 홍보 쇼츠')
>>>>>>> origin/develop
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
<<<<<<< HEAD
    setSteps([])
    setJobId(null)
    setVideoUrl('')
    setVideoDownloadUrl('')
    setVideoTitle('')
    setVideoOverlayTitle(null)
=======
    setCurrentStep(0)
    setSteps([])
    setJobId(null)
    setVideoUrl('')
    setVideoTitle('')
>>>>>>> origin/develop
    setGenerationError('')
  }

  // 새 쇼츠 만들기
  function handleRetry() {
    setUrl('')
    setText('')
    setMode('url')
<<<<<<< HEAD
    //본문 입력 옵션 초기화
    setTitleMode('AUTO')
    setManualTitle('')
    setTitleHook('')
    setImages([])
    //영상 생성 정보 초기화
    setSteps([])
    setJobId(null)
    setVideoUrl('')
    setVideoDownloadUrl('')
    setVideoTitle('')
    setStatus('empty')
    setVideoOverlayTitle(null)
=======
    setCurrentStep(0)
    setSteps([])
    setJobId(null)
    setVideoUrl('')
    setVideoTitle('')
    setStatus('empty')
>>>>>>> origin/develop
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
                titleMode={titleMode}
                setTitleMode={setTitleMode}
                manualTitle={manualTitle}
                setManualTitle={setManualTitle}
                titleHook={titleHook}
                setTitleHook={setTitleHook}
                images={images}
                setImages={setImages}
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
          videoDownloadUrl={videoDownloadUrl}
          videoTitle={videoTitle}
          videoOverlayTitle={videoOverlayTitle}
          onClose={handleClose}
          onRetry={handleRetry}
        />
      )}
    </>
  )
}

export default CreatePage

//메인페이지