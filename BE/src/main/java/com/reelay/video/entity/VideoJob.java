package com.reelay.video.entity;

import com.reelay.common.exception.ErrorCode;
import com.reelay.video.enums.JobStatus;
import com.reelay.video.enums.StepType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

// video_job 테이블 (V1__init.sql)
// 상태는 PENDING -> RUNNING -> DONE / FAILED 4개뿐. 세부 진행은 currentStep + VideoJobStep 으로 본다
@Entity
@Table(name = "video_job")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VideoJob {

    @Id
    @Column(name = "id", columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "source_url", nullable = false, length = 1024)
    private String sourceUrl;

    @Column(name = "blog_id", length = 100)
    private String blogId;

    @Column(name = "log_no", length = 50)
    private String logNo;

    @Column(name = "title", length = 500)
    private String title;

    // 영상 상단 두 줄 제목 (SCRIPT 단계 LLM 생성). title(블로그 원문)과 별개
    // 원문 title 은 FE 표시와 다운로드 파일명이 쓰므로 덮어쓰지 않는다
    @Column(name = "video_title", length = 50)
    private String videoTitle;        // 첫 줄: 핵심 키워드

    @Column(name = "video_title_hook", length = 50)
    private String videoTitleHook;    // 둘째 줄: 강조 문구

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private JobStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_step", length = 20)
    private StepType currentStep;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "output_path", length = 500)
    private String outputPath;

    @Column(name = "duration_ms")
    private Integer durationMs;

    // 회원 기능용. 1차는 항상 null
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static VideoJob create(String sourceUrl, String blogId, String logNo) {
        VideoJob job = new VideoJob();
        job.id = UUID.randomUUID(); // v4
        job.sourceUrl = sourceUrl;
        job.blogId = blogId;
        job.logNo = logNo;
        job.status = JobStatus.PENDING;
        LocalDateTime now = LocalDateTime.now();
        job.createdAt = now;
        job.updatedAt = now;
        return job;
    }

    public void startStep(StepType step) {
        this.status = JobStatus.RUNNING;
        this.currentStep = step;
        touch();
    }

    public void updateTitle(String title) {
        this.title = title;
        touch();
    }

    public void updateVideoTitle(String keyword, String hook) {
        this.videoTitle = keyword;
        this.videoTitleHook = hook;
        touch();
    }

    public void complete(String outputPath, int durationMs) {
        this.status = JobStatus.DONE;
        this.outputPath = outputPath;
        this.durationMs = durationMs;
        this.errorCode = null;
        this.errorMessage = null;
        touch();
    }

    public void fail(ErrorCode code) {
        this.status = JobStatus.FAILED;
        this.errorCode = code.name();
        this.errorMessage = code.getMessage();
        touch();
    }

    public boolean isDone() {
        return status == JobStatus.DONE;
    }

    public boolean isFinished() {
        return status == JobStatus.DONE || status == JobStatus.FAILED;
    }

    private void touch() {
        this.updatedAt = LocalDateTime.now();
    }
}
