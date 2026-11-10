package com.reelay.pipeline.script.dto;

import java.util.List;

// 영상 상단 두 줄 제목 LLM structured output
// keyword: 첫 줄. 블로그 핵심 키워드 (공백 포함 8자 이내)
// hooks:   둘째 줄 후보. 블로그 문장이 아니라 유입을 위한 추천 문구 (서로 다른 유형 5개)
public record LlmTitle(
        String keyword,
        List<String> hooks
) {
}
