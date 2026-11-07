package com.reelay.pipeline.script.service;

import com.reelay.common.exception.BusinessException;
import com.reelay.common.exception.OpenAiTransientException;
import com.reelay.pipeline.crawl.dto.CrawledPost;
import com.reelay.pipeline.script.dto.CutDraft;
import com.reelay.pipeline.script.dto.LlmScript;
import com.reelay.pipeline.script.dto.ScriptResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

// SCRIPT 단계: 크롤링 결과 -> 컷 대본(CutDraft) 목록
// 컷 수는 18 고정이 아니라 본문 길이에 맞춘다 (짧은 글은 컷을 줄인다)
// 1) 생성  2) 검증·정리  3) 길이 초과 컷이 있으면 "줄여줘" 1회 재호출
@Service
public class ScriptService {

    private static final Logger log = LoggerFactory.getLogger(ScriptService.class);

    private final ScriptLlmClient llm;
    private final ScriptValidator validator = new ScriptValidator();

    public ScriptService(ScriptLlmClient llm) {
        this.llm = llm;
    }

    public List<CutDraft> generate(CrawledPost post) {
        int imageCount = post.images().size();
        int targetCuts = ScriptRules.targetCuts(post.body().length());
        String system = ScriptPrompts.system(targetCuts);

        LlmScript first = llm.call(system, ScriptPrompts.user(post));
        ScriptResult result = validator.validate(first, imageCount);
        logLengths("1차", result);

        if (!result.hasTooLong()) {
            return result.cuts();
        }

        // 프롬프트만으로는 글자 수를 잘 안 지킨다 (실측 29~37자). 한 번만 줄여 달라고 다시 부른다
        try {
            LlmScript shortened = llm.call(system,
                    ScriptPrompts.shorten(result.cuts(), result.tooLongCutOrders()));
            ScriptResult second = validator.validate(shortened, imageCount);
            logLengths("줄이기", second);
            return second.cuts();
        }
        catch (OpenAiTransientException e) {
            throw e;
        }
        catch (BusinessException e) {
            // 줄이기 응답이 망가졌으면 1차 대본으로 진행한다. 조금 긴 영상이 실패보다 낫다
            log.warn("줄이기 재호출 결과를 쓸 수 없어 1차 대본을 사용합니다", e);
            return result.cuts();
        }
    }

    private void logLengths(String label, ScriptResult result) {
        if (!log.isDebugEnabled()) {
            return;
        }
        List<Integer> lengths = result.cuts().stream()
                .map(c -> ScriptRules.length(c.narration()))
                .toList();
        log.debug("[SCRIPT {}] 컷 {}개, 글자 수 {}, 초과 컷 {}",
                label, result.cuts().size(), lengths, result.tooLongCutOrders());
    }
}
