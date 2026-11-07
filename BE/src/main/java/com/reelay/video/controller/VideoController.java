package com.reelay.video.controller;

import com.reelay.common.exception.BusinessException;
import com.reelay.common.exception.ErrorCode;
import com.reelay.video.dto.CreateVideoRequest;
import com.reelay.video.dto.CreateVideoResponse;
import com.reelay.video.dto.VideoJobResponse;
import com.reelay.video.service.VideoJobService;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

// 영상 API 4개. 인증 없음 - jobId(UUID v4)를 아는 사람이 소유자
@RestController
@RequestMapping("/api/videos")
public class VideoController {

    private static final MediaType VIDEO_MP4 = MediaType.parseMediaType("video/mp4");

    private final VideoJobService videoJobService;

    public VideoController(VideoJobService videoJobService) {
        this.videoJobService = videoJobService;
    }

    // 1. 영상 생성 요청 -> 202 + jobId. 이후 FE 는 2초 간격으로 GET 폴링
    @PostMapping
    public ResponseEntity<CreateVideoResponse> create(@Valid @RequestBody CreateVideoRequest request) {
        UUID jobId = videoJobService.create(request.sourceUrl());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .location(URI.create("/api/videos/" + jobId))
                .body(new CreateVideoResponse(jobId));
    }

    // 2. 상태·결과·실패 조회. 실패한 job 도 200
    @GetMapping("/{jobId}")
    public VideoJobResponse get(@PathVariable String jobId) {
        return videoJobService.get(parseJobId(jobId));
    }

    // 3. 영상 파일
    // Resource 로 돌려주면 Spring MVC 가 Range 요청을 처리해 206 을 준다 (플레이어 구간 탐색, 재다운로드 방지)
    // download=true 면 첨부파일로, 파일명은 글 제목 (RFC 5987 filename*=UTF-8'')
    @GetMapping("/{jobId}/file")
    public ResponseEntity<Resource> file(@PathVariable String jobId,
                                         @RequestParam(defaultValue = "false") boolean download) {
        VideoJobService.VideoFile file = videoJobService.videoFile(parseJobId(jobId));

        ContentDisposition disposition = (download ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(file.downloadName(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(VIDEO_MP4)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .body(new FileSystemResource(file.path()));
    }

    // 4. 컷 이미지 (B 가 가공해 둔 jpg)
    // 네이버 이미지 서버가 외부 Referer 를 403 으로 막아서 FE 가 원본 URL 을 직접 쓸 수 없다 -> 백엔드가 서빙
    @GetMapping("/{jobId}/scenes/{cutOrder}/image")
    public ResponseEntity<Resource> sceneImage(@PathVariable String jobId, @PathVariable int cutOrder) {
        Path path = videoJobService.sceneImage(parseJobId(jobId), cutOrder);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .body(new FileSystemResource(path));
    }

    // UUID 형식이 아니면 없는 job 으로 본다
    private static UUID parseJobId(String raw) {
        try {
            return UUID.fromString(raw);
        }
        catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.JOB_NOT_FOUND);
        }
    }
}
