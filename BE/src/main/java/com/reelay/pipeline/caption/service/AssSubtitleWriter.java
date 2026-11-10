package com.reelay.pipeline.caption.service;

import com.reelay.common.font.FontDir;
import com.reelay.common.storage.StorageService;
import com.reelay.video.dto.CutInput;
import com.reelay.video.dto.VideoTitle;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class AssSubtitleWriter implements SubtitleWriter {

    private static final int WIDTH = 1080;
    private static final int HEIGHT = 1920;

    private static final int SUB_BOTTOM_GAP = 50;

    private static final int SUB_SIZE = 92;
    private static final int SUB_MIN_SIZE = 72;
    private static final int TITLE_SIZE = 158;
    private static final int TITLE_ONE_LINE_FLOOR = 100;
    private static final int TITLE_HARD_MIN = 56;
    private static final String TITLE_SEPARATORS = "｜|【】[]";

    private static final int TITLE_TWO_LINE_MAX_SIZE = 120;
    private static final double TITLE_LINE_HEIGHT = 0.95;
    private static final int HOOK_BOX_PADDING = 14;
    private static final String HOOK_BOX_COLOR = "&H003C3CE6";
    private static final String HOOK_TEXT_COLOR = "&H00FFFFFF";
    private static final String SUB_BOX_COLOR = "&H40000000";
    private static final int SUB_BOX_PADDING = 6;

    private static final String HIGHLIGHT_COLOR = "&H0018C5F5&";
    private static final String DEFAULT_COLOR = "&H00FFFFFF&";

    private static final int SAFE_LEFT = 100;
    private static final int SAFE_RIGHT = 980;
    private static final int SAFE_TOP = 150;
    private static final int MAX_TEXT_WIDTH = SAFE_RIGHT - SAFE_LEFT;
    private static final int HARD_MIN_SIZE = 60;

    private static final int BAND_TOP = 440;
    private static final int BAND_BOTTOM = 1380;
    private static final int TITLE_GAP_ONE = 90;
    private static final int TITLE_GAP_TWO = 50;
    private static final String TITLE_SLANT = "-0.26";

    private static final double INK_TOP_RATIO = 0.125;
    private static final double INK_HEIGHT_RATIO = 0.75;
    private static final double INK_WIDTH_RATIO = 0.866;
    private static final double SLANT_CENTER_RATIO = 0.13;

    private static final FontRenderContext FRC = new FontRenderContext(null, true, true);

    private final StorageService storage;
    private final Font titleFont;
    private final Font subFont;

    public AssSubtitleWriter(StorageService storage, FontDir fontDir) {
        this.storage = storage;
        this.titleFont = fontDir.font(FontDir.TITLE_FILE);
        this.subFont = fontDir.font(FontDir.SUB_FILE);
    }

    // ASS 자막 파일 생성 및 저장
    @Override
    public Path write(UUID jobId, String title, List<CutInput> cuts) {
        return save(jobId, titleLine(title, totalMs(cuts)), cuts);
    }

    // 자막 해상도와 스타일 설정
    private String header() {
        return """
                [Script Info]
                ScriptType: v4.00+
                PlayResX: %d
                PlayResY: %d
                WrapStyle: 2
                ScaledBorderAndShadow: yes

                [V4+ Styles]
                Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
                Style: title,%s,%d,&H00FFFFFF,&H000000FF,&H00000000,&H00000000,0,0,0,0,100,100,0,0,1,2,0,8,0,0,0,1
                Style: sub,%s,%d,&H00FFFFFF,&H000000FF,%s,%s,0,0,0,0,100,100,0,0,4,%d,0,2,0,0,0,1
                Style: titlehook,%s,%d,%s,&H000000FF,&H00000000,&H00000000,0,0,0,0,100,100,0,0,1,0,0,8,0,0,0,1
                Style: hookbox,%s,%d,&H00FFFFFF,&H000000FF,&H00000000,&H00000000,0,0,0,0,100,100,0,0,1,0,0,7,0,0,0,1

                [Events]
                Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
                """.formatted(WIDTH, HEIGHT,
                FontDir.TITLE_FAMILY, TITLE_SIZE,
                FontDir.SUB_FAMILY, SUB_SIZE, SUB_BOX_COLOR, SUB_BOX_COLOR, SUB_BOX_PADDING,
                FontDir.TITLE_FAMILY, TITLE_SIZE, HOOK_TEXT_COLOR,
                FontDir.TITLE_FAMILY, TITLE_SIZE);
    }

    @Override
    public Path writeWithTitle(UUID jobId, VideoTitle title, List<CutInput> cuts) {
        if(title == null || !title.hasHook() || title.keyword() == null || title.keyword().isBlank()) {
            return write(jobId, title == null ? null : title.keyword(), cuts);
        }
        return save(jobId, twoLineTitle(title, totalMs(cuts)), cuts);
    }

    private Path save(UUID jobId, String titleBlock, List<CutInput> cuts) {
        StringBuilder sb = new StringBuilder();
        sb.append(header());
        sb.append(titleBlock);
        for(CutInput cut : cuts) {
            sb.append(subtitleLine(cut));
        }

        Path out = storage.subtitlePath(jobId);
        try {
            Files.writeString(out, sb.toString(), StandardCharsets.UTF_8);
        }
        catch(IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }

    private String twoLineTitle(VideoTitle title, int totalMs) {
        String first = sanitize(title.keyword());
        String second = sanitize(title.hook());

        int size = Math.min(TITLE_TWO_LINE_MAX_SIZE, largestSize(List.of(first, second + "  ")));
        while(size > TITLE_HARD_MIN && twoLineTop(size) < SAFE_TOP) {
            size -= 2;
        }

        int top = twoLineTop(size);
        int secondY = top + lineHeight(size) + HOOK_BOX_PADDING;
        return dialogue("title", 0, totalMs, top, new Fitted(List.of(first), size), null)
                + hookBox(second, size, secondY, totalMs)
                + dialogue("titlehook", 0, totalMs, secondY, new Fitted(List.of(second), size), null);
    }

    private String hookBox(String text, int size, int y, int totalMs) {
        int w = (int) Math.round(width(text, size, titleFont) * INK_WIDTH_RATIO) + HOOK_BOX_PADDING * 2;
        int h = (int) Math.round(size * INK_HEIGHT_RATIO) + HOOK_BOX_PADDING * 2;
        int x = (WIDTH - w) / 2 - (int) Math.round(size * SLANT_CENTER_RATIO);
        int boxY = y + (int) Math.round(size * INK_TOP_RATIO) - HOOK_BOX_PADDING;

        return "Dialogue: 0,%s,%s,hookbox,,0,0,0,,{\\pos(%d,%d)\\p1\\c%s&\\bord0}m 0 0 l %d 0 %d %d 0 %d{\\p0}%n"
                .formatted(time(0), time(totalMs), x, boxY, HOOK_BOX_COLOR, w, w, h, h);
    }

    private int twoLineTop(int size) {
        return BAND_TOP - TITLE_GAP_TWO - lineHeight(size) - HOOK_BOX_PADDING * 2
                - (int) Math.round(size * (INK_TOP_RATIO + INK_HEIGHT_RATIO));
    }

    private int lineHeight(int size) {
        return (int) Math.round(size * TITLE_LINE_HEIGHT);
    }

    // 영상 전체 제목 생성
    private String titleLine(String title, int totalMs) {
        if(title == null || title.isBlank()) {
            return "";
        }
        Fitted fitted = fitTitle(title);
        int block = lineHeight(fitted.size()) * fitted.lines().size();
        int top = Math.max(SAFE_TOP, BAND_TOP - TITLE_GAP_ONE - block);
        return dialogue("title", 0, totalMs, top, fitted, null);
    }

    // 컷별 자막 생성
    private String subtitleLine(CutInput cut) {
        if(cut.caption() == null || cut.caption().isBlank()) {
            return "";
        }
        Fitted fitted = fit(cut.caption(), SUB_SIZE, SUB_MIN_SIZE);
        return dialogue("sub", cut.startMs(), cut.startMs() + cut.audioMs(),
                BAND_BOTTOM - SUB_BOTTOM_GAP, fitted, cut.highlight());
    }

    // 제목 핵심어 추출
    private String headline(String title) {
        String text = title.strip();

        int cut = text.length();
        for(int i = 0; i < TITLE_SEPARATORS.length(); i++) {
            int at = text.indexOf(TITLE_SEPARATORS.charAt(i));
            if(at > 0 && at < cut) {
                cut = at;
            }
        }
        return text.substring(0, cut).strip();
    }

    private Fitted fitTitle(String raw) {
        String text = sanitize(headline(raw));

        int one = largestSize(List.of(text));
        if(one >= TITLE_ONE_LINE_FLOOR) {
            return new Fitted(List.of(text), one);
        }

        List<String> two = splitBalanced(text, TITLE_ONE_LINE_FLOOR, true, titleFont);
        if(two.size() == 1) {
            return new Fitted(List.of(text), one);
        }

        int size = largestSize(two);
        return size > one ? new Fitted(two, size) : new Fitted(List.of(text), one);
    }

    private int largestSize(List<String> lines) {
        for(int size = TITLE_SIZE; size > TITLE_HARD_MIN; size -= 2) {
            if(fits(lines, size, titleFont)) {
                return size;
            }
        }
        return TITLE_HARD_MIN;
    }

    // ASS Dialogue 문자열 생성
    private String dialogue(String style, int startMs, int endMs, int y, Fitted fitted,
                            String highlight) {
        List<String> painted = new ArrayList<>(fitted.lines().size());
        for(String line : fitted.lines()) {
            painted.add(paint(line, highlight));
        }

        String slant = style.startsWith("title") ? "\\fax" + TITLE_SLANT : "";
        String text = String.join("\\N", painted);
        return "Dialogue: 0,%s,%s,%s,,0,0,0,,{\\pos(%d,%d)\\fs%d%s}%s%n"
                .formatted(time(startMs), time(endMs), style, WIDTH / 2, y, fitted.size(),
                        slant, text);
    }

    private String paint(String line, String highlight) {
        if(highlight == null || highlight.isBlank()) {
            return line;
        }

        String needle = sanitize(highlight);
        if(needle.isEmpty()) {
            return line;
        }

        int at = line.indexOf(needle);
        if(at < 0) {
            return line;
        }

        return line.substring(0, at)
                + "{\\c" + HIGHLIGHT_COLOR + "}" + needle + "{\\c" + DEFAULT_COLOR + "}"
                + line.substring(at + needle.length());
    }

    // 줄바꿈 및 글자 크기 조정
    private Fitted fit(String raw, int baseSize, int minSize) {
        String text = sanitize(raw);
        if(width(text, baseSize, subFont) <= MAX_TEXT_WIDTH) {
            return new Fitted(List.of(text), baseSize);
        }

        List<String> byWord = splitBalanced(text, baseSize, true, subFont);
        for(int size = baseSize; size >= minSize; size -= 4) {
            if(fits(byWord, size, subFont)) {
                return new Fitted(byWord, size);
            }
        }

        List<String> byChar = splitBalanced(text, baseSize, false, subFont);
        for(int size = minSize - 4; size >= HARD_MIN_SIZE; size -= 4) {
            if(fits(byChar, size, subFont)) {
                return new Fitted(byChar, size);
            }
        }
        return new Fitted(byChar, HARD_MIN_SIZE);
    }

    private boolean fits(List<String> lines, int size, Font font) {
        return lines.stream().allMatch(line -> width(line, size, font) <= MAX_TEXT_WIDTH);
    }

    // 균형 잡힌 두 줄 분리
    private List<String> splitBalanced(String text, int size, boolean atWordBoundary, Font font) {
        List<Integer> candidates = new ArrayList<>();
        for(int i = 1; i < text.length(); i++) {
            if(!atWordBoundary || text.charAt(i - 1) == ' ') {
                candidates.add(i);
            }
        }
        if(candidates.isEmpty()) {
            return List.of(text);
        }

        int best = candidates.get(0);
        double bestGap = Double.MAX_VALUE;
        for(int i : candidates) {
            double gap = Math.abs(width(text.substring(0, i).strip(), size, font)
                    - width(text.substring(i).strip(), size, font));
            if(gap < bestGap) {
                bestGap = gap;
                best = i;
            }
        }
        return List.of(text.substring(0, best).strip(), text.substring(best).strip());
    }

    // 문자열 너비 측정
    private double width(String text, int size, Font font) {
        return font.deriveFont((float) size).getStringBounds(text, FRC).getWidth();
    }

    // ASS 태그 충돌 방지를 위한 문자열 정리
    private String sanitize(String text) {
        return text.replace('{', '(')
                .replace('}', ')')
                .replace("\\", "")
                .replaceAll("\\s+", " ")
                .strip();
    }

    // 밀리초를 ASS 시간 형식으로 변환
    private String time(int ms) {
        int cs = Math.round(ms / 10f);
        int s = cs / 100;
        cs %= 100;
        int m = s / 60;
        s %= 60;
        return "%d:%02d:%02d.%02d".formatted(m / 60, m % 60, s, cs);
    }

    // 전체 영상 길이 계산
    private int totalMs(List<CutInput> cuts) {
        CutInput last = cuts.get(cuts.size() - 1);
        return last.startMs() + last.audioMs();
    }

    private record Fitted(List<String> lines, int size) {
    }
}
