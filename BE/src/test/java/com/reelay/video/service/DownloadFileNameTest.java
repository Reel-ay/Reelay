package com.reelay.video.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DownloadFileNameTest {

    private final UUID jobId = UUID.fromString("1234abcd-0000-4000-8000-000000000000");

    @Test
    @DisplayName("경로 문자와 이모지를 걸러낸다")
    void sanitize() {
        String name = DownloadFileName.of("정보통신기사 🔥 합격/불합격? 총정리!", jobId);

        assertThat(name).isEqualTo("정보통신기사 합격불합격 총정리!.mp4");
    }

    @Test
    @DisplayName("제목이 없거나 다 걸러지면 reelay-{jobId 앞 8자}.mp4")
    void fallback() {
        assertThat(DownloadFileName.of(null, jobId)).isEqualTo("reelay-1234abcd.mp4");
        assertThat(DownloadFileName.of("🔥🔥", jobId)).isEqualTo("reelay-1234abcd.mp4");
    }

    @Test
    @DisplayName("너무 긴 제목은 80자로 자른다")
    void truncate() {
        String name = DownloadFileName.of("가".repeat(200), jobId);

        assertThat(name).isEqualTo("가".repeat(80) + ".mp4");
    }
}
