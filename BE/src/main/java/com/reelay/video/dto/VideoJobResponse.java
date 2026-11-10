package com.reelay.video.dto;

import com.reelay.common.exception.ErrorCode;
import com.reelay.video.entity.VideoJob;
import com.reelay.video.entity.VideoJobStep;
import com.reelay.video.entity.VideoScene;
import com.reelay.video.enums.JobStatus;
import com.reelay.video.enums.StepStatus;
import com.reelay.video.enums.StepType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

// GET /api/videos/{jobId} 응답. 진행 화면과 결과 화면이 이 응답 하나로 그려진다
// - steps[]  항상 6개 (진행 화면 체크리스트)
// - scenes[] DONE 일 때만, cutOrder 순
// - title    CRAWL 완료 후부터 (그 전 null). 블로그 원문 제목
// - videoTitle SCRIPT 완료 후부터 (그 전 null). 영상 상단 두 줄 제목
// - error    FAILED 일 때만. 실패도 HTTP 200 (조회는 성공, 실패는 데이터)
public record VideoJobResponse(
        UUID jobId,
        JobStatus status,
        StepType currentStep,
        List<StepResponse> steps,
        String sourceUrl,
        @Schema(description = "블로그 원문 제목. 화면 표시·다운로드 파일명용. CRAWL 완료 전 null",
                example = "자동차정비기능사 합격률｜필기시험 난이도와 합격 공부방법")
        String title,
        @Schema(description = "영상 상단에 고정으로 뜨는 두 줄 제목 (LLM 생성). SCRIPT 완료 전이나 생성 실패 시 null"
                + " -> 이때 영상에는 원문 title 이 한 줄로 들어간다")
        VideoTitleBody videoTitle,
        Integer durationMs,
        String videoUrl,
        List<SceneResponse> scenes,
        ErrorBody error,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public record StepResponse(
            StepType step,
            StepStatus status,
            int attempt,
            LocalDateTime startedAt,
            LocalDateTime finishedAt
    ) {
        static StepResponse from(VideoJobStep s) {
            return new StepResponse(s.getStep(), s.getStatus(), s.getAttempt(), s.getStartedAt(), s.getFinishedAt());
        }
    }

    public record SceneResponse(
            int cutOrder,
            String narration,
            String caption,
            String highlight,
            String imageUrl,   // 이미지 없는 컷(텍스트 카드)은 null
            int startMs,
            int durationMs
    ) {
        static SceneResponse from(UUID jobId, VideoScene s) {
            String imageUrl = s.hasImage()
                    ? "/api/videos/" + jobId + "/scenes/" + s.getCutOrder() + "/image"
                    : null;
            return new SceneResponse(
                    s.getCutOrder(), s.getNarration(), s.getCaption(), s.getHighlight(), imageUrl,
                    nz(s.getStartMs()), nz(s.getAudioMs()));
        }
    }

    // 영상 화면 레이아웃 (9:16, 1080x1920)
    //   상단  : keyword (흰 글씨) / hook (노란 형광펜 박스)
    //   가운데: 블로그 사진 띠 y 490~1430 (사진 비율과 무관하게 고정, 빈 곳은 흐린 사진)
    //   사진 안 아래쪽: 컷 자막 (반투명 검정 박스, highlight 단어는 노란색)
    @Schema(description = "영상 상단 두 줄 제목. 첫 줄 흰 글씨, 둘째 줄 노란 형광펜 박스. 줄마다 공백 포함 8자 이내")
    public record VideoTitleBody(
            @Schema(description = "첫 줄: 블로그 핵심 키워드", example = "자동차정비기능사")
            String keyword,
            @Schema(description = "둘째 줄: LLM 이 추천한 유입 문구 (블로그 문장이 아님). null 이면 한 줄 제목",
                    example = "합격 지름길")
            String hook
    ) {
        static VideoTitleBody of(VideoJob job) {
            if (job.getVideoTitle() == null || job.getVideoTitle().isBlank()) {
                return null;
            }
            return new VideoTitleBody(job.getVideoTitle(), job.getVideoTitleHook());
        }
    }

    // retryable=true  -> FE "다시 시도" (같은 URL 로 새 job)
    // retryable=false -> FE "다른 글 주소 넣기" (비공개·삭제 글 등)
    // sourceUrl 을 함께 줘서 사용자가 원문을 직접 확인할 수 있게 한다
    public record ErrorBody(
            @Schema(description = "실패 원인 코드",
                    allowableValues = {"INVALID_URL", "SOURCE_URL_TOO_LONG", "CRAWL_EMPTY_BODY",
                            "JOB_NOT_FOUND", "SCENE_NOT_FOUND", "SCENE_HAS_NO_IMAGE", "FILE_NOT_FOUND",
                            "JOB_NOT_READY", "CRAWL_FAILED", "SCRIPT_FAILED", "TTS_FAILED",
                            "RENDER_FAILED", "SERVER_RESTARTED", "INTERNAL_ERROR"})
            String code,
            String message,
            @Schema(description = "true면 같은 URL로 재시도, false면 다른 글 필요")
            boolean retryable,
            String sourceUrl
    ) {
    }

    public static VideoJobResponse of(VideoJob job, List<VideoJobStep> steps, List<VideoScene> scenes) {
        UUID id = job.getId();
        boolean done = job.isDone();

        return new VideoJobResponse(
                id,
                job.getStatus(),
                job.getCurrentStep(),
                steps.stream().map(StepResponse::from).toList(),
                job.getSourceUrl(),
                job.getTitle(),
                VideoTitleBody.of(job),
                job.getDurationMs(),
                done ? "/api/videos/" + id + "/file" : null,
                done ? scenes.stream().map(s -> SceneResponse.from(id, s)).toList() : List.of(),
                errorOf(job),
                job.getCreatedAt(),
                job.getUpdatedAt()
        );
    }

    private static ErrorBody errorOf(VideoJob job) {
        if (job.getStatus() != JobStatus.FAILED || job.getErrorCode() == null) {
            return null;
        }
        boolean retryable = !ErrorCode.CRAWL_EMPTY_BODY.name().equals(job.getErrorCode())
                && !ErrorCode.INVALID_URL.name().equals(job.getErrorCode());
        return new ErrorBody(job.getErrorCode(), job.getErrorMessage(), retryable, job.getSourceUrl());
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }
}
