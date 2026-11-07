package com.reelay.video.service;

import com.reelay.common.exception.BusinessException;
import com.reelay.common.exception.ErrorCode;
import com.reelay.common.storage.StorageService;
import com.reelay.pipeline.VideoPipeline;
import com.reelay.pipeline.crawl.service.NaverBlogCrawler;
import com.reelay.video.dto.VideoJobResponse;
import com.reelay.video.entity.VideoJob;
import com.reelay.video.entity.VideoScene;
import com.reelay.video.repository.VideoJobRepository;
import com.reelay.video.repository.VideoJobStepRepository;
import com.reelay.video.repository.VideoSceneRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@Service
public class VideoJobService {

    private final JobProgressRecorder recorder;
    private final VideoPipeline pipeline;
    private final StorageService storage;
    private final VideoJobRepository jobs;
    private final VideoJobStepRepository steps;
    private final VideoSceneRepository scenes;

    public VideoJobService(JobProgressRecorder recorder,
                           VideoPipeline pipeline,
                           StorageService storage,
                           VideoJobRepository jobs,
                           VideoJobStepRepository steps,
                           VideoSceneRepository scenes) {
        this.recorder = recorder;
        this.pipeline = pipeline;
        this.storage = storage;
        this.jobs = jobs;
        this.steps = steps;
        this.scenes = scenes;
    }

    // 1. POST /api/videos
    // URL 파싱 실패는 즉시 INVALID_URL(400). 성공하면 job 을 만들고 파이프라인을 비동기로 넘긴 뒤 바로 돌아간다
    // 같은 URL 을 두 번 보내면 job 도 2개 (중복 허용)
    // 이 메서드에 @Transactional 을 걸지 않는다: job 이 커밋된 뒤에 파이프라인 스레드가 읽어야 한다
    public UUID create(String sourceUrl) {
        String url = sourceUrl.strip();
        NaverBlogCrawler.Target target = NaverBlogCrawler.parseTarget(url);

        VideoJob job = recorder.createJob(url, target.blogId(), target.logNo());
        pipeline.runAsync(job.getId(), url);
        return job.getId();
    }

    // 2. GET /api/videos/{jobId}
    @Transactional(readOnly = true)
    public VideoJobResponse get(UUID jobId) {
        VideoJob job = findJob(jobId);
        return VideoJobResponse.of(
                job,
                steps.findByJobIdOrderByStepOrderAsc(jobId),
                job.isDone() ? scenes.findByJobIdOrderByCutOrderAsc(jobId) : List.of());
    }

    // 3. GET /api/videos/{jobId}/file
    @Transactional(readOnly = true)
    public VideoFile videoFile(UUID jobId) {
        VideoJob job = findJob(jobId);
        if (!job.isDone() || job.getOutputPath() == null) {
            throw new BusinessException(ErrorCode.JOB_NOT_READY);
        }
        Path path = Path.of(job.getOutputPath());
        if (!storage.isFile(path)) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        return new VideoFile(path, DownloadFileName.of(job.getTitle(), jobId));
    }

    // 4. GET /api/videos/{jobId}/scenes/{cutOrder}/image
    @Transactional(readOnly = true)
    public Path sceneImage(UUID jobId, int cutOrder) {
        findJob(jobId);
        VideoScene scene = scenes.findByJobIdAndCutOrder(jobId, cutOrder)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCENE_NOT_FOUND));
        if (!scene.hasImage()) {
            throw new BusinessException(ErrorCode.SCENE_HAS_NO_IMAGE);
        }
        Path path = Path.of(scene.getImagePath());
        if (!storage.isFile(path)) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        return path;
    }

    private VideoJob findJob(UUID jobId) {
        return jobs.findById(jobId).orElseThrow(() -> new BusinessException(ErrorCode.JOB_NOT_FOUND));
    }

    public record VideoFile(Path path, String downloadName) {
    }
}
