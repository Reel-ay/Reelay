import './ExampleVideo.css'

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
            src="./public/포스코합격률 포스코 필기시험 임박! 포스코 필기 인강으로 단기 완벽 대비 (1).mp4"
            type="video/mp4"
          />
          브라우저가 동영상 재생을 지원하지 않습니다.
        </video>
      </div>
    </aside>
  )
}

export default ExampleVideo
//초기화면 예시 영상