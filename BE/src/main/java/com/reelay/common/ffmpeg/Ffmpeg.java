package com.reelay.common.ffmpeg;

import com.reelay.common.exception.FfmpegException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.TimeUnit;

// FFmpeg, FFprobe 실행
@Component
public class Ffmpeg {
    private static final Logger log = LoggerFactory.getLogger(Ffmpeg.class);

    private static final List<String> QUIET = List.of("-v", "error");

    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(15);

    private static final int STDERR_TAIL_LINES = 80;
    private static final int STDOUT_MAX_LINES = 200;
    private static final long DRAIN_WAIT_MS = 2_000;

    private final String ffmpegPath;
    private final String ffprobePath;

    public Ffmpeg(@Value("${reelay.ffmpeg.ffmpeg-path:ffmpeg}") String ffmpegPath,
                  @Value("${reelay.ffmpeg.ffprobe-path:ffprobe}") String ffprobePath) {
        this.ffmpegPath = ffmpegPath;
        this.ffprobePath = ffprobePath;
    }

    // ffmpeg -y -v error -i img.jpg out.mp4
    public FfmpegResult run(List<String> args, Duration timeout) {
        List<String> command = new ArrayList<>(args.size() + 4);
        command.add(ffmpegPath);
        command.add("-y");
        command.addAll(QUIET);
        command.addAll(args);
        return execute(command, timeout);
    }

    // 길이 측정
    // ffprobe -v error -show_entries format=duration -of csv=p=0 cut0.wav
    public double probeSeconds(Path file) {
        FfmpegResult result = execute(List.of(
                ffprobePath, "-v", "error",
                "-show_entries", "format=duration",
                "-of", "csv=p=0",
                file.toString()
        ), PROBE_TIMEOUT);

        String line = result.firstLine();
        try {
            return Double.parseDouble(line);
        }
        catch(NumberFormatException e) {
            throw FfmpegException.badOutput(List.of(ffprobePath, file.toString()),
                    line, result.stderr(), result.elapsedMs());
        }
    }

    // 밀리 초로 변환
    public int probeMillis(Path file) {
        return (int) Math.round(probeSeconds(file) * 1_000);
    }

    public static String filterPath(Path path) {
        String p = path.toAbsolutePath().toString().replace('\\', '/');
        if(p.indexOf('\'') >= 0) {
            throw new IllegalArgumentException("경로에 작은따옴표 사용 불가 - " + p);
        }
        return "'" + p.replace(":", "\\:") + "'";
    }

    // FFmpeg 프로세스를 실행, 출력 로그 수집, 타임아웃 및 예외 처리
    // 실행 시간 측정 -> 프로세스 실행 -> 출력 수집 -> 종료 대기 -> 결과 확인 및 반환
    private FfmpegResult execute(List<String> command, Duration timeout) {
        // 실행 시간 측정
        long startedAt = System.nanoTime();

        // 프로세스 실행
        Process process = start(command);

        // 출력 수집 및 시작
        StreamCollector stdout = new StreamCollector(process.getInputStream(), STDOUT_MAX_LINES);
        StreamCollector stderr = new StreamCollector(process.getErrorStream(), STDERR_TAIL_LINES);
        Thread stdoutThread = drain(stdout, "ffmpeg-stdout");
        Thread stderrThread = drain(stderr, "ffmpeg-stderr");

        try {
            // 프로세스 종료 대기
            if(!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw FfmpegException.timeout(command, timeout.toSeconds(), stderr.text(), elapsedMs(startedAt));
            }

            // 출력 수집 스레드 종료 대기
            stdoutThread.join(DRAIN_WAIT_MS);
            stderrThread.join(DRAIN_WAIT_MS);

            // 실행 결과 확인
            int exitCode = process.exitValue();
            if(exitCode != 0) {
                throw FfmpegException.exitCode(command, exitCode, stderr.text(), elapsedMs(startedAt));
            }

            // 결과 반환
            return new FfmpegResult(stdout.text(), stderr.text(), elapsedMs(startedAt));
        }
        catch(InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw FfmpegException.interrupted(command, stderr.text(), elapsedMs(startedAt));
        }
        // 프로세스 정리
        finally {
            if(process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    // 외부 프로세스 실행
    private Process start(List<String> command) {
        log.debug("실행: {}", String.join(" ", command));
        try {
            return new ProcessBuilder(command).start();
        }
        catch(IOException e) {
            throw FfmpegException.notStarted(command, e.getMessage());
        }
    }

    // 출력 수집 스레드 실행
    private Thread drain(StreamCollector collector, String name) {
        Thread thread = new Thread(collector, name);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    // 실행 시간 계산
    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    // FFmpeg의 표준 출력과 오류 출력을 읽고 로그를 관리하는 내부 클래스
    private static final class StreamCollector implements Runnable {

        private final InputStream stream;
        private final int maxLines;
        private final Deque<String> lines = new ArrayDeque<>();

        private StreamCollector(InputStream stream, int maxLines) {
            this.stream = stream;
            this.maxLines = maxLines;
        }

        // 출력 스트림 -> 로그 수집
        @Override
        public void run() {
            try(BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while((line = reader.readLine()) != null) {
                    append(line);
                }
            }
            catch(IOException e) {
                log.debug("스트림 수집 중단 - {}", e.getMessage());
            }
        }

        // 로그 저장 및 오래된 로그 제거
        private synchronized void append(String line) {
            lines.addLast(line);
            if(lines.size() > maxLines) {
                lines.removeFirst();
            }
        }

        // 로그 -> 문자열
        private synchronized String text() {
            return String.join(System.lineSeparator(), lines);
        }
    }
}
