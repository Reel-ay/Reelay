package com.reelay.pipeline.render.service;

import com.reelay.common.ffmpeg.Ffmpeg;
import com.reelay.common.brand.BrandAssets;
import com.reelay.common.font.FontDir;
import com.reelay.common.storage.LocalStorageService;
import com.reelay.pipeline.caption.service.AssSubtitleWriter;
import com.reelay.pipeline.render.enums.KenBurnsVariant;
import com.reelay.video.dto.CutInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class FfmpegRendererTest {

    private static final Duration SETUP_TIMEOUT = Duration.ofSeconds(30);

    @TempDir
    Path tempDir;

    private Ffmpeg ffmpeg;
    private LocalStorageService storage;
    private FfmpegRenderer renderer;
    private UUID jobId;

    @BeforeEach
    void setUp() {
        ffmpeg = new Ffmpeg("ffmpeg", "ffprobe");
        storage = new LocalStorageService(tempDir.toString());
        // storage = new LocalStorageService("C:/Reelay_work/render/see");
        renderer = new FfmpegRenderer(ffmpeg, storage, new FontDir(), new BrandAssets());
        jobId = UUID.randomUUID();
        storage.prepare(jobId);
    }

    @Test
    @DisplayName("컷 3개를 합쳐 영상과 오디오 길이가 일치하는 mp4를 만든다")
    void rendersCutsIntoSingleMp4() {
        List<CutInput> cuts = List.of(
                cut(0, "790x1120", "3"),
                cut(1, "900x410", "2.4"),
                cut(2, "966x966", "4"));

        Path out = renderer.render(jobId, cuts, null);

        assertThat(out).isEqualTo(storage.outputPath(jobId));
        assertThat(Files.exists(out)).isTrue();
        assertThat(ffmpeg.probeSeconds(out)).isEqualTo(9.4, offset(0.05));
    }

    @Test
    @DisplayName("자막을 넘기면 번인되고 길이는 그대로다")
    void burnsSubtitleWithoutChangingLength() {
        List<CutInput> cuts = List.of(
                cut(0, "790x1120", "3", "첫 번째 자막입니다", 0),
                cut(1, "900x410", "2.4", "두 번째 자막은 길어서 두 줄로 접힙니다", 3000),
                cut(2, "966x966", "4", "세 번째 자막", 5400));

        Path subs = new AssSubtitleWriter(storage, new FontDir())
                .write(jobId, "테스트 제목", cuts);

        Path out = renderer.render(jobId, cuts, subs);

        assertThat(Files.exists(out)).isTrue();
        assertThat(ffmpeg.probeSeconds(out)).isEqualTo(9.4, offset(0.05));
    }

    @Test
    @DisplayName("사진 컷과 텍스트 카드가 섞여도 검정이 유지된다")
    void keepsBlackWhenPhotoAndCardMixed() {
        List<CutInput> cuts = List.of(
                cut(0, "966x724", "1"),
                textCard(1, "1"),
                cut(2, "966x724", "1"));

        Path out = renderer.render(jobId, cuts, null);

        // 1.5초 = 카드 구간. 좌상단은 사진 컷의 레터박스와 같은 검정이어야 한다
        assertThat(blackLevelAt(out, 0.5)).isZero();
        assertThat(blackLevelAt(out, 1.5)).isZero();
    }

    @Test
    @DisplayName("컷이 바뀌어도 마지막 컷까지 로고가 남는다")
    void keepsLogoUntilLastCut() {
        List<CutInput> cuts = List.of(
                cut(0, "790x1120", "3", "첫 번째 자막", 0),
                cut(1, "900x410", "2.4", "두 번째 자막", 3000),
                cut(2, "966x966", "4", "세 번째 자막", 5400));

        Path subs = new AssSubtitleWriter(storage, new FontDir())
                .write(jobId, "테스트 제목", cuts);

        Path out = renderer.render(jobId, cuts, subs);

        assertThat(logoLevelAt(out, 1.0)).isPositive();
        assertThat(logoLevelAt(out, 8.0)).isPositive();
    }

    private int logoLevelAt(Path video, double seconds) {
        Path png = tempDir.resolve("logo" + (int) (seconds * 1000) + ".png");
        ffmpeg.run(List.of("-ss", String.valueOf(seconds), "-i", video.toString(),
                "-frames:v", "1", "-vf", "crop=360:70:360:1400,scale=1:1",
                png.toString()), SETUP_TIMEOUT);

        try {
            BufferedImage frame = ImageIO.read(png.toFile());
            return new Color(frame.getRGB(0, 0)).getRed();
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private CutInput cut(int cutOrder, String imageSize, String seconds) {
        return cut(cutOrder, imageSize, seconds, "자막", 0);
    }

    private CutInput cut(int cutOrder, String imageSize, String seconds, String caption, int startMs) {
        Path image = storage.imagePath(jobId, cutOrder);
        ffmpeg.run(List.of("-f", "lavfi", "-i", "testsrc2=size=" + imageSize + ":d=1",
                "-frames:v", "1", image.toString()), SETUP_TIMEOUT);

        Path audio = storage.audioPath(jobId, cutOrder);
        ffmpeg.run(List.of("-f", "lavfi", "-i", "sine=f=440:d=" + seconds,
                "-ar", "24000", "-ac", "1", audio.toString()), SETUP_TIMEOUT);

        return new CutInput(cutOrder, "내레이션", caption, "자막",
                image, audio, ffmpeg.probeMillis(audio), startMs, KenBurnsVariant.ZOOM_IN_CENTER);
    }

    private CutInput textCard(int cutOrder, String seconds) {
        Path audio = storage.audioPath(jobId, cutOrder);
        ffmpeg.run(List.of("-f", "lavfi", "-i", "sine=f=440:d=" + seconds,
                "-ar", "24000", "-ac", "1", audio.toString()), SETUP_TIMEOUT);

        return new CutInput(cutOrder, "내레이션", "자막", "자막",
                null, audio, ffmpeg.probeMillis(audio), 0, null);
    }

    private int blackLevelAt(Path video, double seconds) {
        Path png = tempDir.resolve("probe" + (int) (seconds * 1000) + ".png");
        ffmpeg.run(List.of("-ss", String.valueOf(seconds), "-i", video.toString(),
                "-frames:v", "1", "-vf", "crop=200:200:0:0,scale=1:1",
                png.toString()), SETUP_TIMEOUT);

        try {
            BufferedImage frame = ImageIO.read(png.toFile());
            return new Color(frame.getRGB(0, 0)).getRed();
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}