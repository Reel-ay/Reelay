package com.reelay.video.entity;

import com.reelay.video.enums.StepStatus;
import com.reelay.video.enums.StepType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

// video_job_step 테이블. job 당 6행을 생성 시점에 미리 만든다 (조회 시 steps[] 항상 6개)
@Entity
@Table(name = "video_job_step")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VideoJobStep {

    private static final int MAX_ERROR_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID jobId;

    @Enumerated(EnumType.STRING)
    @Column(name = "step", nullable = false, length = 20)
    private StepType step;

    // 컬럼이 TINYINT 라 타입을 맞춰야 ddl-auto=validate 를 통과한다
    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "step_order", nullable = false)
    private int stepOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StepStatus status;

    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "attempt", nullable = false)
    private int attempt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "error_message", length = MAX_ERROR_LENGTH)
    private String errorMessage;

    public static VideoJobStep pending(UUID jobId, StepType step) {
        VideoJobStep s = new VideoJobStep();
        s.jobId = jobId;
        s.step = step;
        s.stepOrder = step.getOrder();
        s.status = StepStatus.PENDING;
        s.attempt = 0;
        return s;
    }

    public void start() {
        this.status = StepStatus.RUNNING;
        this.attempt = this.attempt + 1;
        this.startedAt = LocalDateTime.now();
        this.finishedAt = null;
        this.errorMessage = null;
    }

    public void done() {
        this.status = StepStatus.DONE;
        this.finishedAt = LocalDateTime.now();
    }

    // errorMessage 는 로그 확인용 내부 사유. API 응답에는 내보내지 않는다
    public void fail(String reason) {
        this.status = StepStatus.FAILED;
        this.finishedAt = LocalDateTime.now();
        this.errorMessage = truncate(reason);
    }

    private static String truncate(String s) {
        if (s == null || s.length() <= MAX_ERROR_LENGTH) {
            return s;
        }
        return s.substring(0, MAX_ERROR_LENGTH);
    }
}
