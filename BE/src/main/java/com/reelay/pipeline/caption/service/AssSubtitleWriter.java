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

    private static final int CARD_SIZE = 190;
    private static final int CARD_MIN_SIZE = 76;
    private static final int CARD_MAX_LINES = 3;
    private static final int CARD_BLOCK_HEIGHT = 620;
    // 표지는 인물 일러스트가 y 1040 아래를 덮어서 글자 자리가 더 좁다
    private static final int COVER_TOP = 470;
    private static final int COVER_BLOCK_HEIGHT = 480;
    private static final int CARD_LEFT = 100;
    private static final int CARD_TOP = 700;
    private static final int CARD_QUOTE_SIZE = 190;
    private static final int CARD_QUOTE_Y = 470;
    private static final int CARD_HEAD_Y = 170;
    private static final int CARD_HEAD_RULE_Y = 232;
    private static final int CARD_FOOT_RULE_Y = 1700;
    private static final int CARD_RULE_HEIGHT = 3;
    private static final int CARD_HEAD_SIZE = 48;
    private static final int CARD_BADGE_GAP = 56;
    private static final int CARD_BADGE_SIZE = 64;
    private static final int CARD_BADGE_PAD = 36;
    private static final int CARD_RISE = 46;
    private static final int CARD_RISE_MS = 260;
    private static final int CARD_FADE_MS = 180;
    private static final int CARD_STAGGER_MS = 70;
    private static final double CARD_LINE_HEIGHT = 1.05;
    private static final int CARD_BAR_GAP = 100;
    private static final int CARD_BAR_WIDTH = 120;
    private static final int CARD_BAR_HEIGHT = 10;
    private static final String CARD_ACCENT = "&H001A6BF2&";
    private static final String CARD_INK_LIGHT = "&H001A1A1A&";
    private static final String CARD_BADGE_INK = "&H00FFFFFF&";

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
        return save(jobId, titleLine(title, totalMs(cuts)),
                title == null || title.isBlank() ? null : headline(title), null, cuts);
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
                Style: card,%s,%d,&H00FFFFFF,&H000000FF,&H00000000,&H00000000,0,0,0,0,100,100,-2,0,1,0,0,7,0,0,0,1
                Style: cardhead,%s,%d,&H00FFFFFF,&H000000FF,&H00000000,&H00000000,0,0,0,0,100,100,10,0,1,0,0,7,0,0,0,1
                Style: cardbadge,%s,%d,&H00FFFFFF,&H000000FF,&H00000000,&H00000000,0,0,0,0,100,100,0,0,1,0,0,7,0,0,0,1
                Style: cardshape,%s,%d,&H00FFFFFF,&H000000FF,&H00000000,&H00000000,0,0,0,0,100,100,0,0,1,0,0,7,0,0,0,1

                [Events]
                Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
                """.formatted(WIDTH, HEIGHT,
                FontDir.TITLE_FAMILY, TITLE_SIZE,
                FontDir.SUB_FAMILY, SUB_SIZE, SUB_BOX_COLOR, SUB_BOX_COLOR, SUB_BOX_PADDING,
                FontDir.TITLE_FAMILY, TITLE_SIZE, HOOK_TEXT_COLOR,
                FontDir.TITLE_FAMILY, TITLE_SIZE,
                FontDir.TITLE_FAMILY, CARD_SIZE,
                FontDir.SUB_FAMILY, CARD_HEAD_SIZE,
                FontDir.SUB_FAMILY, CARD_BADGE_SIZE,
                FontDir.TITLE_FAMILY, CARD_SIZE);
    }

    @Override
    public Path writeWithTitle(UUID jobId, VideoTitle title, List<CutInput> cuts) {
        if(title == null || !title.hasHook() || title.keyword() == null || title.keyword().isBlank()) {
            return write(jobId, title == null ? null : title.keyword(), cuts);
        }
        return save(jobId, twoLineTitle(title, totalMs(cuts)), title.keyword(), title.hook(), cuts);
    }

    private Path save(UUID jobId, String titleBlock, String keyword, String hook, List<CutInput> cuts) {
        StringBuilder sb = new StringBuilder();
        boolean cardMode = cuts.stream().allMatch(cut -> cut.imagePath() == null);

        sb.append(header());
        if(!cardMode) {
            sb.append(titleBlock);
        }

        // 카드끼리 글자 크기가 다르면 넘어갈 때마다 들쑥날쑥해 보인다. 제일 긴 캡션에 맞춰 하나로 쓴다
        int cardSize = cardMode ? cuts.stream()
                .map(cut -> cardFit(cut.caption()))
                .mapToInt(Fitted::size)
                .min()
                .orElse(CARD_MIN_SIZE) : 0;

        for(int i = 0; i < cuts.size(); i++) {
            sb.append(cardMode
                    ? cardLine(cuts.get(i), i, cuts.size(),
                            i == 0 ? coverSize(cuts.get(i).caption(), cardSize) : cardSize,
                            keyword, hook)
                    : subtitleLine(cuts.get(i)));
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

    // 사진이 한 장도 없으면 전면 타이포그래피 카드로 그린다
    // 배경은 brand/card 템플릿을 컷 번호대로 돌려 쓴다 (BrandAssets.cardTemplate)
    private String cardLine(CutInput cut, int index, int total, int size, String keyword, String hook) {
        if(cut.caption() == null || cut.caption().isBlank()) {
            return "";
        }

        String ink = CARD_INK_LIGHT;
        String accent = CARD_ACCENT;

        int start = cut.startMs();
        int end = start + cut.audioMs();
        Fitted fitted = new Fitted(cardFit(cut.caption()).lines(), size);
        int lineHeight = (int) Math.round(size * CARD_LINE_HEIGHT);

        boolean cover = index == 0;
        boolean quote = total >= 4 && index == total - 2;
        int top = cover ? COVER_TOP : CARD_TOP;

        StringBuilder sb = new StringBuilder();
        if(!cover) {
            sb.append(cardFrame(start, end, keyword, ink));
        }
        boolean badged = index == 0 && hook != null && !hook.isBlank();
        if(badged) {
            sb.append(badge(hook, start, end, accent, CARD_BADGE_INK));
        }
        if(badged) {
            // 배지가 있으면 악센트 바는 중복이라 그리지 않는다
        }
        else if(quote) {
            sb.append("Dialogue: 0,%s,%s,card,,0,0,0,,{\\pos(%d,%d)\\fad(%d,0)\\fs%d\\c%s}“%n"
                    .formatted(time(start), time(end), CARD_LEFT, CARD_QUOTE_Y,
                            CARD_FADE_MS, CARD_QUOTE_SIZE, accent));
        }
        else {
            sb.append("Dialogue: 0,%s,%s,cardshape,,0,0,0,,{\\move(%d,%d,%d,%d,0,%d)\\fad(%d,0)\\p1\\c%s\\bord0\\shad0}m 0 0 l %d 0 %d %d 0 %d{\\p0}%n"
                    .formatted(time(start), time(end), CARD_LEFT - 60, top - CARD_BAR_GAP,
                            CARD_LEFT, top - CARD_BAR_GAP,
                            CARD_RISE_MS, CARD_FADE_MS, accent,
                            CARD_BAR_WIDTH, CARD_BAR_WIDTH, CARD_BAR_HEIGHT, CARD_BAR_HEIGHT));
        }

        List<String> lines = fitted.lines();
        for(int i = 0; i < lines.size(); i++) {
            int y = top + i * lineHeight;
            int from = Math.min(start + i * CARD_STAGGER_MS, end);
            sb.append("Dialogue: 0,%s,%s,card,,0,0,0,,{\\move(%d,%d,%d,%d,0,%d)\\fad(%d,0)\\fs%d\\c%s}%s%n"
                    .formatted(time(from), time(end), CARD_LEFT, y + CARD_RISE, CARD_LEFT, y,
                            CARD_RISE_MS, CARD_FADE_MS, fitted.size(), ink,
                            paintCard(lines.get(i), cut.highlight(), accent, ink)));
        }
        return sb.toString();
    }

    // 시안의 헤더와 하단 선. 컷 번호는 카드 모드에서만 쓰므로 전 컷이 카드일 때만 맞는 숫자다
    private String cardFrame(int startMs, int endMs, String keyword, String ink) {
        StringBuilder sb = new StringBuilder();

        if(keyword != null && !keyword.isBlank()) {
            sb.append("Dialogue: 0,%s,%s,cardhead,,0,0,0,,{\\pos(%d,%d)\\fad(%d,0)\\c%s}%s%n"
                    .formatted(time(startMs), time(endMs), CARD_LEFT, CARD_HEAD_Y,
                            CARD_FADE_MS, ink, sanitize(keyword)));
        }
        sb.append(rule(startMs, endMs, CARD_LEFT, CARD_HEAD_RULE_Y, SAFE_RIGHT - CARD_LEFT, ink));
        sb.append(rule(startMs, endMs, CARD_LEFT, CARD_FOOT_RULE_Y, SAFE_RIGHT - CARD_LEFT, ink));
        return sb.toString();
    }

    // 첫 카드에만 제목 둘째 줄을 알약으로 올린다. 폭은 글자 잉크 폭에 패딩을 더해 잡는다
    private String badge(String hook, int startMs, int endMs, String accent, String ink) {
        if(hook == null || hook.isBlank()) {
            return "";
        }
        String text = sanitize(hook);
        int w = (int) Math.round(width(text, CARD_BADGE_SIZE, subFont) * INK_WIDTH_RATIO) + CARD_BADGE_PAD * 2;
        int h = (int) Math.round(CARD_BADGE_SIZE * INK_HEIGHT_RATIO) + CARD_BADGE_PAD;
        int r = h / 2;
        int y = COVER_TOP - CARD_BADGE_GAP - h;

        return "Dialogue: 0,%s,%s,cardshape,,0,0,0,,{\\pos(%d,%d)\\fad(%d,0)\\p1\\c%s\\bord0\\shad0}%s{\\p0}%n"
                .formatted(time(startMs), time(endMs), CARD_LEFT, y, CARD_FADE_MS, accent,
                        roundedRect(w, h, r))
                + "Dialogue: 0,%s,%s,cardbadge,,0,0,0,,{\\pos(%d,%d)\\fad(%d,0)\\c%s}%s%n"
                .formatted(time(startMs), time(endMs), CARD_LEFT + CARD_BADGE_PAD,
                        y + CARD_BADGE_PAD / 2 - (int) Math.round(CARD_BADGE_SIZE * INK_TOP_RATIO),
                        CARD_FADE_MS, ink, text);
    }

    private String roundedRect(int w, int h, int r) {
        return ("m %d 0 l %d 0 b %d 0 %d 0 %d %d l %d %d b %d %d %d %d %d %d "
                + "l %d %d b 0 %d 0 %d 0 %d l 0 %d b 0 0 0 0 %d 0")
                .formatted(r, w - r, w, w, w, r, w, h - r, w, h, w, h, w - r, h,
                        r, h, h, h, h - r, r, r);
    }

    private String rule(int startMs, int endMs, int x, int y, int width, String color) {
        return "Dialogue: 0,%s,%s,cardshape,,0,0,0,,{\\pos(%d,%d)\\fad(%d,0)\\p1\\c%s\\bord0\\shad0}m 0 0 l %d 0 %d %d 0 %d{\\p0}%n"
                .formatted(time(startMs), time(endMs), x, y, CARD_FADE_MS, color,
                        width, width, CARD_RULE_HEIGHT, CARD_RULE_HEIGHT);
    }

    private String paintCard(String line, String highlight, String accent, String ink) {
        if(highlight == null || highlight.isBlank() || !line.contains(highlight)) {
            return line;
        }
        return line.replace(highlight, "{\\c%s}%s{\\c%s}".formatted(accent, highlight, ink));
    }

    // 카드는 줄 수를 늘려서라도 글자를 키운다. 한 줄에 들어간다고 거기서 멈추면 화면이 비어 보인다
    // 표지 글자가 일러스트를 넘지 않도록 공용 크기를 그대로 쓸 수 있는지 확인한다
    private int coverSize(String raw, int shared) {
        int lines = cardFit(raw).lines().size();
        int size = shared;
        while(size > CARD_MIN_SIZE && Math.round(size * CARD_LINE_HEIGHT) * lines > COVER_BLOCK_HEIGHT) {
            size -= 2;
        }
        return size;
    }

    private Fitted cardFit(String raw) {
        String text = sanitize(raw);
        Fitted best = null;

        for(int count = 1; count <= CARD_MAX_LINES; count++) {
            List<String> lines = splitInto(text, count);
            if(lines.size() != count) {
                continue;
            }
            int size = largestCardSize(lines);
            if(Math.round(size * CARD_LINE_HEIGHT) * count > CARD_BLOCK_HEIGHT) {
                continue;
            }
            if(best == null || size > best.size()) {
                best = new Fitted(lines, size);
            }
        }
        return best == null ? new Fitted(List.of(text), CARD_MIN_SIZE) : best;
    }

    // 폭은 글자 크기에 비례하므로 기준 크기로 한 번 재고 나눈다
    private int largestCardSize(List<String> lines) {
        double worst = 0;
        for(String line : lines) {
            worst = Math.max(worst, width(line, 100, titleFont) * INK_WIDTH_RATIO / 100.0);
        }
        if(worst <= 0) {
            return CARD_MIN_SIZE;
        }
        int size = (int) Math.floor(MAX_TEXT_WIDTH / worst);
        return Math.max(CARD_MIN_SIZE, Math.min(CARD_SIZE, size));
    }

    // 어절 단위로 줄마다 폭이 비슷해지게 나눈다. 어절이 모자라면 나눌 수 있는 만큼만 돌려준다
    private List<String> splitInto(String text, int count) {
        String[] words = text.split(" ");
        if(count == 1 || words.length < count) {
            return count == 1 ? List.of(text) : List.of();
        }

        double total = width(text, 100, titleFont);
        double target = total / count;

        List<String> lines = new ArrayList<>(count);
        StringBuilder line = new StringBuilder();
        for(int i = 0; i < words.length; i++) {
            int remaining = count - lines.size();
            boolean mustBreak = words.length - i == remaining - 1 && !line.isEmpty();
            boolean overTarget = !line.isEmpty()
                    && width(line + " " + words[i], 100, titleFont) > target * (lines.size() + 1);

            if(lines.size() < count - 1 && (mustBreak || overTarget)) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if(!line.isEmpty()) {
                line.append(' ');
            }
            line.append(words[i]);
        }
        lines.add(line.toString());
        return lines;
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
        for(int size = baseSize; size >= minSize; size -= 2) {
            if(subFits(List.of(text), size)) {
                return new Fitted(List.of(text), size);
            }
        }

        List<String> byWord = splitBalanced(text, baseSize, true, subFont);
        for(int size = baseSize; size >= minSize; size -= 4) {
            if(subFits(byWord, size)) {
                return new Fitted(byWord, size);
            }
        }

        List<String> byChar = splitBalanced(text, baseSize, false, subFont);
        for(int size = minSize - 4; size >= HARD_MIN_SIZE; size -= 4) {
            if(subFits(byChar, size)) {
                return new Fitted(byChar, size);
            }
        }
        return new Fitted(byChar, HARD_MIN_SIZE);
    }

    private boolean subFits(List<String> lines, int size) {
        double limit = MAX_TEXT_WIDTH - SUB_BOX_PADDING * 2;
        return lines.stream()
                .allMatch(line -> width(line, size, subFont) * INK_WIDTH_RATIO <= limit);
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
