package com.reelay.common.exception;

import org.springframework.http.HttpStatus;

// message 는 사용자에게 그대로 노출된다
public enum ErrorCode {

    INVALID_URL(HttpStatus.BAD_REQUEST, "네이버 블로그 글 URL이 아닙니다."),
    SOURCE_URL_TOO_LONG(HttpStatus.BAD_REQUEST, "글 주소가 너무 깁니다."),

    // 본문 직접 입력 (POST /api/videos/manual)
    BODY_REQUIRED(HttpStatus.BAD_REQUEST, "본문을 입력해 주세요."),
    BODY_TOO_SHORT(HttpStatus.BAD_REQUEST, "본문을 100자 이상 입력해 주세요."),
    BODY_TOO_LONG(HttpStatus.BAD_REQUEST, "본문은 20,000자까지 입력할 수 있습니다."),
    INVALID_TITLE_MODE(HttpStatus.BAD_REQUEST, "제목 방식은 직접 작성(MANUAL) 또는 자동 생성(AUTO) 중 하나를 골라 주세요."),
    TITLE_REQUIRED(HttpStatus.BAD_REQUEST, "제목을 입력해 주세요."),
    TITLE_TOO_LONG(HttpStatus.BAD_REQUEST, "제목은 한 줄에 20자까지 입력할 수 있습니다."),
    TOO_MANY_IMAGES(HttpStatus.BAD_REQUEST, "사진은 10장까지 올릴 수 있습니다."),
    INVALID_IMAGE(HttpStatus.BAD_REQUEST, "JPG 또는 PNG 사진만 올릴 수 있습니다."),
    IMAGE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "사진은 한 장에 10MB, 모두 합쳐 50MB까지 올릴 수 있습니다."),

    JOB_NOT_FOUND(HttpStatus.NOT_FOUND, "요청을 찾을 수 없습니다."),
    JOB_NOT_READY(HttpStatus.CONFLICT, "영상이 아직 준비되지 않았습니다."),
    SCENE_NOT_FOUND(HttpStatus.NOT_FOUND, "컷을 찾을 수 없습니다."),
    SCENE_HAS_NO_IMAGE(HttpStatus.NOT_FOUND, "이 컷에는 이미지가 없습니다."),
    FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "파일을 찾을 수 없습니다."),

    // 파이프라인 실패. video_job.error_code 로 기록된다
    CRAWL_EMPTY_BODY(HttpStatus.BAD_REQUEST, "글 본문을 읽지 못했습니다. 비공개 또는 삭제된 글일 수 있습니다."),
    CRAWL_FAILED(HttpStatus.BAD_GATEWAY, "블로그에 접속하지 못했습니다. 잠시 후 다시 시도해 주세요."),
    SCRIPT_FAILED(HttpStatus.BAD_GATEWAY, "대본을 만들지 못했습니다. 잠시 후 다시 시도해 주세요."),
    TTS_FAILED(HttpStatus.BAD_GATEWAY, "음성을 만들지 못했습니다. 잠시 후 다시 시도해 주세요."),
    RENDER_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "영상 합성에 실패했습니다."),
    SERVER_RESTARTED(HttpStatus.INTERNAL_SERVER_ERROR, "서버가 재시작되어 작업이 중단되었습니다. 다시 시도해 주세요."),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "요청을 처리하지 못했습니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
