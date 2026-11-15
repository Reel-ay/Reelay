package com.reelay.pipeline.manual.service;

import com.reelay.common.exception.BusinessException;
import com.reelay.common.exception.ErrorCode;
import com.reelay.video.enums.TitleMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ManualInputValidatorTest {

    private static void assertCode(Runnable r, ErrorCode code) {
        assertThatThrownBy(r::run).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(code);
    }

    @Test
    @DisplayName("본문: 없음·공백 / 100자 미만 / 20,000자 초과")
    void body() {
        assertCode(() -> ManualInputValidator.body(null), ErrorCode.BODY_REQUIRED);
        assertCode(() -> ManualInputValidator.body("   "), ErrorCode.BODY_REQUIRED);
        assertCode(() -> ManualInputValidator.body("가".repeat(99)), ErrorCode.BODY_TOO_SHORT);
        assertCode(() -> ManualInputValidator.body("가".repeat(20_001)), ErrorCode.BODY_TOO_LONG);
        assertThat(ManualInputValidator.body("  " + "가".repeat(100) + "  ")).hasSize(100);
    }

    @Test
    @DisplayName("제목 방식: 대소문자 무시, 그 외 값·없음은 INVALID_TITLE_MODE")
    void titleMode() {
        assertThat(ManualInputValidator.titleMode("manual")).isEqualTo(TitleMode.MANUAL);
        assertThat(ManualInputValidator.titleMode(" AUTO ")).isEqualTo(TitleMode.AUTO);
        assertCode(() -> ManualInputValidator.titleMode(null), ErrorCode.INVALID_TITLE_MODE);
        assertCode(() -> ManualInputValidator.titleMode("BLOG"), ErrorCode.INVALID_TITLE_MODE);
    }

    @Test
    @DisplayName("직접 작성: 첫 줄 필수·20자 이내, 둘째 줄 선택. 자동 생성이면 입력을 무시")
    void title() {
        assertCode(() -> ManualInputValidator.title(TitleMode.MANUAL, " "), ErrorCode.TITLE_REQUIRED);
        assertCode(() -> ManualInputValidator.title(TitleMode.MANUAL, "가".repeat(21)), ErrorCode.TITLE_TOO_LONG);
        assertThat(ManualInputValidator.title(TitleMode.MANUAL, " 제주   한달살기 ")).isEqualTo("제주 한달살기");
        assertThat(ManualInputValidator.titleHook(TitleMode.MANUAL, "")).isNull();
        assertThat(ManualInputValidator.title(TitleMode.AUTO, "무시됨")).isNull();
        assertThat(ManualInputValidator.titleHook(TitleMode.AUTO, "무시됨")).isNull();
    }

    @Test
    @DisplayName("사진은 10장까지")
    void imageCount() {
        ManualInputValidator.imageCount(10);
        assertCode(() -> ManualInputValidator.imageCount(11), ErrorCode.TOO_MANY_IMAGES);
    }
}
