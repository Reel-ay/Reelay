package com.reelay.video.service;

import com.reelay.common.exception.ErrorCode;
import com.reelay.common.storage.StorageService;
import com.reelay.video.entity.VideoJob;
import com.reelay.video.enums.JobStatus;
import com.reelay.video.repository.VideoJobRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FailedTmpCleanerTest {

    private final VideoJobRepository jobs = mock(VideoJobRepository.class);
    private final StorageService storage = mock(StorageService.class);

    private static VideoJob failedJob() {
        VideoJob job = VideoJob.create("https://blog.naver.com/someone/123", "someone", "123");
        job.fail(ErrorCode.RENDER_FAILED);
        return job;
    }

    @Test
    @DisplayName("보관 기간이 지난 실패 job 의 tmp 를 지운다")
    void cleansExpiredFailedJobs() {
        VideoJob a = failedJob();
        VideoJob b = failedJob();
        when(jobs.findByStatusAndUpdatedAtBefore(eq(JobStatus.FAILED), any(LocalDateTime.class)))
                .thenReturn(List.of(a, b));

        int cleaned = new FailedTmpCleaner(jobs, storage, 3).clean();

        assertThat(cleaned).isEqualTo(2);
        verify(storage).cleanTmp(a.getId());
        verify(storage).cleanTmp(b.getId());
    }

    @Test
    @DisplayName("기준 시각은 지금으로부터 보관 기간(일) 전이다")
    void cutoffIsRetentionDaysAgo() {
        LocalDateTime before = LocalDateTime.now().minusDays(3);

        new FailedTmpCleaner(jobs, storage, 3).clean();

        verify(jobs).findByStatusAndUpdatedAtBefore(eq(JobStatus.FAILED),
                org.mockito.ArgumentMatchers.argThat(cutoff ->
                        !cutoff.isBefore(before) && cutoff.isBefore(before.plusMinutes(1))));
    }

    @Test
    @DisplayName("한 job 정리가 실패해도 나머지는 계속 지운다")
    void continuesOnError() {
        VideoJob a = failedJob();
        VideoJob b = failedJob();
        when(jobs.findByStatusAndUpdatedAtBefore(eq(JobStatus.FAILED), any(LocalDateTime.class)))
                .thenReturn(List.of(a, b));
        when(storage.tmpDir(a.getId())).thenReturn(Path.of("tmp"));
        doThrow(new RuntimeException("삭제 실패")).when(storage).cleanTmp(a.getId());

        int cleaned = new FailedTmpCleaner(jobs, storage, 3).clean();

        assertThat(cleaned).isEqualTo(1);
        verify(storage).cleanTmp(b.getId());
    }

    @Test
    @DisplayName("보관 기간이 0 이하이면 자동 삭제를 하지 않는다")
    void disabledWhenRetentionIsZero() {
        int cleaned = new FailedTmpCleaner(jobs, storage, 0).clean();

        assertThat(cleaned).isZero();
        verifyNoInteractions(jobs, storage);
        verify(storage, never()).cleanTmp(any());
    }
}
