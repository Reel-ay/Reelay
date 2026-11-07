package com.reelay.pipeline;

import com.reelay.common.exception.BusinessException;
import com.reelay.common.exception.ErrorCode;
import com.reelay.pipeline.image.dto.CutImage;
import com.reelay.pipeline.render.enums.KenBurnsVariant;
import com.reelay.pipeline.script.dto.CutDraft;
import com.reelay.pipeline.tts.dto.CutAudio;
import com.reelay.video.dto.CutInput;
import com.reelay.video.enums.StepType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VideoPipelineAssembleTest {

    @Test
    @DisplayName("startMs 는 앞 컷 길이의 누적이고, 이미지 없는 컷은 imagePath=null")
    void assemble() {
        List<CutDraft> drafts = List.of(
                new CutDraft(0, "첫 문장", "첫", null, 0),
                new CutDraft(1, "둘째 문장", "둘째", null, null),
                new CutDraft(2, "셋째 문장", "셋째", null, 1));
        List<CutImage> images = List.of(
                new CutImage(0, Path.of("0.jpg"), KenBurnsVariant.ZOOM_IN_CENTER),
                new CutImage(1, null, null),
                new CutImage(2, Path.of("2.jpg"), KenBurnsVariant.PAN_LEFT_TO_RIGHT));
        // TTS 결과 순서가 섞여 와도 cutOrder 로 맞춘다
        List<CutAudio> audios = List.of(
                new CutAudio(2, Path.of("2.wav"), 3000),
                new CutAudio(0, Path.of("0.wav"), 2500),
                new CutAudio(1, Path.of("1.wav"), 4100));

        List<CutInput> cuts = VideoPipeline.assemble(drafts, images, audios);

        assertThat(cuts).extracting(CutInput::startMs).containsExactly(0, 2500, 6600);
        assertThat(cuts).extracting(CutInput::audioMs).containsExactly(2500, 4100, 3000);
        assertThat(cuts.get(1).imagePath()).isNull();
        assertThat(cuts.get(2).kenburns()).isEqualTo(KenBurnsVariant.PAN_LEFT_TO_RIGHT);
    }

    @Test
    @DisplayName("BusinessException 은 그 코드를, 그 밖의 예외는 단계별 기본 코드를 쓴다")
    void errorCode() {
        assertThat(VideoPipeline.errorCodeOf(StepType.CRAWL, new BusinessException(ErrorCode.CRAWL_EMPTY_BODY)))
                .isEqualTo(ErrorCode.CRAWL_EMPTY_BODY);
        assertThat(VideoPipeline.errorCodeOf(StepType.CRAWL, new IllegalStateException()))
                .isEqualTo(ErrorCode.CRAWL_FAILED);
        assertThat(VideoPipeline.errorCodeOf(StepType.TTS, new RuntimeException()))
                .isEqualTo(ErrorCode.TTS_FAILED);
        assertThat(VideoPipeline.errorCodeOf(StepType.CAPTION, new RuntimeException()))
                .isEqualTo(ErrorCode.RENDER_FAILED);
    }
}
