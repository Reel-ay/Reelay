package com.reelay.video.repository;

import com.reelay.video.entity.VideoJobStep;
import com.reelay.video.enums.StepType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VideoJobStepRepository extends JpaRepository<VideoJobStep, Long> {

    List<VideoJobStep> findByJobIdOrderByStepOrderAsc(UUID jobId);

    Optional<VideoJobStep> findByJobIdAndStep(UUID jobId, StepType step);
}
