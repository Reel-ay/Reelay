package com.reelay.pipeline.tts.service;

import com.reelay.common.exception.BusinessException;
import com.reelay.common.exception.ErrorCode;
import com.reelay.common.exception.FfmpegException;
import com.reelay.common.ffmpeg.Ffmpeg;
import com.reelay.common.storage.StorageService;
import com.reelay.pipeline.script.dto.CutDraft;
import com.reelay.pipeline.tts.dto.CutAudio;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

// TTS 단계: 문장별 wav 생성 + 길이 실측
// - 18문장을 동시에 호출한다. 과금은 입력 글자 수 기준이라 1번에 보내든 18번에 나눠 보내든 비용이 같고,
//   병렬이면 가장 긴 문장 하나만큼만 기다리면 된다
// - 쪼개는 단위는 문장까지. 어절로 쪼개면 로봇 음성이 된다
@Service
public class TtsService {

    private static final Logger log = LoggerFactory.getLogger(TtsService.class);

    // OpenAI 동시 요청 수 제한을 고려한 상한
    private static final int PARALLELISM = 6;

    // 길이 측정값끼리 이 이상 차이 나면 로그로 남긴다
    private static final int MISMATCH_WARN_MS = 50;

    // 15자 문장은 최소 2초대. 이보다 짧으면 측정이 잘못된 것으로 본다
    // (ffprobe 는 이미지 등 엉뚱한 파일에도 오류 없이 0.04초를 돌려준다 -> 그 컷이 1프레임으로 사라짐)
    static final int MIN_AUDIO_MS = 500;

    // RENDER 가 concat -c copy 로 이어 붙이므로 모든 컷의 wav 형식이 같아야 한다 (B 검증값)
    static final int EXPECTED_SAMPLE_RATE = 24_000;
    static final int EXPECTED_CHANNELS = 1;
    static final int EXPECTED_BITS = 16;

    private final SpeechClient speechClient;
    private final Ffmpeg ffmpeg;
    private final StorageService storage;
    private final AtomicInteger threadSeq = new AtomicInteger();
    private final ExecutorService executor = Executors.newFixedThreadPool(PARALLELISM, r -> {
        Thread t = new Thread(r, "tts-" + threadSeq.incrementAndGet());
        t.setDaemon(true);
        return t;
    });

    public TtsService(SpeechClient speechClient, Ffmpeg ffmpeg, StorageService storage) {
        this.speechClient = speechClient;
        this.ffmpeg = ffmpeg;
        this.storage = storage;
    }

    // cutOrder 순으로 정렬해서 돌려준다
    public List<CutAudio> synthesize(UUID jobId, List<CutDraft> cuts) {
        List<CompletableFuture<CutAudio>> futures = new ArrayList<>();
        for (CutDraft cut : cuts) {
            futures.add(CompletableFuture.supplyAsync(() -> synthesizeOne(jobId, cut), executor));
        }

        try {
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        }
        catch (CompletionException e) {
            // 한 문장이라도 재시도를 다 쓰고 실패하면 TTS 단계 전체 실패
            futures.forEach(f -> f.cancel(true));
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof BusinessException be) {
                throw be;
            }
            throw new BusinessException(ErrorCode.TTS_FAILED, cause);
        }

        return futures.stream()
                .map(CompletableFuture::join)
                .sorted(Comparator.comparingInt(CutAudio::cutOrder))
                .toList();
    }

    private CutAudio synthesizeOne(UUID jobId, CutDraft cut) {
        byte[] wav = speechClient.synthesize(cut.narration());

        WavFiles.Info info;
        try {
            info = WavFiles.parse(wav);
            WavFiles.fixHeader(wav, info);
        }
        catch (IllegalArgumentException e) {
            log.error("[TTS] cut {} 응답이 wav 가 아닙니다", cut.cutOrder());
            throw new BusinessException(ErrorCode.TTS_FAILED, e);
        }
        checkFormat(cut.cutOrder(), info);

        Path path = storage.audioPath(jobId, cut.cutOrder());
        write(path, wav);

        int audioMs = measure(cut.cutOrder(), path, info.durationMs());
        log.debug("[TTS] cut {} {}ms '{}'", cut.cutOrder(), audioMs, cut.narration());
        return new CutAudio(cut.cutOrder(), path, audioMs);
    }

    // ffprobe 실측값을 쓴다. wav 헤더로 계산한 값과 크게 다르면 로그로 남긴다
    private int measure(int cutOrder, Path path, int headerMs) {
        int probedMs;
        try {
            probedMs = ffmpeg.probeMillis(path);
        }
        catch (FfmpegException e) {
            // FfmpegException 은 BusinessException 계열이 아니라 여기서 TTS_FAILED 로 바꾼다
            log.error("[TTS] cut {} ffprobe 실패: {}", cutOrder, e.getMessage());
            throw new BusinessException(ErrorCode.TTS_FAILED, e);
        }
        if (Math.abs(probedMs - headerMs) > MISMATCH_WARN_MS) {
            log.warn("[TTS] cut {} 길이 불일치 ffprobe={}ms header={}ms", cutOrder, probedMs, headerMs);
        }
        if (probedMs < MIN_AUDIO_MS) {
            log.error("[TTS] cut {} 길이가 {}ms 로 너무 짧습니다 ({})", cutOrder, probedMs, path);
            throw new BusinessException(ErrorCode.TTS_FAILED);
        }
        return probedMs;
    }

    private void checkFormat(int cutOrder, WavFiles.Info info) {
        if (info.sampleRate() != EXPECTED_SAMPLE_RATE
                || info.channels() != EXPECTED_CHANNELS
                || info.bitsPerSample() != EXPECTED_BITS) {
            log.error("[TTS] cut {} wav 형식이 다릅니다: {}Hz / {}ch / {}bit (기대값 {}Hz / {}ch / {}bit)",
                    cutOrder, info.sampleRate(), info.channels(), info.bitsPerSample(),
                    EXPECTED_SAMPLE_RATE, EXPECTED_CHANNELS, EXPECTED_BITS);
            throw new BusinessException(ErrorCode.TTS_FAILED);
        }
    }

    private void write(Path path, byte[] bytes) {
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, bytes);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
