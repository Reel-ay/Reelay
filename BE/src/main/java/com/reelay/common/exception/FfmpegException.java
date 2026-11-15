package com.reelay.common.exception;

import java.util.List;

// FFmpeg 실행 실패 -> 사용자에게 노출 X(내부 진단용)
// 용도 : 로그, 명령과 필터그래프 오류 문자열 확인
// BusinessException만 잡는 전역 핸들러 X -> 호출부에서 로그로 남기고, BusinessException(RENDER_FAILED)로 변환할 것
public class FfmpegException extends RuntimeException{
    public static final int NOT_STARTED = -1;
    public enum Kind {
        NOT_STARTED,   // 실행 파일을 못 찾음
        TIMEOUT,       // 시간 초과로 강제 종료
        INTERRUPTED,   // 대기 중 중단 요청
        BAD_OUTPUT,    // 종료 코드는 0인데 출력이 기대한 형식이 아님
        EXIT_CODE      // 0이 아닌 종료 코드로 끝남
    }
    private final Kind kind;

    private final int exitCode;
    private final String stderr;

    private FfmpegException(List<String> command, Kind kind, int exitCode, String reason, String stderr, long elapsedMs) {
        super(describe(command, reason, stderr, elapsedMs));
        this.kind = kind;
        this.exitCode = exitCode;
        this.stderr = stderr == null ? "" : stderr;
    }

    public static FfmpegException notStarted(List<String> command, String detail) {
        return new FfmpegException(command, Kind.NOT_STARTED, 0,
                "프로세스를 시작하지 못함 - " + detail, "", 0);
    }

    public static FfmpegException timeout(List<String> command, long timeoutSeconds,
                                          String stderr, long elapsedMs) {
        return new FfmpegException(command, Kind.TIMEOUT, 0,
                "타임아웃 " + timeoutSeconds + "초를 넘겨 강제 종료", stderr, elapsedMs);
    }

    public static FfmpegException interrupted(List<String> command,
                                              String stderr, long elapsedMs) {
        return new FfmpegException(command, Kind.INTERRUPTED, 0,
                "대기 중 인터럽트", stderr, elapsedMs);
    }

    public static FfmpegException exitCode(List<String> command, int exitCode,
                                           String stderr, long elapsedMs) {
        return new FfmpegException(command, Kind.EXIT_CODE, exitCode,
                "종료 코드 " + exitCode, stderr, elapsedMs);
    }

    public static FfmpegException badOutput(List<String> command, String got,
                                            String stderr, long elapsedMs) {
        return new FfmpegException(command, Kind.BAD_OUTPUT, 0,
                "출력을 해석하지 못함 - '" + got + "'", stderr, elapsedMs);
    }

    private static String describe(List<String> command, String reason, String stderr, long elapsedMs) {
        String tail = (stderr == null || stderr.isBlank()) ? "(없음)" : System.lineSeparator() + stderr;

        return "FFmpeg 실패 - " + reason + " (" + elapsedMs + "ms)"
                + System.lineSeparator() + "명령: " + String.join(" ", command)
                + System.lineSeparator() + "stderr: " + tail;
    }

    public Kind getKind() {
        return kind;
    }

    public int getExitCode() {
        return exitCode;
    }

    public String getStderr() {
        return stderr;
    }
}
