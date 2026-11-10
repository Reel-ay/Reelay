package com.reelay.pipeline.script.service;

import com.reelay.common.exception.BusinessException;
import com.reelay.common.exception.ErrorCode;
import com.reelay.pipeline.script.dto.CutDraft;
import com.reelay.pipeline.script.dto.LlmScript;
import com.reelay.pipeline.script.dto.LlmScript.LlmCut;
import com.reelay.pipeline.script.dto.ScriptResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScriptValidatorTest {

    private final ScriptValidator validator = new ScriptValidator();

    // 상한(25자) 안쪽의 기본 문장
    private static final String OK_NARRATION = "정보통신기사는 실기 비중이 커요";

    private static List<LlmCut> cuts(int n) {
        List<LlmCut> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(new LlmCut(OK_NARRATION, "실기 비중", "실기", 0));
        }
        return list;
    }

    @Test
    @DisplayName("정상 대본은 cutOrder 를 0부터 매기고 그대로 통과한다")
    void valid() {
        ScriptResult result = validator.validate(new LlmScript(cuts(18)), 3);

        assertThat(result.cuts()).hasSize(18);
        assertThat(result.cuts()).extracting(CutDraft::cutOrder).startsWith(0, 1, 2).endsWith(17);
        assertThat(result.hasTooLong()).isFalse();
    }

    @Test
    @DisplayName("caption 안에 없는 highlight 는 null 로 지운다 (B 와의 약속)")
    void highlightMustBeSubstring() {
        List<LlmCut> list = cuts(18);
        list.set(0, new LlmCut(OK_NARRATION, "실기 비중", "필기", 0));

        CutDraft first = validator.validate(new LlmScript(list), 3).cuts().get(0);

        assertThat(first.highlight()).isNull();
    }

    @Test
    @DisplayName("범위 밖 이미지 인덱스는 null (텍스트 카드)")
    void imageIndexOutOfRange() {
        List<LlmCut> list = cuts(18);
        list.set(0, new LlmCut(OK_NARRATION, "실기 비중", "실기", 7));
        list.set(1, new LlmCut(OK_NARRATION, "실기 비중", "실기", -1));

        List<CutDraft> result = validator.validate(new LlmScript(list), 3).cuts();

        assertThat(result.get(0).sourceImageIndex()).isNull();
        assertThat(result.get(1).sourceImageIndex()).isNull();
        assertThat(result.get(2).sourceImageIndex()).isZero();
    }

    @Test
    @DisplayName("글자 수 상한을 넘은 컷 번호를 모은다")
    void collectTooLong() {
        List<LlmCut> list = cuts(18);
        list.set(4, new LlmCut("이 문장은 공백을 빼고 세어도 스물다섯 글자를 훌쩍 넘어가는 아주 긴 나레이션이에요", "긴 나레이션", null, null));

        ScriptResult result = validator.validate(new LlmScript(list), 3);

        assertThat(result.tooLongCutOrders()).containsExactly(4);
    }

    @Test
    @DisplayName("빈 나레이션 컷은 버리고, caption 이 비면 나레이션으로 채운다")
    void dropBlankAndFillCaption() {
        List<LlmCut> list = cuts(18);
        list.add(2, new LlmCut("  ", "x", null, null));
        list.set(0, new LlmCut(OK_NARRATION, null, null, null));

        List<CutDraft> result = validator.validate(new LlmScript(list), 3).cuts();

        assertThat(result).hasSize(18);
        assertThat(result.get(0).caption()).isEqualTo(OK_NARRATION);
    }

    @Test
    @DisplayName("짧은 글용으로 컷이 적어도 받아주지만, 3개 미만이면 SCRIPT_FAILED")
    void tooFewCuts() {
        assertThat(validator.validate(new LlmScript(cuts(5)), 3).cuts()).hasSize(5);

        assertThatThrownBy(() -> validator.validate(new LlmScript(cuts(2)), 3))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SCRIPT_FAILED);
    }

    @Test
    @DisplayName("컷이 25개를 넘으면 뒤를 자른다")
    void tooManyCuts() {
        assertThat(validator.validate(new LlmScript(cuts(30)), 3).cuts()).hasSize(ScriptRules.MAX_CUTS);
    }

    @Test
    @DisplayName("목표 컷 수는 본문 100자당 1컷, 4~18 사이")
    void targetCuts() {
        assertThat(ScriptRules.targetCuts(1_855)).isEqualTo(18); // 실측 중간값
        assertThat(ScriptRules.targetCuts(900)).isEqualTo(9);
        assertThat(ScriptRules.targetCuts(150)).isEqualTo(4);
    }

    @Test
    @DisplayName("글자 수는 공백을 빼고 센다")
    void lengthIgnoresWhitespace() {
        assertThat(ScriptRules.length("가 나 다")).isEqualTo(3);
        assertThat(ScriptRules.length(null)).isZero();
    }

    @Test
    @DisplayName("첫 줄은 물음표·느낌표·따옴표·이모지를 지우고, 둘째 줄은 ?! 를 남긴다")
    void titleSymbols() {
        assertThat(ScriptValidator.normalizeTitleLine("\"정보통신기사?!\" 🔥", false)).isEqualTo("정보통신기사");
        assertThat(ScriptValidator.normalizeTitleLine("모르면 손해! 🔥", true)).isEqualTo("모르면 손해!");
    }

    @Test
    @DisplayName("한 줄이 공백 포함 8자를 넘으면 뒤 어절부터 버린다")
    void titleDropsTrailingWords() {
        // "클로드 사용법 총정리" = 11자 -> "클로드 사용법" (7자)
        assertThat(ScriptValidator.normalizeTitleLine("클로드 사용법 총정리", false)).isEqualTo("클로드 사용법");
        assertThat(ScriptValidator.normalizeTitleLine("TOP9", true)).isEqualTo("TOP9");
    }

    @Test
    @DisplayName("한 어절이 8자를 넘으면 자르지 않고 둔다 (자막 쪽 안전망)")
    void titleKeepsSingleLongWord() {
        assertThat(ScriptValidator.normalizeTitleLine("전자계산기조직응용기사", false)).isEqualTo("전자계산기조직응용기사");
    }


    @Test
    @DisplayName("공백·문장부호를 빼고 한쪽이 다른 쪽을 품으면 같은 말로 본다")
    void sameText() {
        assertThat(ScriptValidator.isSameText("정보통신기사", "정보통신기사!")).isTrue();
        assertThat(ScriptValidator.isSameText("정보통신기사", "독학 3단계")).isFalse();
    }

    @Test
    @DisplayName("둘째 줄 후보가 원문 제목 단어로만 이루어지면 블로그 문구로 본다")
    void copiedFromBlogTitle() {
        String blogTitle = "자동차정비기능사 합격률｜필기시험 난이도와 합격 공부방법";
        assertThat(ScriptValidator.isCopiedFrom("필기시험", blogTitle)).isTrue();
        assertThat(ScriptValidator.isCopiedFrom("합격 공부방법", blogTitle)).isTrue();
        assertThat(ScriptValidator.isCopiedFrom("합격 지름길", blogTitle)).isFalse();
        assertThat(ScriptValidator.isCopiedFrom("TOP9", blogTitle)).isFalse();
        assertThat(ScriptValidator.isCopiedFrom("TOP9", null)).isFalse();
    }
}
