package com.reelay.pipeline.script.service;

import com.reelay.common.exception.BusinessException;
import com.reelay.common.exception.ErrorCode;
import com.reelay.pipeline.script.dto.CutDraft;
import com.reelay.pipeline.script.dto.LlmScript;
import com.reelay.pipeline.script.dto.ScriptResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

// LLM 응답을 B 가 믿고 쓸 수 있는 CutDraft 로 정리한다
// B 와의 약속: highlight 는 반드시 caption 안의 실제 부분 문자열 (아니면 null)
// LLM 이 규칙을 어겨도 파이프라인이 멈추지 않게 고칠 수 있는 건 여기서 고친다
public class ScriptValidator {

    private static final Logger log = LoggerFactory.getLogger(ScriptValidator.class);

    public ScriptResult validate(LlmScript script, int imageCount) {
        if (script == null || script.cuts() == null) {
            throw new BusinessException(ErrorCode.SCRIPT_FAILED);
        }

        List<CutDraft> cuts = new ArrayList<>();
        List<Integer> tooLong = new ArrayList<>();

        for (LlmScript.LlmCut raw : script.cuts()) {
            if (raw == null || isBlank(raw.narration())) {
                continue; // 읽을 문장이 없는 컷은 버린다
            }
            if (cuts.size() >= ScriptRules.MAX_CUTS) {
                log.warn("컷이 {}개를 넘어 뒤를 버립니다", ScriptRules.MAX_CUTS);
                break;
            }

            int cutOrder = cuts.size();
            String narration = raw.narration().strip();
            String caption = isBlank(raw.caption()) ? narration : raw.caption().strip();
            caption = trimDanglingParticle(caption);
            String highlight = normalizeHighlight(caption, raw.highlight());
            Integer imageIndex = normalizeImageIndex(raw.sourceImageIndex(), imageCount);

            if (ScriptRules.isTooLong(narration)) {
                tooLong.add(cutOrder);
            }
            cuts.add(new CutDraft(cutOrder, narration, caption, highlight, imageIndex));
        }

        if (cuts.size() < ScriptRules.MIN_CUTS) {
            log.warn("컷이 {}개뿐입니다 (최소 {})", cuts.size(), ScriptRules.MIN_CUTS);
            throw new BusinessException(ErrorCode.SCRIPT_FAILED);
        }

        return new ScriptResult(List.copyOf(cuts), List.copyOf(tooLong));
    }

    // 제목 한 줄 정리
    // - 따옴표·이모지 등 제목에 맞지 않는 기호를 지우고 공백을 하나로 모은다
    //   (둘째 줄 강조 문구는 ?! 를 허용한다. 첫 줄 키워드는 명사구라 허용하지 않는다)
    // - 공백 포함 8자를 넘으면 뒤 어절부터 버린다 (자격증·시험 이름 같은 앞의 주제어를 살린다)
    // - 한 어절이 8자를 넘으면 그대로 둔다. 자막 쪽(AssSubtitleWriter)의 글자 크기 축소가 안전망이다
    // - 비었으면 null
    static String normalizeTitleLine(String raw, boolean allowPunctuation) {
        String line = cleanTitleLine(raw, allowPunctuation);
        if (line == null) {
            return null;
        }

        String[] words = line.split(" ");
        StringBuilder fitted = new StringBuilder(words[0]);
        for (int i = 1; i < words.length; i++) {
            String next = fitted + " " + words[i];
            if (titleLength(next) > ScriptRules.MAX_TITLE_LINE_CHARS) {
                break;
            }
            fitted.setLength(0);
            fitted.append(next);
        }
        String result = fitted.toString();
        if (titleLength(result) > ScriptRules.MAX_TITLE_LINE_CHARS) {
            log.warn("제목 한 줄이 {}자를 넘습니다 (한 어절): '{}'", ScriptRules.MAX_TITLE_LINE_CHARS, result);
        }
        if (titleLength(result) > ScriptRules.VIDEO_TITLE_COLUMN_LENGTH) {
            result = result.substring(0, result.offsetByCodePoints(0, ScriptRules.VIDEO_TITLE_COLUMN_LENGTH));
        }
        return result;
    }

    // 기호·이모지 제거와 공백 정리만 한다 (길이는 자르지 않음). 비면 null
    static String cleanTitleLine(String raw, boolean allowPunctuation) {
        if (isBlank(raw)) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        raw.codePoints().forEach(cp -> {
            if (Character.isWhitespace(cp)) {
                sb.append(' ');
            }
            else if (isTitleChar(cp) || (allowPunctuation && (cp == '?' || cp == '!'))) {
                sb.appendCodePoint(cp);
            }
        });
        String line = sb.toString().replaceAll("\\s+", " ").strip();
        return line.isEmpty() ? null : line;
    }

    // 공백 포함 글자 수 (화면 폭 기준이라 공백도 센다)
    static int titleLength(String s) {
        return s.codePointCount(0, s.length());
    }

    // 공백·문장부호를 빼고 비교해 한쪽이 다른 쪽을 그대로 품고 있으면 같은 말로 본다
    static boolean isSameText(String a, String b) {
        String x = compact(a);
        String y = compact(b);
        return !x.isEmpty() && !y.isEmpty() && (x.contains(y) || y.contains(x));
    }

    // 둘째 줄 후보가 블로그 원문(제목)에서 그대로 따온 말인지
    // 후보의 모든 어절(2자 이상)이 원문 제목에 들어 있으면 "블로그 문구"로 본다 -> 유입 문구가 아니다
    private static String dropBoundNoun(String text) {
        int space = text.lastIndexOf(' ');
        if (space <= 0) {
            return text;
        }
        String last = text.substring(space + 1);
        if (!BOUND_NOUNS.contains(last)) {
            return text;
        }
        log.debug("의존명사로 끝나는 caption 에서 마지막 어절을 뺐습니다 - '{}'", text);
        return text.substring(0, space).strip();
    }

    private static String compact(String s) {
        return s == null ? "" : s.replaceAll("[\\s?!·|｜]", "").toLowerCase();
    }

    // 한글·영문·숫자와 제목에 흔한 기호만 남긴다
    private static boolean isTitleChar(int cp) {
        if (Character.isLetterOrDigit(cp)) {
            return true;
        }
        return "·-&+#%()".indexOf(cp) >= 0;
    }

    // 조사로 끝나는 caption은 말이 끊겨 보인다 ("자세한 내용은"). 조사만 떼어 "자세한 내용"으로 만든다
    // 이·가·의·에·과·도 는 뺐다. 실측 250건에서 "강의" "난이도" "결과" "문제풀이" 같은 명사가 전부 걸렸다
    private static final List<String> TRAILING_PARTICLES =
            List.of("에서", "으로", "에게", "부터", "까지", "은", "는", "을", "를");

    private static final List<String> BOUND_NOUNS =
            List.of("수", "것", "때", "점", "줄", "바", "데", "뿐", "만큼");

    static String trimDanglingParticle(String caption) {
        if (isBlank(caption)) {
            return caption;
        }
        String trimmed = dropBoundNoun(caption.strip());
        for (String particle : TRAILING_PARTICLES) {
            if (!trimmed.endsWith(particle)) {
                continue;
            }
            String cut = trimmed.substring(0, trimmed.length() - particle.length()).strip();
            if (cut.isEmpty() || cut.endsWith(" ")) {
                return trimmed;
            }
            int space = cut.lastIndexOf(' ');
            if (space == cut.length() - 1) {
                return trimmed;
            }
            log.debug("조사로 끝나는 caption 에서 조사를 뗐습니다 - '{}' -> '{}'", trimmed, cut);
            return cut;
        }
        return trimmed;
    }

    // caption 안에 실제로 없으면 null. B 가 강조 태그 위치를 못 찾는 사고를 막는다
    static String normalizeHighlight(String caption, String highlight) {
        if (isBlank(highlight)) {
            return null;
        }
        String h = highlight.strip();
        if (caption.contains(h)) {
            return h;
        }
        log.debug("caption 에 없는 highlight 제거 - caption='{}', highlight='{}'", caption, h);
        return null;
    }

    // 주어진 이미지 인덱스 범위 밖이면 null (= 타이포그래피 카드). URL 환각 차단
    static Integer normalizeImageIndex(Integer index, int imageCount) {
        if (index == null || index < 0 || index >= imageCount) {
            return null;
        }
        return index;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
