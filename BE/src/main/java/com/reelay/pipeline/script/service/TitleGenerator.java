package com.reelay.pipeline.script.service;

import com.reelay.pipeline.crawl.dto.CrawledPost;
import com.reelay.pipeline.script.dto.CutDraft;
import com.reelay.pipeline.script.dto.LlmTitle;
import com.reelay.video.dto.VideoTitle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

// 영상 상단 두 줄 제목 생성
// - 첫 줄(keyword): 블로그 핵심 키워드
// - 둘째 줄(hook): LLM 이 추천한 유입 문구. 후보 5개 중 규칙을 통과한 첫 번째를 쓴다
//   (8자 이내, 첫 줄과 다른 말, 블로그 원문 제목 단어를 그대로 옮긴 말이 아닐 것)
// 제목은 영상 품질 요소일 뿐이라 실패해도 job 을 실패시키지 않는다 -> null 이면 원문 제목 한 줄로 폴백
@Component
public class TitleGenerator {

    private static final Logger log = LoggerFactory.getLogger(TitleGenerator.class);

    private final ScriptLlmClient llm;

    public TitleGenerator(ScriptLlmClient llm) {
        this.llm = llm;
    }

    public VideoTitle generate(CrawledPost post, List<CutDraft> cuts) {
        try {
            String system = TitlePrompts.system();
            String user = TitlePrompts.user(post, cuts);

            LlmTitle first = llm.callTitle(system, user);
            VideoTitle title = pick(first, post.title());
            if (title != null && !title.hasHook() && first != null) {
                // 후보가 전부 탈락 -> 이유를 알려 주고 한 번만 다시
                LlmTitle second = llm.callTitle(system, user + TitlePrompts.retry("원문 제목 단어이거나 첫 줄과 같거나 너무 김"));
                VideoTitle retried = pick(second, post.title());
                if (retried != null && retried.hasHook()) {
                    title = new VideoTitle(title.keyword(), retried.hook());
                }
            }
            log.info("[TITLE] 영상 제목 '{}' / '{}'",
                    title == null ? null : title.keyword(), title == null ? null : title.hook());
            return title;
        }
        catch (RuntimeException e) {
            log.warn("[TITLE] 영상 제목 생성 실패, 원문 제목으로 진행합니다", e);
            return null;
        }
    }

    // 첫 줄이 없으면 null. 둘째 줄은 규칙을 통과한 첫 후보, 없으면 null(한 줄 제목)
    static VideoTitle pick(LlmTitle raw, String blogTitle) {
        if (raw == null) {
            return null;
        }
        String keyword = ScriptValidator.normalizeTitleLine(raw.keyword(), false);
        if (keyword == null) {
            return null;
        }
        if (raw.hooks() != null) {
            for (String candidate : raw.hooks()) {
                // 유입 문구는 잘라 쓰면 뜻이 망가지므로 길면 자르지 않고 탈락시킨다
                String hook = ScriptValidator.cleanTitleLine(candidate, true);
                if (hook == null) {
                    continue;
                }
                if (ScriptValidator.titleLength(hook) > ScriptRules.MAX_TITLE_LINE_CHARS) {
                    log.debug("[TITLE] 후보 탈락(길이) '{}'", hook);
                    continue;
                }
                if (ScriptValidator.isSameText(keyword, hook)) {
                    log.debug("[TITLE] 후보 탈락(첫 줄과 같음) '{}'", hook);
                    continue;
                }
                if (ScriptValidator.isCopiedFrom(hook, blogTitle)) {
                    log.debug("[TITLE] 후보 탈락(원문 제목 단어) '{}'", hook);
                    continue;
                }
                return new VideoTitle(keyword, hook);
            }
        }
        return new VideoTitle(keyword, null);
    }
}
