package com.reelay.pipeline.script.service;

import com.reelay.pipeline.crawl.dto.CrawledPost;
import com.reelay.pipeline.script.dto.LlmScript;
import com.reelay.pipeline.script.dto.Script;
import com.reelay.video.dto.VideoTitle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScriptServiceTest {

    private final ScriptLlmClient llm = mock(ScriptLlmClient.class);
    private final TitleGenerator titleGenerator = mock(TitleGenerator.class);
    private final ScriptService service = new ScriptService(llm, titleGenerator);

    private static final CrawledPost POST =
            new CrawledPost(null, null, null, null, "본문", 1, List.of());

    private void stubCuts() {
        when(llm.call(anyString(), anyString())).thenReturn(new LlmScript(List.of(
                new LlmScript.LlmCut("첫 문장이에요", "첫 문장", null, null),
                new LlmScript.LlmCut("둘째 문장이에요", "둘째 문장", null, null),
                new LlmScript.LlmCut("셋째 문장이에요", "셋째 문장", null, null))));
    }

    @Test
    @DisplayName("직접 쓴 제목이 있으면 제목 LLM 을 부르지 않고 그대로 쓴다")
    void fixedTitleSkipsGenerator() {
        stubCuts();
        VideoTitle fixed = new VideoTitle("제주 한달살기", "비용 총정리");

        Script script = service.generate(POST, fixed);

        assertThat(script.videoTitle()).isEqualTo(fixed);
        assertThat(script.cuts()).hasSize(3);
        verify(titleGenerator, never()).generate(any(), any());
    }

    @Test
    @DisplayName("제목이 없으면 TitleGenerator 로 만든다")
    void generatesTitleWhenNotFixed() {
        stubCuts();
        VideoTitle generated = new VideoTitle("제주 한달살기", "숨은 명소");
        when(titleGenerator.generate(any(), any())).thenReturn(generated);

        Script script = service.generate(POST);

        assertThat(script.videoTitle()).isEqualTo(generated);
    }
}
