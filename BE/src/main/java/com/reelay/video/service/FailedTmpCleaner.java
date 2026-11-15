package com.reelay.video.service;

import com.reelay.common.storage.StorageService;
import com.reelay.video.entity.VideoJob;
import com.reelay.video.enums.JobStatus;
import com.reelay.video.repository.VideoJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

// 실패한 job 의 tmp 보관 정책
// VideoPipeline 은 실패 시 원인 추적을 위해 tmp(cutN.mp4, joined.mp4 등)를 남긴다.
// 그대로 두면 디스크가 차므로, FAILED 이고 보관 기간이 지난 job 의 tmp 만 지운다.
// - 서버 기동 직후 1회 + 매일 04:30 (reelay.storage.failed-tmp-cleanup-cron)
// - 보관 기간: reelay.storage.failed-tmp-retention-days (기본 3일, 0 이하이면 자동 삭제 끔)
// - images/, audio/, out.mp4 는 건드리지 않는다 (이미지 API 등에서 쓴다)
@Component
public class FailedTmpCleaner {

    private static final Logger log = LoggerFactory.getLogger(FailedTmpCleaner.class);

    private final VideoJobRepository jobs;
    private final StorageService storage;
    private final int retentionDays;

    public FailedTmpCleaner(VideoJobRepository jobs,
                            StorageService storage,
                            @Value("${reelay.storage.failed-tmp-retention-days:3}") int retentionDays) {
        this.jobs = jobs;
        this.storage = storage;
        this.retentionDays = retentionDays;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void cleanOnStartup() {
        clean();
    }

    @Scheduled(cron = "${reelay.storage.failed-tmp-cleanup-cron:0 30 4 * * *}")
    public void cleanDaily() {
        clean();
    }

    // 지운 job 수를 돌려준다
    public int clean() {
        if (retentionDays <= 0) {
            return 0;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        List<VideoJob> expired = jobs.findByStatusAndUpdatedAtBefore(JobStatus.FAILED, cutoff);

        int cleaned = 0;
        for (VideoJob job : expired) {
            UUID jobId = job.getId();
            try {
                storage.cleanTmp(jobId);
                cleaned++;
            }
            catch (RuntimeException e) {
                // 한 job 정리 실패가 나머지 정리를 막지 않게 한다
                log.warn("[{}] 실패 job tmp 정리 실패 - {}", jobId, storage.tmpDir(jobId), e);
            }
        }
        if (cleaned > 0) {
            log.info("보관 기간 {}일이 지난 실패 job {}개의 tmp 를 정리했습니다", retentionDays, cleaned);
        }
        return cleaned;
    }
}
