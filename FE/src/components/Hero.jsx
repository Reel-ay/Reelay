import './Hero.css'

function Hero() {
  return (
    <section className="hero">
      <span className="eyebrow">
        <i className="dot"></i>
        블로그 주소 하나면 됩니다
      </span>

      <h1>
        긴 콘텐츠를 <em>짧고 선명하게</em>
      </h1>

      <p className="lede">
        블로그에 있던 이미지를 그대로 활용해
        대본과 음성을 만들고 9:16 영상으로 완성합니다.
      </p>
    </section>
  )
}

export default Hero

//화면 메인 소개