package com.reelay.pipeline.manual.service;

import com.reelay.common.exception.BusinessException;
import com.reelay.common.exception.ErrorCode;
import com.reelay.video.enums.TitleMode;

// 본문 직접 입력 값 검증. 실패하면 400 + 사용자에게 보여줄 메시지
public final class ManualInputValidator {

    public static final int MIN_BODY_CHARS = 100;      // 크롤러 하한과 같다
    public static final int MAX_BODY_CHARS = 20_000;
    public static final int MAX_TITLE_LINE_CHARS = 20; // 화면 권장은 8자. 넘어도 자막 쪽이 글자 크기를 줄여 맞춘다
    public static final int MAX_IMAGES = 10;

    private ManualInputValidator() {
    }

    public static String body(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(ErrorCode.BODY_REQUIRED);
        }
        String body = raw.strip();
        if (body.length() < MIN_BODY_CHARS) {
            throw new BusinessException(ErrorCode.BODY_TOO_SHORT);
        }
        if (body.length() > MAX_BODY_CHARS) {
            throw new BusinessException(ErrorCode.BODY_TOO_LONG);
        }
        return body;
    }

    public static TitleMode titleMode(String raw) {
        return TitleMode.parse(raw).orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TITLE_MODE));
    }

    // MANUAL 이면 필수
    public static String title(TitleMode mode, String raw) {
        if (mode != TitleMode.MANUAL) {
            return null;
        }
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(ErrorCode.TITLE_REQUIRED);
        }
        return line(raw);
    }

    // 둘째 줄은 선택
    public static String titleHook(TitleMode mode, String raw) {
        if (mode != TitleMode.MANUAL || raw == null || raw.isBlank()) {
            return null;
        }
        return line(raw);
    }

    public static void imageCount(int count) {
        if (count > MAX_IMAGES) {
            throw new BusinessException(ErrorCode.TOO_MANY_IMAGES);
        }
    }

    private static String line(String raw) {
        String line = raw.replaceAll("\\s+", " ").strip();
        if (line.codePointCount(0, line.length()) > MAX_TITLE_LINE_CHARS) {
            throw new BusinessException(ErrorCode.TITLE_TOO_LONG);
        }
        return line;
    }
}
