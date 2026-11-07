package com.reelay.video.dto;

import com.reelay.common.exception.ErrorCode;
import com.reelay.video.entity.VideoJob;
import com.reelay.video.entity.VideoJobStep;
import com.reelay.video.entity.VideoScene;
import com.reelay.video.enums.JobStatus;
import com.reelay.video.enums.StepStatus;
import com.reelay.video.enums.StepType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

// GET /api/videos/{jobId} 응답. 진행 화면과 결과 화면이 이 응답 하나로 그려진다
// - steps[]  항상 6개 (진행 화면 체크리스트)
// - scenes[] DONE 일 때만, cutOrder 순
// - title    CRAWL 완료 후부터 (그 전 null)
// - error    FAILED 일 때만. 실패도 HTTP 200 (조회는 성공, 실패는 데이터)
public record VideoJobResponse(
        UUID jobId,
        JobStatus status,
        StepType currentStep,
        List<StepResponse> steps,
        String sourceUrl,
        String title,
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

    // retryable=true  -> FE "다시 시도" (같은 URL 로 새 job)
    // retryable=false -> FE "다른 글 주소 넣기" (비공개·삭제 글 등)
    // sourceUrl 을 함께 줘서 사용자가 원문을 직접 확인할 수 있게 한다
    public record ErrorBody(
            String code,
            String message,
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
