-- 영상 상단 두 줄 제목 (SCRIPT 단계 LLM 생성, 줄마다 공백 포함 8자 목표)
--   video_title      첫 줄: 블로그 내용의 핵심 키워드
--   video_title_hook 둘째 줄: 유입을 끄는 강조 문구 (강조색)
-- 기존 title(블로그 원문)은 FE 표시와 다운로드 파일명이 쓰므로 그대로 두고 컬럼을 따로 둔다
-- 50 은 8자 제약에 여유를 둔 값. 넘으면 코드에서 자른다
-- 기존 row 는 NULL -> 자막은 원문 title 한 줄로 폴백
ALTER TABLE video_job
    ADD COLUMN video_title      VARCHAR(50) AFTER title,
    ADD COLUMN video_title_hook VARCHAR(50) AFTER video_title;
