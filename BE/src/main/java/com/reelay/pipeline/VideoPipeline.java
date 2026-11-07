package com.reelay.pipeline;

import com.reelay.common.config.AsyncConfig;
import com.reelay.common.exception.BusinessException;
import com.reelay.common.exception.ErrorCode;
import com.reelay.common.storage.StorageService;
import com.reelay.pipeline.caption.service.AssSubtitleWriter;
import com.reelay.pipeline.crawl.dto.CrawledImage;
import com.reelay.pipeline.crawl.dto.CrawledPost;
import com.reelay.pipeline.crawl.service.BlogCrawler;
import com.reelay.pipeline.image.dto.CutImage;
import com.reelay.pipeline.image.service.ImageProcessor;
import com.reelay.pipeline.render.service.FfmpegRenderer;
import com.reelay.pipeline.script.dto.CutDraft;
import com.reelay.pipeline.script.service.ScriptService;
import com.reelay.pipeline.tts.dto.CutAudio;
import com.reelay.pipeline.tts.service.TtsService;
import com.reelay.video.dto.CutInput;
import com.reelay.video.entity.VideoScene;
import com.reelay.video.enums.StepType;
import com.reelay.video.service.JobProgressRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

// 6단계 오케스트레이터 (작업자 A)
//   1 CRAWL   A  블로그 -> 제목·본문·이미지 URL
//   2 SCRIPT  A  LLM -> 컷 18개
//   3 IMAGE   B  이미지 다운로드·컷 배정·Ken Burns
//   4 TTS     A  문장별 wav + 길이 실측
//   5 CAPTION B  subs.ass
//   6 RENDER  B  out.mp4
// 단계마다 진행 상태를 DB 에 기록한다. B 모듈 내부 동작은 모르고 인터페이스만 호출한다
@Component
public class VideoPipeline {

    private static final Logger log = LoggerFactory.getLogger(VideoPipeline.class);

    private final JobProgressRecorder recorder;
    private final StorageService storage;
    private final BlogCrawler crawler;
    private final ScriptService scriptService;
    private final TtsService ttsService;

    // B 구현이 develop 에 들어오기 전에도 서버가 뜨도록 ObjectProvider 로 받는다
    // 구현이 없으면 IMAGE 는 텍스트 카드로 폴백, CAPTION·RENDER 는 실패 처리
    private final ObjectProvider<ImageProcessor> imageProcessor;
    private final ObjectProvider<AssSubtitleWriter> subtitleWriter;
    private final ObjectProvider<FfmpegRenderer> renderer;

    // FFmpeg 렌더가 CPU 를 다 쓰므로 동시 실행 수를 따로 제한한다 (B 와 결정할 값)
    private final Semaphore renderPermits;

    public VideoPipeline(JobProgressRecorder recorder,
                         StorageService storage,
                         BlogCrawler crawler,
                         ScriptService scriptService,
                         TtsService ttsService,
                         ObjectProvider<ImageProcessor> imageProcessor,
                         ObjectProvider<AssSubtitleWriter> subtitleWriter,
                         ObjectProvider<FfmpegRenderer> renderer,
                         @Value("${reelay.render.max-concurrent:1}") int renderMaxConcurrent) {
        this.recorder = recorder;
        this.storage = storage;
        this.crawler = crawler;
        this.scriptService = scriptService;
        this.ttsService = ttsService;
        this.imageProcessor = imageProcessor;
        this.subtitleWriter = subtitleWriter;
        this.renderer = renderer;
        this.renderPermits = new Semaphore(Math.max(1, renderMaxConcurrent), true);
    }

    // POST /api/videos 가 202 를 돌려준 뒤 별도 스레드에서 실행된다
    @Async(AsyncConfig.PIPELINE_EXECUTOR)
    public void runAsync(UUID jobId, String sourceUrl) {
        run(jobId, sourceUrl);
    }

    // 테스트에서 동기로 부를 수 있게 분리
    public void run(UUID jobId, String sourceUrl) {
        long startedAt = System.currentTimeMillis();
        log.info("[{}] 파이프라인 시작 {}", jobId, sourceUrl);
        try {
            storage.prepare(jobId);

            CrawledPost post = step(jobId, StepType.CRAWL, () -> crawler.crawl(sourceUrl));
            recorder.updateTitle(jobId, post.title());

            List<CutDraft> drafts = step(jobId, StepType.SCRIPT, () -> scriptService.generate(post));

            List<CutImage> images = step(jobId, StepType.IMAGE, () -> processImages(jobId, post, drafts));

            List<CutAudio> audios = step(jobId, StepType.TTS, () -> ttsService.synthesize(jobId, drafts));

            List<CutInput> cuts = assemble(drafts, images, audios);

            Path subtitle = step(jobId, StepType.CAPTION, () -> required(subtitleWriter, "AssSubtitleWriter")
                    .write(jobId, post.title(), cuts));

            Path output = step(jobId, StepType.RENDER, () -> render(jobId, cuts, subtitle));

            int durationMs = totalDuration(cuts);
            recorder.complete(jobId, output.toString(), durationMs, toScenes(jobId, cuts, drafts));
            log.info("[{}] 완료 {}ms 영상, 소요 {}s", jobId, durationMs, (System.currentTimeMillis() - startedAt) / 1000);
        }
        catch (StepFailedException e) {
            log.warn("[{}] {} 단계 실패 - {}", jobId, e.step, e.code);
        }
        catch (RuntimeException e) {
            // 단계 밖(prepare 등)에서 터진 예외. job 이 RUNNING 으로 남지 않게 반드시 실패로 기록
            log.error("[{}] 파이프라인 예외", jobId, e);
            recorder.failJob(jobId, ErrorCode.INTERNAL_ERROR);
        }
        finally {
            storage.cleanTmp(jobId);
        }
    }

    // 단계 하나 실행 + 상태 기록. 실패하면 job 전체 FAILED
    private <T> T step(UUID jobId, StepType type, Supplier<T> action) {
        recorder.startStep(jobId, type);
        long t0 = System.currentTimeMillis();
        try {
            T result = action.get();
            recorder.finishStep(jobId, type);
            log.info("[{}] {} 완료 ({}ms)", jobId, type, System.currentTimeMillis() - t0);
            return result;
        }
        catch (RuntimeException e) {
            Throwable cause = unwrap(e);
            ErrorCode code = errorCodeOf(type, cause);
            log.warn("[{}] {} 실패 -> {}", jobId, type, code, cause);
            recorder.failStep(jobId, type, code, cause.toString());
            throw new StepFailedException(type, code);
        }
    }

    // IMAGE 는 전역 실패가 없다. 전부 실패해도 텍스트 카드(imagePath=null)로 진행
    private List<CutImage> processImages(UUID jobId, CrawledPost post, List<CutDraft> drafts) {
        ImageProcessor processor = imageProcessor.getIfAvailable();
        if (processor == null) {
            log.warn("[{}] ImageProcessor 구현이 없어 전 컷 텍스트 카드로 진행합니다", jobId);
            return textCards(drafts);
        }
        List<String> urls = post.images().stream().map(CrawledImage::url).toList();
        try {
            return processor.process(jobId, urls, drafts);
        }
        catch (RuntimeException e) {
            log.warn("[{}] IMAGE 처리 실패, 텍스트 카드로 폴백", jobId, e);
            return textCards(drafts);
        }
    }

    private Path render(UUID jobId, List<CutInput> cuts, Path subtitle) {
        FfmpegRenderer r = required(renderer, "FfmpegRenderer");
        try {
            renderPermits.acquire();
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.RENDER_FAILED, e);
        }
        try {
            return r.render(jobId, cuts, subtitle);
        }
        finally {
            renderPermits.release();
        }
    }

    // CutDraft + CutImage + CutAudio -> CutInput (B 와의 경계 데이터)
    // startMs 는 앞 컷 길이의 누적
    static List<CutInput> assemble(List<CutDraft> drafts, List<CutImage> images, List<CutAudio> audios) {
        Map<Integer, CutImage> imageByCut = new HashMap<>();
        for (CutImage image : images) {
            imageByCut.put(image.cutOrder(), image);
        }
        Map<Integer, CutAudio> audioByCut = new HashMap<>();
        for (CutAudio audio : audios) {
            audioByCut.put(audio.cutOrder(), audio);
        }

        List<CutInput> cuts = new ArrayList<>();
        int startMs = 0;
        for (CutDraft draft : drafts) {
            CutAudio audio = audioByCut.get(draft.cutOrder());
            if (audio == null) {
                throw new BusinessException(ErrorCode.TTS_FAILED);
            }
            CutImage image = imageByCut.get(draft.cutOrder());
            cuts.add(new CutInput(
                    draft.cutOrder(),
                    draft.narration(),
                    draft.caption(),
                    draft.highlight(),
                    image == null ? null : image.imagePath(),
                    audio.audioPath(),
                    audio.audioMs(),
                    startMs,
                    image == null ? null : image.kenburns()
            ));
            startMs += audio.audioMs();
        }
        return cuts;
    }

    private static List<CutImage> textCards(List<CutDraft> drafts) {
        return drafts.stream().map(d -> new CutImage(d.cutOrder(), null, null)).toList();
    }

    private static int totalDuration(List<CutInput> cuts) {
        return cuts.stream().mapToInt(CutInput::audioMs).sum();
    }

    private static List<VideoScene> toScenes(UUID jobId, List<CutInput> cuts, List<CutDraft> drafts) {
        Map<Integer, Integer> imageIndexByCut = new HashMap<>();
        for (CutDraft d : drafts) {
            imageIndexByCut.put(d.cutOrder(), d.sourceImageIndex());
        }
        return cuts.stream()
                .map(c -> VideoScene.of(jobId, c, imageIndexByCut.get(c.cutOrder())))
                .toList();
    }

    private static <T> T required(ObjectProvider<T> provider, String name) {
        T bean = provider.getIfAvailable();
        if (bean == null) {
            throw new IllegalStateException(name + " 구현이 아직 없습니다 (작업자 B)");
        }
        return bean;
    }

    // 단계별 기본 에러 코드. BusinessException 이면 그 코드를 그대로 쓴다 (예: CRAWL_EMPTY_BODY)
    static ErrorCode errorCodeOf(StepType type, Throwable cause) {
        if (cause instanceof BusinessException be) {
            return be.getErrorCode();
        }
        return switch (type) {
            case CRAWL -> ErrorCode.CRAWL_FAILED;
            case SCRIPT -> ErrorCode.SCRIPT_FAILED;
            case TTS -> ErrorCode.TTS_FAILED;
            case IMAGE, CAPTION, RENDER -> ErrorCode.RENDER_FAILED;
        };
    }

    private static Throwable unwrap(Throwable e) {
        Throwable t = e;
        while (t instanceof CompletionException && t.getCause() != null) {
            t = t.getCause();
        }
        return t;
    }

    // 단계 실패를 run() 까지 올려보내는 용도. 이미 DB 에 기록이 끝난 상태
    static final class StepFailedException extends RuntimeException {
        final StepType step;
        final ErrorCode code;

        StepFailedException(StepType step, ErrorCode code) {
            super(step + " failed: " + code, null, false, false);
            this.step = step;
            this.code = code;
        }
    }
}
