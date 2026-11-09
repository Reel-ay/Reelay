package com.reelay.pipeline.caption.service;

import com.reelay.common.ffmpeg.Ffmpeg;
import com.reelay.common.font.FontDir;
import com.reelay.common.storage.LocalStorageService;
import com.reelay.video.dto.CutInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AssSubtitleWriterTest {

    @TempDir
    Path tempDir;

    private LocalStorageService storage;
    private AssSubtitleWriter writer;
    private UUID jobId;

    @BeforeEach
    void setUp() {
        storage = new LocalStorageService(tempDir.toString());
        writer = new AssSubtitleWriter(storage, new FontDir());
        jobId = UUID.randomUUID();
        storage.prepare(jobId);
    }

    @Test
    @DisplayName("섹션 3개와 제목 포함 Dialogue 를 만든다")
    void writesStructure() throws IOException {
        Path ass = writer.write(jobId, "제목", List.of(
                cut(0, "첫 자막", 0, 3000),
                cut(1, "둘째 자막", 3000, 2400)));

        assertThat(ass).isEqualTo(storage.subtitlePath(jobId));

        String text = read(ass);
        assertThat(text).contains("[Script Info]", "[V4+ Styles]", "[Events]");
        assertThat(text).contains("PlayResX: 1080", "PlayResY: 1920");
        assertThat(text).contains("Style: title,", "Style: sub,");
        assertThat(dialogues(text)).hasSize(3);
    }

    @Test
    @DisplayName("startMs와 audioMs를 센티초 시각으로 바꾼다")
    void convertsTime() throws IOException {
        Path ass = writer.write(jobId, null, List.of(cut(0, "자막", 5400, 2400)));

        assertThat(dialogues(read(ass))).hasSize(1);
        assertThat(read(ass)).contains("Dialogue: 0,0:00:05.40,0:00:07.80,sub,");
    }

    @Test
    @DisplayName("제목은 영상 전체 구간에 깔린다")
    void titleSpansWholeVideo() throws IOException {
        Path ass = writer.write(jobId, "제목", List.of(
                cut(0, "가", 0, 3000),
                cut(1, "나", 3000, 2400)));

        assertThat(read(ass)).contains("Dialogue: 0,0:00:00.00,0:00:05.40,title,");
    }

    @Test
    @DisplayName("짧은 자막은 한 줄, 긴 자막은 두 줄로 접는다")
    void wrapsOnlyWhenTooWide() throws IOException {
        String shortText = "짧은 자막";
        String longText = "부여 서동공원에서 열리는 제23회 연꽃축제 안내입니다";

        String shortLine = subLine(writer.write(jobId, null, List.of(cut(0, shortText, 0, 3000))));
        assertThat(shortLine).doesNotContain("\\N");

        String longLine = subLine(writer.write(jobId, null, List.of(cut(0, longText, 0, 3000))));
        assertThat(longLine).contains("\\N");
    }

    @Test
    @DisplayName("중괄호를 바꿔서 ASS 태그로 읽히지 않게 한다")
    void sanitizesBraces() throws IOException {
        Path ass = writer.write(jobId, null, List.of(cut(0, "{강조} 자막", 0, 3000)));
        assertThat(subLine(ass)).contains("(강조) 자막");
    }

    @Test
    @DisplayName("ffmpeg가 자막을 읽어 영상에 굽는다")
    void ffmpegBurnsSubtitle() throws IOException {
        Ffmpeg ffmpeg = new Ffmpeg("ffmpeg", "ffprobe");
        FontDir fontDir = new FontDir();
        Path ass = writer.write(jobId, "제목입니다", List.of(cut(0, "자막입니다", 0, 1000)));

        Path plain = tempDir.resolve("plain.png");
        Path burned = tempDir.resolve("burned.png");
        List<String> source = List.of(
                "-f", "lavfi", "-i", "color=c=gray:s=1080x1920:d=1", "-frames:v", "1");

        ffmpeg.run(concat(source, List.of(plain.toString())), Duration.ofSeconds(30));
        ffmpeg.run(concat(source, List.of(
                "-vf", "ass=" + Ffmpeg.filterPath(ass)
                        + ":fontsdir=" + Ffmpeg.filterPath(fontDir.path()),
                burned.toString())), Duration.ofSeconds(30));
        assertThat(Files.size(burned)).isNotEqualTo(Files.size(plain));
    }

    private CutInput cut(int cutOrder, String caption, int startMs, int audioMs) {
        return new CutInput(cutOrder, "내레이션", caption, null, null, null, audioMs, startMs, null);
    }

    private String read(Path ass) throws IOException {
        return Files.readString(ass, StandardCharsets.UTF_8);
    }

    private List<String> dialogues(String text) {
        return text.lines().filter(line -> line.startsWith("Dialogue:")).toList();
    }

    private String subLine(Path ass) throws IOException {
        return dialogues(read(ass)).stream()
                .filter(line -> line.contains(",sub,"))
                .findFirst()
                .orElseThrow();
    }

    private List<String> concat(List<String> a, List<String> b) {
        return java.util.stream.Stream.concat(a.stream(), b.stream()).toList();
    }
}