package com.reelay.video.service;

import com.reelay.common.exception.BusinessException;
import com.reelay.common.exception.ErrorCode;
import com.reelay.common.storage.StorageService;
import com.reelay.pipeline.VideoPipeline;
import com.reelay.pipeline.crawl.service.NaverBlogCrawler;
import com.reelay.pipeline.manual.dto.ManualInput;
import com.reelay.pipeline.manual.service.ManualImageStore;
import com.reelay.pipeline.manual.service.ManualInputValidator;
import com.reelay.video.enums.TitleMode;
import com.reelay.video.dto.VideoJobResponse;
import com.reelay.video.entity.VideoJob;
import com.reelay.video.entity.VideoScene;
import com.reelay.video.repository.VideoJobRepository;
import com.reelay.video.repository.VideoJobStepRepository;
import com.reelay.video.repository.VideoSceneRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;

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
    private final ManualImageStore imageStore;

    public VideoJobService(JobProgressRecorder recorder,
                           VideoPipeline pipeline,
                           StorageService storage,
                           VideoJobRepository jobs,
                           VideoJobStepRepository steps,
                           VideoSceneRepository scenes,
                           ManualImageStore imageStore) {
        this.recorder = recorder;
        this.pipeline = pipeline;
        this.storage = storage;
        this.jobs = jobs;
        this.steps = steps;
        this.scenes = scenes;
        this.imageStore = imageStore;
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

    // 1-2. POST /api/videos/manual (블로그 글 없이 본문 직접 입력)
    // 입력 검증과 사진 열어 보기를 job 생성 전에 끝낸다 -> 잘못된 입력은 job 없이 400
    // 사진은 요청이 끝나면 임시 파일이 지워지므로 파이프라인에 넘기기 전에 job 폴더에 저장한다
    public UUID createManual(String rawBody, String rawTitleMode, String rawTitle, String rawTitleHook,
                             List<MultipartFile> files) {
        String body = ManualInputValidator.body(rawBody);
        TitleMode mode = ManualInputValidator.titleMode(rawTitleMode);
        String title = ManualInputValidator.title(mode, rawTitle);
        String hook = ManualInputValidator.titleHook(mode, rawTitleHook);
        List<BufferedImage> images = imageStore.decode(files);

        VideoJob job = recorder.createManualJob(title);
        UUID jobId = job.getId();
        try {
            storage.prepare(jobId);
            List<String> uris = imageStore.save(jobId, images);
            pipeline.runManualAsync(jobId, new ManualInput(mode, title, hook, body, uris));
        }
        catch (RuntimeException e) {
            // 사진 저장 실패 등. job 이 PENDING 으로 남지 않게 실패로 기록하고 500
            recorder.failJob(jobId, ErrorCode.INTERNAL_ERROR);
            throw e;
        }
        return jobId;
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
