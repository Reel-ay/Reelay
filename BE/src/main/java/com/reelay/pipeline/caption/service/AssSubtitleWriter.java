package com.reelay.pipeline.caption.service;

import com.reelay.common.font.FontDir;
import com.reelay.common.storage.StorageService;
import com.reelay.video.dto.CutInput;
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

    private static final int SUB_Y = 1500;
    private static final int TITLE_Y = 80;

    private static final int SUB_SIZE = 92;
    private static final int SUB_MIN_SIZE = 72;
    private static final int TITLE_SIZE = 158;
    private static final int TITLE_MIN_SIZE = 110;

    private static final int MAX_TEXT_WIDTH = WIDTH - 120;

    private static final String FONT_NAME = "Black Han Sans";

    private static final FontRenderContext FRC = new FontRenderContext(null, true, true);

    private final StorageService storage;
    private final Font baseFont;

    public AssSubtitleWriter(StorageService storage, FontDir fontDir) {
        this.storage = storage;
        this.baseFont = loadFont(fontDir.fontFile());
    }

    // ASS 자막 파일 생성 및 저장
    @Override
    public Path write(UUID jobId, String title, List<CutInput> cuts) {
        StringBuilder sb = new StringBuilder();
        sb.append(header());
        sb.append(titleLine(title, totalMs(cuts)));
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
                Style: sub,%s,%d,&H00FFFFFF,&H000000FF,&H00000000,&H00000000,0,0,0,0,100,100,0,0,1,3,0,8,0,0,0,1

                [Events]
                Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
                """.formatted(WIDTH, HEIGHT, FONT_NAME, TITLE_SIZE, FONT_NAME, SUB_SIZE);
    }

    // 영상 전체 제목 생성
    private String titleLine(String title, int totalMs) {
        if(title == null || title.isBlank()) {
            return "";
        }
        Fitted fitted = fit(title, TITLE_SIZE, TITLE_MIN_SIZE);
        return dialogue("title", 0, totalMs, TITLE_Y, fitted);
    }

    // 컷별 자막 생성
    private String subtitleLine(CutInput cut) {
        if(cut.caption() == null || cut.caption().isBlank()) {
            return "";
        }
        Fitted fitted = fit(cut.caption(), SUB_SIZE, SUB_MIN_SIZE);
        return dialogue("sub", cut.startMs(), cut.startMs() + cut.audioMs(), SUB_Y, fitted);
    }

    // ASS Dialogue 문자열 생성
    private String dialogue(String style, int startMs, int endMs, int y, Fitted fitted) {
        String text = String.join("\\N", fitted.lines());
        return "Dialogue: 0,%s,%s,%s,,0,0,0,,{\\pos(%d,%d)\\fs%d}%s%n"
                .formatted(time(startMs), time(endMs), style, WIDTH / 2, y, fitted.size(), text);
    }

    // 줄바꿈 및 글자 크기 조정
    private Fitted fit(String raw, int baseSize, int minSize) {
        String text = sanitize(raw);
        if(width(text, baseSize) <= MAX_TEXT_WIDTH) {
            return new Fitted(List.of(text), baseSize);
        }

        List<String> two = splitBalanced(text);
        for(int size = baseSize; size >= minSize; size -= 4) {
            int s = size;
            if(two.stream().allMatch(line -> width(line, s) <= MAX_TEXT_WIDTH)) {
                return new Fitted(two, size);
            }
        }
        return new Fitted(two, minSize);
    }

    // 균형 잡힌 두 줄 분리
    private List<String> splitBalanced(String text) {
        List<Integer> candidates = new ArrayList<>();
        for(int i = 1; i < text.length(); i++) {
            if(text.charAt(i - 1) == ' ') {
                candidates.add(i);
            }
        }
        if(candidates.isEmpty()) {
            for(int i = 1; i < text.length(); i++) {
                candidates.add(i);
            }
        }

        int best = candidates.get(0);
        double bestGap = Double.MAX_VALUE;
        for(int i : candidates) {
            double gap = Math.abs(width(text.substring(0, i).strip(), SUB_SIZE)
                    - width(text.substring(i).strip(), SUB_SIZE));
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

    // TTF 폰트 로드
    private Font loadFont(Path file) {
        try {
            return Font.createFont(Font.TRUETYPE_FONT, file.toFile());
        }
        catch(Exception e) {
            throw new IllegalStateException("폰트를 읽지 못함 - " + file, e);
        }
    }

    private record Fitted(List<String> lines, int size) {
    }
}
