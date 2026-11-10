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

    // 컷 자막은 사진 띠(y 490~1430) 안 아래쪽에 겹쳐 띄운다
    // 아래 기준 정렬(Alignment 2)이라 이 값이 자막 박스의 아래 끝. 두 줄이면 위로 자란다
    private static final int SUB_Y = 1395;

    // 제목 1줄 기준
    private static final int TITLE_Y = 290;

    private static final int SUB_SIZE = 92;
    private static final int SUB_MIN_SIZE = 72;
    private static final int TITLE_SIZE = 158;
    private static final int TITLE_ONE_LINE_FLOOR = 100;
    private static final int TITLE_HARD_MIN = 56;
    private static final String TITLE_SEPARATORS = "｜|【】[]";

    // 두 줄 제목 (첫 줄 키워드 흰 글씨 + 둘째 줄 강조 문구 형광펜 박스)
    // 상단 앱 UI(상태바·계정명)를 피하면서 이미지 띠(y 490) 위에서 끝나도록 잡은 값. 실제 영상 보고 조정
    private static final int TITLE_TWO_LINE_Y = 150;
    private static final int TITLE_TWO_LINE_MAX_SIZE = 116;
    private static final double TITLE_LINE_HEIGHT = 1.2;
    // 형광펜 박스 안쪽 여백(px). BorderStyle 3 에서 Outline 값이 박스 여백이 된다
    private static final int HOOK_BOX_PADDING = 14;
    // 형광펜 색 (ASS 는 &HAABBGGRR 순서). HIGHLIGHT_COLOR 와 같은 노랑 #F5C518
    private static final String HOOK_BOX_COLOR = "&H0018C5F5";
    private static final String HOOK_TEXT_COLOR = "&H00111111";
    // 사진 위에서도 자막이 읽히도록 줄마다 반투명 검정 박스 (BorderStyle 4 = 줄 단위 박스, libass 0.17+)
    // 알파 0x40 = 약 75% 불투명. 강조어 색이 바뀌어도 박스가 끊기지 않는다
    private static final String SUB_BOX_COLOR = "&H40000000";

    private static final String HIGHLIGHT_COLOR = "&H0018C5F5&";
    private static final String DEFAULT_COLOR = "&H00FFFFFF&";

    private static final int SAFE_LEFT = 100;
    private static final int SAFE_RIGHT = 978;
    private static final int MAX_TEXT_WIDTH = SAFE_RIGHT - SAFE_LEFT;
    private static final int HARD_MIN_SIZE = 60;

    private static final String FONT_NAME = "Black Han Sans";

    private static final FontRenderContext FRC = new FontRenderContext(null, true, true);

    private final StorageService storage;
    private final Font baseFont;

    public AssSubtitleWriter(StorageService storage, FontDir fontDir) {
        this.storage = storage;
        this.baseFont = fontDir.baseFont();
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
                Style: sub,%s,%d,&H00FFFFFF,&H000000FF,%s,%s,0,0,0,0,100,100,0,0,4,6,0,2,0,0,0,1
                Style: titlehook,%s,%d,%s,&H000000FF,%s,%s,0,0,0,0,100,100,0,0,3,%d,0,8,0,0,0,1

                [Events]
                Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
                """.formatted(WIDTH, HEIGHT, FONT_NAME, TITLE_SIZE, FONT_NAME, SUB_SIZE, SUB_BOX_COLOR, SUB_BOX_COLOR,
                FONT_NAME, TITLE_SIZE, HOOK_TEXT_COLOR, HOOK_BOX_COLOR, HOOK_BOX_COLOR, HOOK_BOX_PADDING);
    }

    // 두 줄 제목 버전. hook 이 없으면 기존 한 줄 제목과 같다
    @Override
    public Path writeWithTitle(UUID jobId, VideoTitle title, List<CutInput> cuts) {
        if(title == null || !title.hasHook() || title.keyword() == null || title.keyword().isBlank()) {
            return write(jobId, title == null ? null : title.keyword(), cuts);
        }
        return save(jobId, twoLineTitle(title, totalMs(cuts)), cuts);
    }

    // 헤더 + 제목 + 컷 자막을 합쳐 subs.ass 로 저장
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

    // 첫 줄은 흰 글씨(title 스타일), 둘째 줄은 노란 형광펜 박스 위 검은 글씨(titlehook 스타일)
    // 두 줄을 같은 크기로, 줄마다 따로 Dialogue 를 만든다
    private String twoLineTitle(VideoTitle title, int totalMs) {
        String first = sanitize(title.keyword());
        String second = sanitize(title.hook());
        // 형광펜 박스 여백만큼 폭이 늘어나므로 그만큼 빼고 맞춘다
        int size = Math.min(TITLE_TWO_LINE_MAX_SIZE, largestSize(List.of(first, second + "  ")));
        int secondY = TITLE_TWO_LINE_Y + (int) Math.round(size * TITLE_LINE_HEIGHT) + HOOK_BOX_PADDING;
        return dialogue("title", 0, totalMs, TITLE_TWO_LINE_Y, new Fitted(List.of(first), size), null)
                + dialogue("titlehook", 0, totalMs, secondY, new Fitted(List.of(second), size), null);
    }

    // 영상 전체 제목 생성
    private String titleLine(String title, int totalMs) {
        if(title == null || title.isBlank()) {
            return "";
        }
        return dialogue("title", 0, totalMs, TITLE_Y, fitTitle(title), null);
    }

    // 컷별 자막 생성
    private String subtitleLine(CutInput cut) {
        if(cut.caption() == null || cut.caption().isBlank()) {
            return "";
        }
        Fitted fitted = fit(cut.caption(), SUB_SIZE, SUB_MIN_SIZE);
        return dialogue("sub", cut.startMs(), cut.startMs() + cut.audioMs(), SUB_Y, fitted,
                cut.highlight());
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

        List<String> two = splitBalanced(text, TITLE_ONE_LINE_FLOOR, true);
        if(two.size() == 1) {
            return new Fitted(List.of(text), one);
        }

        int size = largestSize(two);
        return size > one ? new Fitted(two, size) : new Fitted(List.of(text), one);
    }

    private int largestSize(List<String> lines) {
        for(int size = TITLE_SIZE; size > TITLE_HARD_MIN; size -= 2) {
            if(fits(lines, size)) {
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

        String text = String.join("\\N", painted);
        return "Dialogue: 0,%s,%s,%s,,0,0,0,,{\\pos(%d,%d)\\fs%d}%s%n"
                .formatted(time(startMs), time(endMs), style, WIDTH / 2, y, fitted.size(), text);
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
        if(width(text, baseSize) <= MAX_TEXT_WIDTH) {
            return new Fitted(List.of(text), baseSize);
        }

        List<String> byWord = splitBalanced(text, baseSize, true);
        for(int size = baseSize; size >= minSize; size -= 4) {
            if(fits(byWord, size)) {
                return new Fitted(byWord, size);
            }
        }

        List<String> byChar = splitBalanced(text, baseSize, false);
        for(int size = minSize - 4; size >= HARD_MIN_SIZE; size -= 4) {
            if(fits(byChar, size)) {
                return new Fitted(byChar, size);
            }
        }
        return new Fitted(byChar, HARD_MIN_SIZE);
    }

    private boolean fits(List<String> lines, int size) {
        return lines.stream().allMatch(line -> width(line, size) <= MAX_TEXT_WIDTH);
    }

    // 균형 잡힌 두 줄 분리
    private List<String> splitBalanced(String text, int size, boolean atWordBoundary) {
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
            double gap = Math.abs(width(text.substring(0, i).strip(), size)
                    - width(text.substring(i).strip(), size));
            if(gap < bestGap) {
                bestGap = gap;
                best = i;
            }
        }
        return List.of(text.substring(0, best).strip(), text.substring(best).strip());
    }

    // 문자열 너비 측정
    private double width(String text, int size) {
        return baseFont.deriveFont((float) size).getStringBounds(text, FRC).getWidth();
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
