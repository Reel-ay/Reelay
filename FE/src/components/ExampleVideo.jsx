function ExampleVideo() {
  return (
    <aside className="aside">
      <h2 className="aside-title">
        예시 쇼츠 영상
      </h2>

      <div className="phone">
        <video
          className="example-video"
          controls
          playsInline
          preload="metadata"
        >
          <source
            src="/example.mp4"
            type="video/mp4"
          />
          브라우저가 동영상 재생을 지원하지 않습니다.
        </video>
      </div>

      <p className="aside-note">
        미리 테스트하면서 만들어놓은 쇼츠넣기.
        <br />
        ...
      </p>
    </aside>
  )
}

export default ExampleVideo
//초기화면 예시 영상