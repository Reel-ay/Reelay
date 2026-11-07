package com.reelay.pipeline.tts.service;

import com.reelay.common.exception.BusinessException;
import com.reelay.common.exception.ErrorCode;
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

    private final SpeechClient speechClient;
    private final AudioDurationProbe probe;
    private final StorageService storage;
    private final AtomicInteger threadSeq = new AtomicInteger();
    private final ExecutorService executor = Executors.newFixedThreadPool(PARALLELISM, r -> {
        Thread t = new Thread(r, "tts-" + threadSeq.incrementAndGet());
        t.setDaemon(true);
        return t;
    });

    public TtsService(SpeechClient speechClient, AudioDurationProbe probe, StorageService storage) {
        this.speechClient = speechClient;
        this.probe = probe;
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

        Path path = storage.audioPath(jobId, cut.cutOrder());
        write(path, wav);

        int headerMs = info.durationMs();
        int probedMs = probe.probeMs(path);
        int audioMs = probedMs > 0 ? probedMs : headerMs;

        if (probedMs > 0 && Math.abs(probedMs - headerMs) > MISMATCH_WARN_MS) {
            log.warn("[TTS] cut {} 길이 불일치 ffprobe={}ms header={}ms", cut.cutOrder(), probedMs, headerMs);
        }
        if (audioMs <= 0) {
            throw new BusinessException(ErrorCode.TTS_FAILED);
        }
        log.debug("[TTS] cut {} {}ms '{}'", cut.cutOrder(), audioMs, cut.narration());
        return new CutAudio(cut.cutOrder(), path, audioMs);
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
