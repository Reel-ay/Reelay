const processes = [
  {
    number: '01',
    title: '핵심 내용을 담아요',
    description:
      '블로그 본문을 분석하고 핵심 내용을 30~60초 분량의 숏폼 대본으로 만듭니다.',
  },
  {
    number: '02',
    title: '이미지를 연결해요',
    description:
      '블로그에 포함된 기존 이미지를 대본의 흐름에 맞게 자동으로 배치합니다.',
  },
  {
    number: '03',
    title: '목소리를 입혀요',
    description:
      '생성된 대본을 바탕으로 영상에 사용할 음성을 만듭니다.',
  },
  {
    number: '04',
    title: '영상으로 완성해요',
    description:
      '이미지와 음성을 합성해 최종 9:16 숏폼 영상을 완성합니다.',
  },
]

function ProcessFlow() {
  return (
    <section className="flow">
      <div className="wrap">
        <h2>영상은 이렇게 완성돼요.</h2>

        <div className="flow-grid">
          {processes.map((process) => (
            <div className="flow-item" key={process.number}>
              <div className="n">
                {process.number}
              </div>

              <h3>{process.title}</h3>

              <p>{process.description}</p>
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}

export default ProcessFlow
//페이지 아래부분 제작과정 흐름도