import './BlogInput.css'
import { useEffect, useState } from 'react'

function BlogInput({
  mode,
  setMode,
  url,
  setUrl,
  text,
  setText,
  status,
  onSubmit,
  titleMode,
  setTitleMode,
  manualTitle,
  setManualTitle,
  titleHook,
  setTitleHook,
  images,
  setImages,
}) {
  const isUrl = mode === 'url'
  const isLoading = status === 'loading'

  const [imageError, setImageError] = useState('')
  const [imagePreviews, setImagePreviews] = useState([])

  // 이미지 미리보기 URL 생성 및 정리
  useEffect(() => {
    const previews = images.map((file) => ({
      file,
      url: URL.createObjectURL(file),
    }))

    setImagePreviews(previews)

    return () => {
      previews.forEach((preview) => URL.revokeObjectURL(preview.url))
    }
  }, [images])

  function handleModeChange() {
    setMode(isUrl ? 'text' : 'url')
    setImageError('')
  }

   function handleImageChange(e) {
    const selectedFiles = Array.from(e.target.files || [])

    // 같은 파일을 다시 선택할 수 있도록 초기화
    e.target.value = ''

    if (selectedFiles.length === 0) return

    const nextImages = [...images, ...selectedFiles]

    if (nextImages.length > 10) {
      setImageError('이미지는 최대 10장까지 업로드할 수 있습니다.')
      return
    }

    const invalidFile = selectedFiles.find(
      (file) => !['image/jpeg', 'image/png'].includes(file.type)
    )

    if (invalidFile) {
      setImageError('JPG 또는 PNG 이미지만 업로드할 수 있습니다.')
      return
    }

    const oversizedFile = selectedFiles.find(
      (file) => file.size > 10 * 1024 * 1024
    )

    if (oversizedFile) {
      setImageError('이미지 한 장의 용량은 10MB를 초과할 수 없습니다.')
      return
    }

    const totalSize = nextImages.reduce(
      (total, file) => total + file.size,
      0
    )

    if (totalSize > 50 * 1024 * 1024) {
      setImageError('이미지 전체 용량은 50MB를 초과할 수 없습니다.')
      return
    }

    setImages(nextImages)
    setImageError('')
  }

  // 업로드 이미지 삭제
  function handleImageRemove(index) {
    setImages(images.filter((_, i) => i !== index))
    setImageError('')
  }

  return (
    <form className="card" onSubmit={onSubmit}>
      <div className="conversion-preview">
        <div className="conversion-heading">
          <strong>블로그 이미지가 쇼츠 영상으로!</strong>
          <span>
            AI가 블로그 속 이미지를 활용해 영상을 제작합니다.
          </span>
        </div>

        <div className="conversion-flow">
          <div className="conversion-item">
            <img
              src="/before.png"
              alt="블로그 이미지 예시"
            />
            <span>블로그 이미지</span>
          </div>

          <div className="conversion-arrow">
            <span>→</span>
            <small>쇼츠 변환</small>
          </div>

          <div className="conversion-item">
            <img
              src="/after.png"
              alt="쇼츠 영상 예시"
            />
            <span>쇼츠 영상</span>
          </div>
        </div>
      </div>

      <div className="conversion-divider" />

      <label
        className="field-label"
        htmlFor={isUrl ? 'url' : 'text'}
      >
        {isUrl ? '블로그 주소' : '블로그 본문'}
      </label>

      {isUrl ? (
        <input
          className="input"
          id="url"
          type="url"
          inputMode="url"
          autoComplete="off"
          placeholder="https://blog.example.com/posts/123"
          value={url}
          onChange={(e) => setUrl(e.target.value)}
        />
      ) : (
         <>
          <textarea
            className="textarea"
            id="text"
            placeholder="블로그 본문을 붙여넣어 주세요."
            value={text}
            onChange={(e) => setText(e.target.value)}
          />

          <div className="under">
            <span>본문은 공백 제외 100자 이상 입력해 주세요.</span>
            <span className="counter">
              {text.trim().length.toLocaleString('ko-KR')}자
            </span>
          </div>

          {/* 제목 설정 */}
          <div className="manual-options">
            <label className="field-label">영상 제목 설정</label>

            <div className="title-mode-options">
              <label>
                <input
                  type="radio"
                  name="titleMode"
                  value="AUTO"
                  checked={titleMode === 'AUTO'}
                  onChange={() => setTitleMode('AUTO')}
                />
                AI 자동 생성
              </label>

              <label>
                <input
                  type="radio"
                  name="titleMode"
                  value="MANUAL"
                  checked={titleMode === 'MANUAL'}
                  onChange={() => setTitleMode('MANUAL')}
                />
                직접 입력
              </label>
            </div>

            {titleMode === 'MANUAL' && (
              <div className="manual-title-fields">
                <label className="field-label" htmlFor="manualTitle">
                  영상 제목
                </label>

                <input
                  className="input"
                  id="manualTitle"
                  type="text"
                  maxLength={20}
                  placeholder="영상 제목을 입력해 주세요."
                  value={manualTitle}
                  onChange={(e) => setManualTitle(e.target.value)}
                />

                <div className="under">
                  <span>최대 20자까지 입력할 수 있습니다.</span>
                  <span className="counter">
                    {manualTitle.length}/20
                  </span>
                </div>

                <label className="field-label" htmlFor="titleHook">
                  후킹 문구 (선택)
                </label>

                <input
                  className="input"
                  id="titleHook"
                  type="text"
                  placeholder="예: 비용 총정리"
                  value={titleHook}
                  onChange={(e) => setTitleHook(e.target.value)}
                />
              </div>
            )}
          </div>

          {/* 이미지 업로드 */}
          <div className="image-upload-section">
            <label className="field-label" htmlFor="images">
              블로그 이미지 첨부 (선택)
            </label>

            <p className="upload-description">
              JPG, PNG / 최대 10장 / 한 장당 10MB / 전체 50MB 이하
            </p>

            <input
              id="images"
              type="file"
              accept="image/jpeg,image/png"
              multiple
              onChange={handleImageChange}
              disabled={isLoading}
            />

            {imageError && (
              <p className="image-error">{imageError}</p>
            )}

            {imagePreviews.length > 0 && (
              <div className="image-preview-list">
                {imagePreviews.map((preview, index) => (
                  <div className="image-preview-item" key={`${preview.file.name}-${index}`}>
                    <img src={preview.url} alt={`첨부 이미지 ${index + 1}`} />

                    <button
                      type="button"
                      onClick={() => handleImageRemove(index)}
                      disabled={isLoading}
                      aria-label={`${preview.file.name} 삭제`}
                    >
                      ×
                    </button>

                    <span>{preview.file.name}</span>
                  </div>
                ))}
              </div>
            )}

            <p className="upload-count">
              {images.length}/10장 첨부됨
            </p>
          </div>
        </>
      )}

      <div className="under">
        {isUrl && <span>글 본문이 공개된 주소면 됩니다.</span>}

        <button
          type="button"
          className="swap"
          onClick={handleModeChange}
          disabled={isLoading}
        >
          {isUrl ? '본문 직접 붙여넣기' : '주소로 입력하기'}
        </button>
      </div>

      <button className="submit" type="submit" disabled={isLoading}>
        {isLoading ? '만드는 중…' : '영상 만들기'}
      </button>

      <p className="sub-note">
        블로그 분석부터 음성 생성과 영상 재생까지 자동으로 진행합니다.
      </p>
    </form>
  )
}

export default BlogInput