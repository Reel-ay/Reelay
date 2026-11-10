package com.reelay.pipeline.caption.service;

import com.reelay.common.ffmpeg.Ffmpeg;
import com.reelay.common.font.FontDir;
import com.reelay.common.storage.LocalStorageService;
import com.reelay.video.dto.CutInput;
import com.reelay.video.dto.VideoTitle;
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
import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class AssSubtitleWriterTest {

    @TempDir
    Path tempDir;

    private LocalStorageService storage;
    private AssSubtitleWriter writer;
    private UUID jobId;

    private static final Pattern FS = Pattern.compile("\\\\fs(\\d+)");

    @BeforeEach
    void setUp() {
        storage = new LocalStorageService(tempDir.toString());
        writer = new AssSubtitleWriter(storage, new FontDir());
        jobId = UUID.randomUUID();
        storage.prepare(jobId);
    }

    @Test
    @DisplayName("두 줄 제목: 첫 줄은 흰 글씨, 둘째 줄은 형광펜 박스 스타일로 같은 크기의 Dialogue 2개를 만든다")
    void writesTwoLineTitle() throws IOException {
        Path ass = writer.writeWithTitle(jobId, new VideoTitle("클로드 사용법", "TOP9"),
                List.of(cut(0, "자막", 0, 3000)));

        String text = read(ass);
        assertThat(text).contains("Style: titlehook,");

        String first = dialogues(text).stream().filter(l -> l.contains(",title,")).findFirst().orElseThrow();
        String second = dialogues(text).stream().filter(l -> l.contains(",titlehook,")).findFirst().orElseThrow();
        assertThat(first).endsWith("클로드 사용법");
        assertThat(second).endsWith("TOP9");
        // 글자색을 바꾸는 방식이 아니라 박스 스타일로 강조한다
        assertThat(second).doesNotContain("\\c&H");

        Matcher a = FS.matcher(first);
        Matcher b = FS.matcher(second);
        assertThat(a.find()).isTrue();
        assertThat(b.find()).isTrue();
        assertThat(a.group(1)).isEqualTo(b.group(1));
        assertThat(Integer.parseInt(a.group(1))).isLessThanOrEqualTo(116);
    }

    @Test
    @DisplayName("둘째 줄이 없으면 기존 한 줄 제목과 같다")
    void twoLineTitleWithoutHookFallsBack() throws IOException {
        List<CutInput> cuts = List.of(cut(0, "자막", 0, 3000));
        String single = read(writer.write(jobId, "자동차정비기능사합격률｜필기시험 난이도", cuts));
        String viaTitle = read(writer.writeWithTitle(jobId,
                VideoTitle.singleLine("자동차정비기능사합격률｜필기시험 난이도"), cuts));

        assertThat(viaTitle).isEqualTo(single);
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

    @Test
    @DisplayName("제목은 구분자 앞까지만 쓴다")
    void usesHeadlineBeforeSeparator() throws IOException {
        Path ass = writer.write(jobId, "자동차정비기능사합격률｜필기시험 난이도와 합격 공부방법",
                List.of(cut(0, "자막", 0, 3000)));

        String title = titleLine(ass).replace("\\N", "");
        assertThat(title).contains("자동차정비기능사합격률");
        assertThat(title).doesNotContain("필기시험");
    }

    @Test
    @DisplayName("구분자가 없으면 제목을 버리지 않는다")
    void keepsWholeTitleWithoutSeparator() throws IOException {
        Path ass = writer.write(jobId, "포스코합격률 포스코 필기시험 임박! 포스코 필기 인강으로 단기 완벽 대비",
                List.of(cut(0, "자막", 0, 3000)));

        String title = titleLine(ass).replace("\\N", "");
        assertThat(title).contains("포스코합격률");
        assertThat(title).contains("단기 완벽 대비");
    }

    @Test
    @DisplayName("공백 없는 제목은 쪼개지 않고 한 줄로 둔다")
    void keepsSingleWordTitleOnOneLine() throws IOException {
        Path ass = writer.write(jobId, "자동차정비기능사합격률｜필기시험 난이도와 합격 공부방법",
                List.of(cut(0, "자막", 0, 3000)));

        assertThat(titleLine(ass)).doesNotContain("\\N");
    }

    @Test
    @DisplayName("짧은 제목은 공백이 있어도 한 줄로 둔다")
    void keepsShortTitleOnOneLine() throws IOException {
        Path ass = writer.write(jobId, "합격률 공부방법", List.of(cut(0, "자막", 0, 3000)));

        String title = titleLine(ass);
        assertThat(title).doesNotContain("\\N");
        assertThat(title).contains("합격률 공부방법");
    }

    @Test
    @DisplayName("실제 블로그 제목이 모두 세이프존 폭 안에 들어온다")
    void everyRealTitleFitsSafeZone() throws IOException {
        List<String> titles = List.of(
                "자동차정비기능사합격률｜필기시험 난이도와 합격 공부방법",
                "전산응용기계제도기능사시험후기｜필기·실기 준비와 공부방법",
                "워드프로세서합격률 높이는 공부방법 | 시험 일정 및 합격률, 강의까지 모두 추천",
                "식물보호산업기사교재는 이렇게 고르세요 필기실기 일정과 공부 팁",
                "인디자인독학 실무자들은 이렇게 공부해요",
                "지역농협6급교재｜NCS 독학 가능할까? 합격 준비 전 알아둘 공부방법");

        for(String title : titles) {
            UUID id = UUID.randomUUID();
            storage.prepare(id);
            Path ass = writer.write(id, title, List.of(cut(0, "자막", 0, 3000)));

            assertThat(maxWidth(ass, ",title,"))
                    .as("제목 넘침 : %s", title)
                    .isLessThanOrEqualTo(878.0);
        }
    }

    @Test
    @DisplayName("공백 없는 긴 자막도 세이프존 폭 안에 들어온다")
    void longCaptionWithoutSpaceFits() throws IOException {
        Path ass = writer.write(jobId, "제목",
                List.of(cut(0, "전산응용기계제도기능사시험후기준비", 0, 3000)));

        assertThat(maxWidth(ass, ",sub,")).isLessThanOrEqualTo(878.0);
    }

    @Test
    @DisplayName("highlight가 caption 안에 있으면 그 부분만 색을 바꾼다")
    void paintsHighlightInsideCaption() throws IOException {
        Path ass = writer.write(jobId, "제목",
                List.of(cut(0, "필기시험 난이도", "필기시험", 0, 3000)));

        assertThat(subLine(ass))
                .contains("{\\c&H0018C5F5&}필기시험{\\c&H00FFFFFF&} 난이도");
    }

    @Test
    @DisplayName("highlight가 없으면 색 태그를 넣지 않는다")
    void keepsPlainCaptionWithoutHighlight() throws IOException {
        Path ass = writer.write(jobId, "제목", List.of(cut(0, "필기시험 난이도", null, 0, 3000)));

        assertThat(subLine(ass)).doesNotContain("\\c&H");
    }

    @Test
    @DisplayName("highlight가 caption에 없으면 그냥 넘어간다")
    void ignoresHighlightNotInCaption() throws IOException {
        Path ass = writer.write(jobId, "제목",
                List.of(cut(0, "필기시험 난이도", "합격률", 0, 3000)));

        String sub = subLine(ass);
        assertThat(sub).doesNotContain("\\c&H");
        assertThat(sub).contains("필기시험 난이도");
    }

    @Test
    @DisplayName("두 줄로 접혀 highlight가 쪼개지면 색을 넣지 않는다")
    void skipsHighlightSplitAcrossLines() throws IOException {
        Path ass = writer.write(jobId, "제목",
                List.of(cut(0, "자동차정비기능사 필기시험 난이도와 합격률", "기능사 필기시험", 0, 3000)));

        String sub = subLine(ass);
        assertThat(sub).contains("\\N");
        assertThat(sub).doesNotContain("\\c&H");
    }

    @Test
    @DisplayName("제목에는 색을 넣지 않는다")
    void neverPaintsTitle() throws IOException {
        Path ass = writer.write(jobId, "합격률", List.of(cut(0, "합격률", "합격률", 0, 3000)));

        assertThat(titleLine(ass)).doesNotContain("\\c&H");
    }

    private CutInput cut(int cutOrder, String caption, int startMs, int audioMs) {
        return cut(cutOrder, caption, null, startMs, audioMs);
    }

    private CutInput cut(int cutOrder, String caption, String highlight, int startMs, int audioMs) {
        return new CutInput(cutOrder, "내레이션", caption, highlight, null, null, audioMs, startMs, null);
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

    private String titleLine(Path ass) throws IOException {
        return dialogues(read(ass)).stream()
                .filter(line -> line.contains(",title,"))
                .findFirst()
                .orElseThrow();
    }

    private double maxWidth(Path ass, String style) throws IOException {
        FontRenderContext frc = new FontRenderContext(null, true, true);
        Font base = new FontDir().font(
                style.contains("title") ? FontDir.TITLE_FILE : FontDir.SUB_FILE);

        double max = 0;
        for(String line : dialogues(read(ass))) {
            if(!line.contains(style)) {
                continue;
            }
            Matcher m = FS.matcher(line);
            if(!m.find()) {
                continue;
            }
            Font font = base.deriveFont((float) Integer.parseInt(m.group(1)));

            String body = line.substring(line.lastIndexOf(",,") + 2).replaceAll("\\{[^}]*}", "");
            for(String row : body.split("\\\\N")) {
                max = Math.max(max, font.getStringBounds(row, frc).getWidth());
            }
        }
        return max;
    }
}