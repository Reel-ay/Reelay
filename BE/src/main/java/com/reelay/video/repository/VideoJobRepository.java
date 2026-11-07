package com.reelay.video.repository;

import com.reelay.video.entity.VideoJob;
import com.reelay.video.enums.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface VideoJobRepository extends JpaRepository<VideoJob, UUID> {

    List<VideoJob> findByStatusIn(Collection<JobStatus> statuses);

    // 보관 기간이 지난 실패 job (FailedTmpCleaner)
    List<VideoJob> findByStatusAndUpdatedAtBefore(JobStatus status, LocalDateTime cutoff);
}
