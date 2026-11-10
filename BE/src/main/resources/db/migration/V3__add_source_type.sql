-- 본문 직접 입력(POST /api/videos/manual) 지원
--   source_type: BLOG(블로그 URL) | MANUAL(본문·사진 직접 입력). 기존 row 는 BLOG
--   source_url : 직접 입력은 URL 이 없어서 NULL 허용으로 바꾼다
ALTER TABLE video_job
    ADD COLUMN source_type VARCHAR(20) NOT NULL DEFAULT 'BLOG' AFTER id,
    MODIFY COLUMN source_url VARCHAR(1024) NULL;
