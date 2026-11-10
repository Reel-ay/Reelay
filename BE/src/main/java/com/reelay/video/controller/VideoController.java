package com.reelay.video.controller;

import com.reelay.common.exception.BusinessException;
import com.reelay.common.exception.ErrorCode;
import com.reelay.video.dto.CreateVideoRequest;
import com.reelay.video.dto.CreateVideoResponse;
import com.reelay.video.dto.VideoJobResponse;
import com.reelay.video.service.VideoJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.reelay.video.dto.VideoJobResponse.ErrorBody;
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
@Tag(name = "영상", description = "네이버 블로그 글을 9:16 쇼츠로 변환")
@RestController
@RequestMapping("/api/videos")
public class VideoController {

    private static final MediaType VIDEO_MP4 = MediaType.parseMediaType("video/mp4");

    private final VideoJobService videoJobService;

    public VideoController(VideoJobService videoJobService) {
        this.videoJobService = videoJobService;
    }

    // 1. 영상 생성 요청 -> 202 + jobId. 이후 FE 는 2초 간격으로 GET 폴링
    @Operation(summary = "영상 생성 요청",
            description = "202+jobId 즉시 반환, 변환은 백그라운드. Location헤더에 조회 주소. 2초 간격 폴링, 완료까지 50~60초")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "접수됨"),
            @ApiResponse(responseCode = "400", description = "INVALID_URL, SOURCE_URL_TOO_LONG",
                    content = @Content(schema = @Schema(implementation = ErrorBody.class)))
    })
    @PostMapping
    public ResponseEntity<CreateVideoResponse> create(@Valid @RequestBody CreateVideoRequest request) {
        UUID jobId = videoJobService.create(request.sourceUrl());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .location(URI.create("/api/videos/" + jobId))
                .body(new CreateVideoResponse(jobId));
    }

    // 2. 상태·결과·실패 조회. 실패한 job 도 200
    @Operation(summary = "상태·결과 조회",
            description = "steps는 항상 6개, scenes는 DONE일 때만. 변환 실패도 200 -> status와 error로 판단할 것. StepStatus에 FAILED 없음\n\n"
                    + "title = 블로그 원문 제목(화면 표시·파일명), videoTitle = 영상 상단 두 줄 제목"
                    + "(keyword 흰 글씨 / hook 노란 형광펜, SCRIPT 완료 후부터, 실패 시 null)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "실패한 job도 여기로"),
            @ApiResponse(responseCode = "404", description = "JOB_NOT_FOUND. UUID 형식 아닌 경우 포함",
                    content = @Content(schema = @Schema(implementation = ErrorBody.class)))
    })
    @GetMapping("/{jobId}")
    public VideoJobResponse get(@PathVariable String jobId) {
        return videoJobService.get(parseJobId(jobId));
    }

    // 3. 영상 파일
    // Resource 로 돌려주면 Spring MVC 가 Range 요청을 처리해 206 을 준다 (플레이어 구간 탐색, 재다운로드 방지)
    // download=true 면 첨부파일로, 파일명은 글 제목 (RFC 5987 filename*=UTF-8'')
    @Operation(summary = "영상 파일",
            description = "Range 지원 -> video 태그에 꽂으면 구간 탐색 가능. download=true면 첨부파일, 파일명은 글 제목")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "전체"),
            @ApiResponse(responseCode = "206", description = "Range 부분 응답"),
            @ApiResponse(responseCode = "404", description = "JOB_NOT_FOUND, FILE_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ErrorBody.class))),
            @ApiResponse(responseCode = "409", description = "JOB_NOT_READY. 렌더 미완료",
                    content = @Content(schema = @Schema(implementation = ErrorBody.class)))
    })
    @GetMapping("/{jobId}/file")
    public ResponseEntity<Resource> file(@PathVariable String jobId,
                                         @Parameter(description = "true면 첨부파일로 내려받기")
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
    @Operation(summary = "컷 이미지",
            description = "네이버가 외부 Referer를 403으로 막음 -> 원본 URL 직접 사용 금지. 텍스트 카드 컷은 404")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "jpg"),
            @ApiResponse(responseCode = "404",
                    description = "SCENE_HAS_NO_IMAGE(텍스트 카드), SCENE_NOT_FOUND, JOB_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ErrorBody.class)))
    })
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
