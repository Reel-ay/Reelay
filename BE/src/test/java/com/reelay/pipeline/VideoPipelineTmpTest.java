package com.reelay.pipeline;

import com.reelay.common.exception.ErrorCode;
import com.reelay.common.storage.StorageService;
import com.reelay.pipeline.caption.service.SubtitleWriter;
import com.reelay.pipeline.crawl.dto.CrawledPost;
import com.reelay.pipeline.crawl.service.BlogCrawler;
import com.reelay.pipeline.image.service.ImageProcessor;
import com.reelay.pipeline.render.service.VideoRenderer;
import com.reelay.pipeline.script.dto.CutDraft;
import com.reelay.pipeline.script.dto.Script;
import com.reelay.pipeline.script.service.ScriptService;
import com.reelay.pipeline.tts.dto.CutAudio;
import com.reelay.pipeline.tts.service.TtsService;
import com.reelay.video.dto.VideoTitle;
import com.reelay.video.enums.StepType;
import com.reelay.video.service.JobProgressRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 실패한 job 의 tmp 는 남기고, 성공한 job 의 tmp 만 비우는지 확인한다
class VideoPipelineTmpTest {

    private static final String URL = "https://blog.naver.com/someone/123";

    private final UUID jobId = UUID.randomUUID();

    private JobProgressRecorder recorder;
    private StorageService storage;
    private BlogCrawler crawler;
    private ScriptService scriptService;
    private TtsService ttsService;
    private VideoRenderer renderer;
    private SubtitleWriter subtitleWriter;
    private VideoPipeline pipeline;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        recorder = mock(JobProgressRecorder.class);
        storage = mock(StorageService.class);
        crawler = mock(BlogCrawler.class);
        scriptService = mock(ScriptService.class);
        ttsService = mock(TtsService.class);
        subtitleWriter = mock(SubtitleWriter.class);
        renderer = mock(VideoRenderer.class);

        ObjectProvider<ImageProcessor> imageProvider = mock(ObjectProvider.class);
        ObjectProvider<SubtitleWriter> subtitleProvider = mock(ObjectProvider.class);
        ObjectProvider<VideoRenderer> rendererProvider = mock(ObjectProvider.class);
        when(imageProvider.getIfAvailable()).thenReturn(null); // 텍스트 카드로 진행
        when(subtitleProvider.getIfAvailable()).thenReturn(subtitleWriter);
        when(rendererProvider.getIfAvailable()).thenReturn(renderer);

        when(storage.tmpDir(jobId)).thenReturn(Path.of("storage", jobId.toString(), "tmp"));
        when(crawler.crawl(URL)).thenReturn(
                new CrawledPost(URL, "someone", "123", "제목", "본문", 1, List.of()));
        when(scriptService.generate(any())).thenReturn(new Script(new VideoTitle("클로드 사용법", "TOP9"), List.of(
                new CutDraft(0, "첫 문장이에요", "첫 문장", null, null))));
        when(ttsService.synthesize(eq(jobId), anyList())).thenReturn(List.of(
                new CutAudio(0, Path.of("0.wav"), 2500)));
        when(subtitleWriter.writeWithTitle(eq(jobId), any(), anyList())).thenReturn(Path.of("subs.ass"));

        pipeline = new VideoPipeline(recorder, storage, crawler, scriptService, ttsService,
                imageProvider, subtitleProvider, rendererProvider, 1);
    }

    @Test
    @DisplayName("성공하면 tmp 를 비운다")
    void cleansTmpOnSuccess() {
        when(renderer.render(eq(jobId), anyList(), any())).thenReturn(Path.of("out.mp4"));

        pipeline.run(jobId, URL);

        verify(recorder).complete(eq(jobId), anyString(), anyInt(), anyList());
        verify(storage).cleanTmp(jobId);
    }

    @Test
    @DisplayName("LLM 이 만든 두 줄 제목을 저장하고 자막에 넘긴다 (원문 제목 아님)")
    void usesGeneratedVideoTitle() {
        when(renderer.render(eq(jobId), anyList(), any())).thenReturn(Path.of("out.mp4"));

        pipeline.run(jobId, URL);

        verify(recorder).updateTitle(jobId, "제목");
        VideoTitle expected = new VideoTitle("클로드 사용법", "TOP9");
        verify(recorder).updateVideoTitle(jobId, expected);
        verify(subtitleWriter).writeWithTitle(eq(jobId), eq(expected), anyList());
    }

    @Test
    @DisplayName("단계가 실패하면 tmp 를 남긴다 (원인 추적용)")
    void keepsTmpOnStepFailure() {
        when(renderer.render(eq(jobId), anyList(), any())).thenThrow(new RuntimeException("ffmpeg 실패"));

        pipeline.run(jobId, URL);

        verify(recorder).failStep(eq(jobId), eq(StepType.RENDER), eq(ErrorCode.RENDER_FAILED), anyString());
        verify(storage, never()).cleanTmp(any());
    }

    @Test
    @DisplayName("단계 밖에서 예외가 나도 tmp 를 남긴다")
    void keepsTmpOnUnexpectedFailure() {
        doThrow(new RuntimeException("폴더 생성 실패")).when(storage).prepare(jobId);

        pipeline.run(jobId, URL);

        verify(recorder).failJob(jobId, ErrorCode.INTERNAL_ERROR);
        verify(storage, never()).cleanTmp(any());
    }

    @Test
    @DisplayName("성공 후 tmp 정리가 실패해도 job 을 FAILED 로 바꾸지 않는다")
    void cleanupFailureDoesNotFailJob() {
        when(renderer.render(eq(jobId), anyList(), any())).thenReturn(Path.of("out.mp4"));
        doThrow(new RuntimeException("삭제 실패")).when(storage).cleanTmp(jobId);

        pipeline.run(jobId, URL);

        verify(recorder).complete(eq(jobId), anyString(), anyInt(), anyList());
        verify(recorder, never()).failJob(any(), any());
        verify(recorder, never()).failStep(any(), any(), any(), any());
    }
}
