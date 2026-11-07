package com.reelay.pipeline.tts.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

// ffprobe 로 오디오 길이(ms)를 잰다
// ffprobe 를 못 찾거나 실패하면 -1 을 돌려주고, 호출한 쪽이 wav 헤더 계산값을 쓴다
@Component
public class AudioDurationProbe {

    private static final Logger log = LoggerFactory.getLogger(AudioDurationProbe.class);
    private static final long TIMEOUT_SECONDS = 10;

    private final String ffprobe;
    private volatile boolean warned = false;

    public AudioDurationProbe(@Value("${reelay.ffprobe.path:ffprobe}") String ffprobe) {
        this.ffprobe = ffprobe;
    }

    public int probeMs(Path file) {
        ProcessBuilder pb = new ProcessBuilder(
                ffprobe, "-v", "error",
                "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1",
                file.toAbsolutePath().toString());
        pb.redirectErrorStream(true);
        try {
            Process process = pb.start();
            String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return -1;
            }
            if (process.exitValue() != 0) {
                log.warn("ffprobe 실패 ({}): {}", process.exitValue(), out);
                return -1;
            }
            return (int) Math.round(Double.parseDouble(out) * 1000);
        }
        catch (IOException e) {
            if (!warned) {
                warned = true;
                log.warn("ffprobe 를 실행하지 못했습니다. FFmpeg 설치와 PATH 를 확인하세요 (reelay.ffprobe.path={})", ffprobe);
            }
            return -1;
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        }
        catch (NumberFormatException e) {
            log.warn("ffprobe 출력을 해석하지 못했습니다: {}", e.getMessage());
            return -1;
        }
    }
}
