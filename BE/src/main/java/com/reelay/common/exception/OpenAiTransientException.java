package com.reelay.common.exception;

// OpenAI 429·5xx·네트워크 오류. @Retryable 재시도 대상
// 재시도를 다 쓰면 그대로 올라가 errorCode(SCRIPT_FAILED / TTS_FAILED)로 기록된다
public class OpenAiTransientException extends BusinessException {

    public OpenAiTransientException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
