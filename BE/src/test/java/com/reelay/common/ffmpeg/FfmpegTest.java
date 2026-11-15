package com.reelay.common.ffmpeg;

import com.reelay.common.exception.FfmpegException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class FfmpegTest {

    private final Ffmpeg ffmpeg = new Ffmpeg("ffmpeg", "ffprobe");

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("wav를 만들고 길이를 잰다")
    void makesWavAndMeasuresIt() {
        Path wav = tempDir.resolve("tone.wav");

        ffmpeg.run(List.of(
                "-f", "lavfi", "-i", "sine=f=440:d=2.4",
                "-ar", "24000", "-ac", "1",
                wav.toString()
        ), Duration.ofSeconds(30));

        assertThat(Files.exists(wav)).isTrue();
        assertThat(ffmpeg.probeMillis(wav)).isEqualTo(2400);
    }

    @Test
    @DisplayName("실패하면 stderr를 예외에 담는다")
    void capturesStderrOnFailure() {
        Path missing = tempDir.resolve("nope.jpg");

        assertThatExceptionOfType(FfmpegException.class)
                .isThrownBy(() -> ffmpeg.run(List.of(
                        "-i", missing.toString(),
                        "-f", "null", "-"
                ), Duration.ofSeconds(30)))
                .satisfies(e -> {
                    assertThat(e.getKind()).isEqualTo(FfmpegException.Kind.EXIT_CODE);
                    assertThat(e.getExitCode()).isNotZero();
                    assertThat(e.getStderr()).isNotBlank();
                    assertThat(e.getStderr()).contains("nope.jpg");
                    assertThat(e.getMessage()).contains("명령:");
                });
    }

    @Test
    @DisplayName("타임아웃을 넘기면 강제 종료한다")
    void killsOnTimeout() {
        long startedAt = System.nanoTime();
        assertThatExceptionOfType(FfmpegException.class)
                .isThrownBy(() -> ffmpeg.run(List.of(
                        "-re",
                        "-f", "lavfi", "-i", "testsrc2=size=320x240:d=10",
                        "-f", "null", "-"
                ), Duration.ofSeconds(2)))
                .satisfies(e -> assertThat(e.getKind())
                        .isEqualTo(FfmpegException.Kind.TIMEOUT));

        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
        assertThat(elapsedMs).isLessThan(5_000);
    }

    @Test
    @DisplayName("실행 파일이 없으면 시작 실패로 구분한다")
    void failsWhenBinaryMissing() {
        Ffmpeg missing = new Ffmpeg("ffmpeg-not-installed", "ffprobe");

        assertThatExceptionOfType(FfmpegException.class)
                .isThrownBy(() -> missing.run(List.of("-f", "null", "-"),
                        Duration.ofSeconds(10)))
                .satisfies(e -> {
                    assertThat(e.getKind()).isEqualTo(FfmpegException.Kind.NOT_STARTED);
                    assertThat(e.getStderr()).isEmpty();
                    assertThat(e.getMessage()).contains("ffmpeg-not-installed");
                });
    }

    @Test
    @DisplayName("길이를 숫자로 못 읽으면 0을 지어내지 않고 실패한다")
    void failsWhenDurationIsNotANumber() {
        Path raw = tempDir.resolve("raw.h264");

        ffmpeg.run(List.of(
                "-f", "lavfi", "-i", "testsrc2=size=64x64:d=1",
                "-c:v", "libx264", "-f", "h264",
                raw.toString()
        ), Duration.ofSeconds(30));

        assertThatExceptionOfType(FfmpegException.class)
                .isThrownBy(() -> ffmpeg.probeSeconds(raw))
                .satisfies(e -> {
                    assertThat(e.getKind()).isEqualTo(FfmpegException.Kind.BAD_OUTPUT);
                    assertThat(e.getMessage()).contains("N/A");
                });
    }
}