package com.reelay.video.enums;

import java.util.Locale;
import java.util.Optional;

// 본문 직접 입력 때 영상 상단 제목을 정하는 방식
public enum TitleMode {
    MANUAL,  // 사용자가 직접 작성
    AUTO;    // 본문 기반 LLM 자동 생성 (첫 줄 키워드 + 둘째 줄 유입 문구)

    public static Optional<TitleMode> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(raw.strip().toUpperCase(Locale.ROOT)));
        }
        catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
