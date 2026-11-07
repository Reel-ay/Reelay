function BlogInput({
  mode,
  setMode,
  url,
  setUrl,
  text,
  setText,
  status,
  onSubmit,
}) {
  const isUrl = mode === 'url'
  const isLoading = status === 'loading'

  function handleModeChange() {
    setMode(isUrl ? 'text' : 'url')
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
              src="/apple.jpg"
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
              src="/apple_5.jpg"
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
        <textarea
          className="textarea"
          id="text"
          placeholder="블로그 본문을 붙여넣어 주세요."
          value={text}
          onChange={(e) => setText(e.target.value)}
        />
      )}

      <div className="under">
        <span>
          {isUrl
            ? '글 본문이 공개된 주소면 됩니다.'
            : '블로그 본문을 직접 입력할 수 있습니다.'}
        </span>

        {!isUrl && (
          <span className="counter">
            {text.trim().length.toLocaleString('ko-KR')}자
          </span>
        )}

        <button
          type="button"
          className="swap"
          onClick={handleModeChange}
          disabled={isLoading}
        >
          {isUrl ? '본문 직접 붙여넣기' : '주소로 입력하기'}
        </button>
      </div>

      <button
        className="submit"
        type="submit"
        disabled={isLoading}
      >
        {isLoading ? '만드는 중…' : '영상 만들기'}
      </button>

      <p className="sub-note">
        블로그 분석부터 음성 생성과 영상 재생까지 자동으로 진행합니다.
      </p>
    </form>
  )
}

export default BlogInput
//블로그URL입력