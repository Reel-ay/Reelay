import { useEffect, useState } from 'react'

import Hero from '../components/Hero'
import BlogInput from '../components/BlogInput'
import ExampleVideo from '../components/ExampleVideo'
import ProcessFlow from '../components/ProcessFlow'
import GenerationModal from '../components/GenerationModal'
import ResultModal from '../components/ResultModal'

function CreatePage() {
  // 입력 방식
  const [mode, setMode] = useState('url')

  // 입력 데이터
  const [url, setUrl] = useState('')
  const [text, setText] = useState('')

  // 화면 상태
  const [status, setStatus] = useState('empty')

  // 영상 제작 진행 단계
  const [currentStep, setCurrentStep] = useState(0)

  // 생성된 영상 정보
  const [videoUrl, setVideoUrl] = useState('')
  const [videoTitle, setVideoTitle] = useState('')

  // 영상 생성 요청
  function handleSubmit(e) {
    e.preventDefault()

    const value = mode === 'url' ? url.trim() : text.trim()

    if (!value) {
      alert('블로그 주소 또는 본문을 입력해 주세요.')
      return
    }

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

    setCurrentStep(0)
    setStatus('loading')
  }

  // 로딩 화면 진행 시뮬레이션
  // 실제 백엔드 연결 후에는 서버 진행 상태로 대체
  useEffect(() => {
    if (status !== 'loading') return

    const stepTimer = setInterval(() => {
      setCurrentStep((prev) => Math.min(prev + 1, 3))
    }, 1200)

    const finishTimer = setTimeout(() => {
      // 임시 테스트 데이터
      setVideoUrl('/example.mp4')
      setVideoTitle('블로그 홍보 쇼츠')
      setStatus('completed')
    }, 4800)

    return () => {
      clearInterval(stepTimer)
      clearTimeout(finishTimer)
    }
  }, [status])

  // 결과 모달 닫기
  function handleClose() {
    setStatus('empty')
    setCurrentStep(0)
  }

  // 새 쇼츠 만들기
  function handleRetry() {
    setUrl('')
    setText('')
    setMode('url')
    setCurrentStep(0)
    setStatus('empty')
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
      {status === 'loading' && (
        <GenerationModal currentStep={currentStep} />
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