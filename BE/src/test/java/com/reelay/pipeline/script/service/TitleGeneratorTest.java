package com.reelay.pipeline.script.service;

import com.reelay.common.exception.BusinessException;
import com.reelay.common.exception.ErrorCode;
import com.reelay.pipeline.crawl.dto.CrawledPost;
import com.reelay.pipeline.script.dto.LlmTitle;
import com.reelay.video.dto.VideoTitle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TitleGeneratorTest {

    private static final String BLOG_TITLE = "자동차정비기능사 합격률｜필기시험 난이도와 합격 공부방법";

    private final ScriptLlmClient llm = mock(ScriptLlmClient.class);
    private final TitleGenerator generator = new TitleGenerator(llm);

    private static CrawledPost post() {
        return new CrawledPost("https://blog.naver.com/a/1", "a", "1", BLOG_TITLE, "본문", 1, List.of());
    }

    @Test
    @DisplayName("원문 제목 단어·첫 줄과 같은 말·8자 초과 후보는 건너뛰고 첫 유입 문구를 고른다")
    void picksFirstValidHook() {
        LlmTitle raw = new LlmTitle("자동차정비기능사 합격률",
                List.of("필기시험", "자동차정비기능사", "한 번에 붙는 공부 순서", "합격 지름길", "초보 필수"));

        VideoTitle title = TitleGenerator.pick(raw, BLOG_TITLE);

        assertThat(title.keyword()).isEqualTo("자동차정비기능사");
        assertThat(title.hook()).isEqualTo("합격 지름길");
    }

    @Test
    @DisplayName("첫 줄이 없으면 null (원문 제목 한 줄로 폴백)")
    void nullWithoutKeyword() {
        assertThat(TitleGenerator.pick(new LlmTitle(" ", List.of("TOP9")), BLOG_TITLE)).isNull();
        assertThat(TitleGenerator.pick(null, BLOG_TITLE)).isNull();
    }

    @Test
    @DisplayName("후보가 전부 탈락하면 한 번 다시 불러 둘째 줄을 채운다")
    void retriesWhenAllHooksRejected() {
        when(llm.callTitle(anyString(), anyString()))
                .thenReturn(new LlmTitle("자동차정비기능사", List.of("필기시험", "합격률")))
                .thenReturn(new LlmTitle("자동차정비기능사", List.of("독학 3단계")));

        VideoTitle title = generator.generate(post(), List.of());

        assertThat(title).isEqualTo(new VideoTitle("자동차정비기능사", "독학 3단계"));
        verify(llm, times(2)).callTitle(anyString(), anyString());
    }

    @Test
    @DisplayName("제목 생성이 실패해도 예외를 던지지 않고 null (job 은 계속 진행)")
    void failureReturnsNull() {
        when(llm.callTitle(anyString(), anyString())).thenThrow(new BusinessException(ErrorCode.SCRIPT_FAILED));

        assertThat(generator.generate(post(), List.of())).isNull();
    }
}
