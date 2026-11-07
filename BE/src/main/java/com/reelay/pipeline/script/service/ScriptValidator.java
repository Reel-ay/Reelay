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
