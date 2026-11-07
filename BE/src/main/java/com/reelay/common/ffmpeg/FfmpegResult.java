package com.reelay.common.ffmpeg;

// 종료코드 0 - 실행 결과
// stdout : ffprobe 조회 결과
// stderr : 경고(픽셀 포맷 변환, 비표준 해상도 등)
public record FfmpegResult(
        String stdout,
        String stderr,
        long elapsedMs
) {
    public String firstLine() {
        int newline = stdout.indexOf('\n');
        return (newline<0 ? stdout : stdout.substring(0, newline)).trim();
    }
}
