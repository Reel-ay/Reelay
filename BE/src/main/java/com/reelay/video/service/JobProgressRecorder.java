package com.reelay.video.service;

import com.reelay.common.exception.BusinessException;
import com.reelay.common.exception.ErrorCode;
import com.reelay.video.dto.VideoTitle;
import com.reelay.video.entity.VideoJob;
import com.reelay.video.entity.VideoJobStep;
import com.reelay.video.entity.VideoScene;
import com.reelay.video.enums.JobStatus;
import com.reelay.video.enums.StepStatus;
import com.reelay.video.enums.StepType;
import com.reelay.video.repository.VideoJobRepository;
import com.reelay.video.repository.VideoJobStepRepository;
import com.reelay.video.repository.VideoSceneRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

// job·단계 상태를 DB 에 기록한다
// 메서드 하나 = 트랜잭션 하나. 파이프라인이 단계마다 바로 커밋해야 폴링(GET)에서 진행 상황이 보인다
@Component
public class JobProgressRecorder {

    private final VideoJobRepository jobs;
    private final VideoJobStepRepository steps;
    private final VideoSceneRepository scenes;

    public JobProgressRecorder(VideoJobRepository jobs, VideoJobStepRepository steps, VideoSceneRepository scenes) {
        this.jobs = jobs;
        this.steps = steps;
        this.scenes = scenes;
    }

    // job 1행 + 단계 6행(PENDING)을 함께 만든다 -> 조회 시 steps[] 가 항상 6개
    @Transactional
    public VideoJob createJob(String sourceUrl, String blogId, String logNo) {
        VideoJob job = jobs.save(VideoJob.create(sourceUrl, blogId, logNo));
        for (StepType type : StepType.values()) {
            steps.save(VideoJobStep.pending(job.getId(), type));
        }
        return job;
    }

    @Transactional
    public void startStep(UUID jobId, StepType type) {
        getJob(jobId).startStep(type);
        getStep(jobId, type).start();
    }

    @Transactional
    public void finishStep(UUID jobId, StepType type) {
        getStep(jobId, type).done();
    }

    @Transactional
    public void updateTitle(UUID jobId, String title) {
        getJob(jobId).updateTitle(title);
    }

    @Transactional
    public void updateVideoTitle(UUID jobId, VideoTitle videoTitle) {
        if (videoTitle == null) {
            return;
        }
        getJob(jobId).updateVideoTitle(videoTitle.keyword(), videoTitle.hook());
    }

    // 단계 실패 = job 전체 실패 (중간 산출물에서 재개하지 않는다)
    @Transactional
    public void failStep(UUID jobId, StepType type, ErrorCode code, String reason) {
        getStep(jobId, type).fail(reason);
        getJob(jobId).fail(code);
    }

    // 단계 밖에서 터진 예외용
    @Transactional
    public void failJob(UUID jobId, ErrorCode code) {
        getJob(jobId).fail(code);
    }

    @Transactional
    public void complete(UUID jobId, String outputPath, int durationMs, List<VideoScene> sceneRows) {
        scenes.saveAll(sceneRows);
        getJob(jobId).complete(outputPath, durationMs);
    }

    // 서버 기동 시 끝나지 않은 job 정리. 1차는 로컬 단일 인스턴스라 "유일한 워커"가 보장되어 안전하다
    // 다중 인스턴스로 배포하면 남의 job 을 죽이므로 배포 결정 때 다시 볼 것
    @Transactional
    public int failUnfinished(ErrorCode code) {
        List<VideoJob> unfinished = jobs.findByStatusIn(List.of(JobStatus.PENDING, JobStatus.RUNNING));
        for (VideoJob job : unfinished) {
            for (VideoJobStep step : steps.findByJobIdOrderByStepOrderAsc(job.getId())) {
                if (step.getStatus() == StepStatus.RUNNING) {
                    step.fail(code.name());
                }
            }
            job.fail(code);
        }
        return unfinished.size();
    }

    private VideoJob getJob(UUID jobId) {
        return jobs.findById(jobId).orElseThrow(() -> new BusinessException(ErrorCode.JOB_NOT_FOUND));
    }

    private VideoJobStep getStep(UUID jobId, StepType type) {
        return steps.findByJobIdAndStep(jobId, type)
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_NOT_FOUND));
    }
}
