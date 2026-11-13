import './ResultModal.css'

function ResultModal({ videoUrl, videoTitle, onClose, onRetry }) {
  return (
    <div className="modal-overlay">
      <div className="modal result-modal">

        <div className="modal-header">
          <h2>쇼츠 영상 생성 완료!</h2>
          <p>
            블로그 콘텐츠가 숏폼 영상으로 완성되었습니다.
          </p>
        </div>

        <div className="result-video">
          <video key={videoUrl} controls playsInline preload="metadata">
            <source src={videoUrl} type="video/mp4" />
            브라우저가 동영상 재생을 지원하지 않습니다.
          </video>
        </div>

        <h3 className="result-title">
          {videoTitle}
        </h3>

        <div className="result-actions">
          <a
            href={videoUrl}
            download
            className="download-button"
            target="_blank"
            rel="noreferrer"
          >
            영상 다운로드
          </a>

          <button
            type="button"
            className="retry-button"
            onClick={onRetry}
          >
            새 쇼츠 만들기
          </button>
        </div>

        <button
          type="button"
          className="close-button"
          onClick={onClose}
        >
          닫기
        </button>

      </div>
    </div>
  )
}

export default ResultModal

//생성결과 모달