-- 제목에 이모지가 들어온다. utf8mb4 필수
-- created_at, updated_at 은 DB 기본값을 쓰지 않고 애플리케이션에서 채운다

CREATE TABLE video_job (
    id            BINARY(16)    NOT NULL,           -- UUID v4
    source_url    VARCHAR(1024) NOT NULL,
    blog_id       VARCHAR(100),
    log_no        VARCHAR(50),
    title         VARCHAR(500),                     -- CRAWL 이후 채워진다
    status        VARCHAR(20)   NOT NULL,           -- PENDING RUNNING DONE FAILED
    current_step  VARCHAR(20),                      -- CRAWL SCRIPT IMAGE TTS CAPTION RENDER
    error_code    VARCHAR(50),
    error_message VARCHAR(1000),                    -- 사용자에게 그대로 노출
    output_path   VARCHAR(500),
    duration_ms   INT,
    user_id       BIGINT        NULL,               -- 회원 기능용. 1차는 항상 NULL
    created_at    DATETIME(3)   NOT NULL,
    updated_at    DATETIME(3)   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_job_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- job 당 6행
CREATE TABLE video_job_step (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    job_id        BINARY(16)  NOT NULL,
    step          VARCHAR(20) NOT NULL,             -- CRAWL SCRIPT IMAGE TTS CAPTION RENDER
    step_order    TINYINT     NOT NULL,             -- 1~6
    status        VARCHAR(20) NOT NULL,             -- PENDING RUNNING DONE FAILED
    attempt       TINYINT     NOT NULL DEFAULT 0,
    started_at    DATETIME(3),
    finished_at   DATETIME(3),
    error_message VARCHAR(1000),
    PRIMARY KEY (id),
    UNIQUE KEY uk_step_job_step (job_id, step),
    CONSTRAINT fk_step_job FOREIGN KEY (job_id)
        REFERENCES video_job (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 컷 = 자막 1줄 = TTS 1문장 = 이미지 1장
CREATE TABLE video_scene (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    job_id             BINARY(16)   NOT NULL,
    cut_order          SMALLINT     NOT NULL,       -- 0부터
    narration          VARCHAR(500) NOT NULL,
    caption            VARCHAR(200) NOT NULL,
    highlight          VARCHAR(50),                 -- caption 안의 강조 단어
    source_image_index SMALLINT,                    -- NULL 이면 타이포그래피 카드
    image_path         VARCHAR(500),
    audio_path         VARCHAR(500),                -- wav
    audio_ms           INT,                         -- ffprobe 실측. 컷 길이이자 자막 구간
    start_ms           INT,
    kenburns           VARCHAR(30),
    PRIMARY KEY (id),
    UNIQUE KEY uk_scene_job_order (job_id, cut_order),
    CONSTRAINT fk_scene_job FOREIGN KEY (job_id)
        REFERENCES video_job (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
