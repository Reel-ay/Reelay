package com.reelay.common.exception;

import com.openai.errors.InternalServerException;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIServiceException;
import com.openai.errors.RateLimitException;

import java.io.IOException;

// OpenAI SDK 예외를 재시도 가능 / 불가로 나눈다
// 재시도: 429, 5xx, 네트워크 오류
// 즉시 실패: 401(키 오류), 400(요청 오류) 등 - 다시 보내도 결과가 같다
public final class OpenAiErrors {

    private OpenAiErrors() {
    }

    public static BusinessException translate(Throwable e, ErrorCode errorCode) {
        if (e instanceof BusinessException be) {
            return be;
        }
        if (isTransient(e)) {
            return new OpenAiTransientException(errorCode, e);
        }
        return new BusinessException(errorCode, e);
    }

    static boolean isTransient(Throwable e) {
        // Spring AI 가 SDK 예외를 감싸는 경우가 있어 원인 체인을 따라간다
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof RateLimitException
                    || t instanceof InternalServerException
                    || t instanceof OpenAIIoException) {
                return true;
            }
            if (t instanceof OpenAIServiceException se && se.statusCode() >= 500) {
                return true;
            }
            if (t instanceof IOException) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }
}
