package com.reelay.video.entity;

import com.reelay.pipeline.render.enums.KenBurnsVariant;
import com.reelay.video.dto.CutInput;
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

import java.util.UUID;

// video_scene 테이블. 컷 = 자막 1줄 = TTS 1문장 = 이미지 1장
// 영상이 완성(DONE)된 뒤 한 번에 저장한다
@Entity
@Table(name = "video_scene")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VideoScene {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID jobId;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "cut_order", nullable = false)
    private int cutOrder;

    @Column(name = "narration", nullable = false, length = 500)
    private String narration;

    @Column(name = "caption", nullable = false, length = 200)
    private String caption;

    @Column(name = "highlight", length = 50)
    private String highlight;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "source_image_index")
    private Integer sourceImageIndex;

    @Column(name = "image_path", length = 500)
    private String imagePath;

    @Column(name = "audio_path", length = 500)
    private String audioPath;

    @Column(name = "audio_ms")
    private Integer audioMs;

    @Column(name = "start_ms")
    private Integer startMs;

    @Enumerated(EnumType.STRING)
    @Column(name = "kenburns", length = 30)
    private KenBurnsVariant kenburns;

    public static VideoScene of(UUID jobId, CutInput cut, Integer sourceImageIndex) {
        VideoScene s = new VideoScene();
        s.jobId = jobId;
        s.cutOrder = cut.cutOrder();
        s.narration = cut.narration();
        s.caption = cut.caption();
        s.highlight = cut.highlight();
        s.sourceImageIndex = sourceImageIndex;
        s.imagePath = cut.imagePath() == null ? null : cut.imagePath().toString();
        s.audioPath = cut.audioPath() == null ? null : cut.audioPath().toString();
        s.audioMs = cut.audioMs();
        s.startMs = cut.startMs();
        s.kenburns = cut.kenburns();
        return s;
    }

    public boolean hasImage() {
        return imagePath != null;
    }
}
