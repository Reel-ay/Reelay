import './Header.css'

function Header() {
  return (
    <header className="site-head">
      <div className="head-in">
        <a className="brand" href="#">
          <span className="mark" aria-hidden="true">
            <svg width="14" height="14" viewBox="0 0 16 16" fill="none">
              <rect
                x="2.5"
                y="1"
                width="11"
                height="14"
                rx="2.5"
                stroke="#fff"
                strokeWidth="1.6"
              />
              <path d="M6.7 6.1v3.8l3.1-1.9z" fill="#fff" />
            </svg>
          </span>

          <span className="brand-name">Reelay</span>
        </a>

        <span className="head-note">
          (주)스마트동스쿨 · 숏폼 영상 제작 서비스
        </span>
      </div>
    </header>
  )
}

export default Header

//상단 헤더