import './GenerationModal.css'

function GenerationModal({ steps = [], errorMessage, onClose}) {

  // 화면에 표시할 6단계
  const stepList = [
    { id: 'CRAWL', label: '블로그 글 읽는 중' },
    { id: 'SCRIPT', label: '숏폼 대본 만드는 중' },
    { id: 'IMAGE', label: '이미지 다듬는 중' },
    { id: 'TTS', label: '음성 만드는 중' },
    { id: 'CAPTION', label: '자막 맞추는 중' },
    { id: 'RENDER', label: '영상 합성하는 중' },
  ]

  // 백엔드 상태에 따른 화면 표시
  function getStepStatus(stepId) {
    const serverStep = steps.find(
      (step) => step.step === stepId
    )

    return serverStep?.status || 'PENDING'
  }

  // 상태별 화면 문구
  function getStatusText(status) {
    switch (status) {
      case 'DONE':
        return '완료'

      case 'RUNNING':
        return '진행 중'

      case 'FAILED':
        return '실패'

      default:
        return '대기 중'
    }
  }

  return (
    <div className="modal-overlay">
      <div className="modal generation-modal">

        <div className="modal-header">
          <h2>
            {errorMessage ? '영상 생성에 실패했습니다.' : '쇼츠 영상 생성 중'}
          </h2>

          <p>
            {errorMessage
              ? '영상 생성 중 문제가 발생했습니다.'
              : '블로그 내용을 분석하고 영상을 제작하고 있습니다.'}
          </p>
        </div>

        {/* 영상 생성 이미지 */}
        <div className="generating-visual">
          <img
            src="/moving_background.gif"
            alt="쇼츠영상 생성 중"
          />
        </div>

        {/* 영상 생성 진행 단계 */}
        <div className="modal-steps">

          {stepList.map((step) => {
            const status = getStepStatus(step.id)

            return (
              <div
                className="modal-step"
                key={step.id}
              >

                <div className="step-info">
                  <span>{step.label}</span>

                  <span>
                    {getStatusText(status)}
                  </span>
                </div>

                <div className="step-bar">
                  <div
                    className={`step-progress ${
                      status === 'DONE'
                        ? 'completed'
                        : status === 'RUNNING'
                          ? 'active'
                          : status === 'FAILED'
                            ? 'failed'
                            : ''
                    }`}
                  />
                </div>

              </div>
            )
          })}

        </div>
        
        {errorMessage ? (
          <div className="generation-error">
            <p>{errorMessage}</p>

            <button type="button" onClick={onClose}>
              닫기
            </button>
          </div>
        ) : (
          <p className="modal-notice">
            영상 생성이 완료될 때까지 잠시 기다려 주세요.
          </p>
        )}

      </div>
    </div>
  )
}

export default GenerationModal
