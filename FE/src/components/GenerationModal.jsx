function GenerationModal({ currentStep }) {
  const steps = [
    '블로그 글 읽는 중',
    '숏폼 대본 만드는 중',
    '이미지 다듬는 중',
    '음성 만드는 중',
    '자막 맞추는 중',
    '영상 합성하는 중'
  ]

  return (
    <div className="modal-overlay">
      <div className="modal">
        <div className="modal-header">
          <h2>쇼츠 영상 생성 중</h2>
          <p>
            블로그 내용을 분석하고 영상을 제작하고 있습니다.
          </p>
        </div>

        {/* 영상 생성 이미지 */}
        <div className="generating-visual">
          <img
            src="/background.jpeg"
            alt="쇼츠영상 생성 중"
          />
        </div>

        <div className="modal-steps">
          {steps.map((step, index) => (
            <div className="modal-step" key={step}>
              <div className="step-info">
                <span>{step}</span>

                <span>
                  {index < currentStep
                    ? '완료'
                    : index === currentStep
                      ? '진행 중'
                      : '대기 중'}
                </span>
              </div>

              <div className="step-bar">
                <div
                  className={`step-progress ${
                    index < currentStep
                      ? 'completed'
                      : index === currentStep
                        ? 'active'
                        : ''
                  }`}
                />
              </div>
            </div>
          ))}
        </div>

        <p className="modal-notice">
          영상 생성이 완료될 때까지 잠시 기다려 주세요.
        </p>
      </div>
    </div>
  )
}

export default GenerationModal
//생성 진행 모달