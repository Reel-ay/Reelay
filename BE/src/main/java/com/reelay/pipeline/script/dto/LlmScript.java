package com.reelay.pipeline.script.dto;

import java.util.List;

// LLM structured output 스키마. Spring AI 가 이 모양의 JSON 스키마를 프롬프트에 붙이고 응답을 이 타입으로 변환한다
// 레이아웃이 1종 고정이라 템플릿 추천 항목은 없다
public record LlmScript(List<LlmCut> cuts) {

    public record LlmCut(
            String narration,          // TTS 가 읽을 문장. 15~25자
            String caption,            // 나레이션에서 그대로 따온 짧은 구
            String highlight,          // caption 안의 부분 문자열. 없으면 null
            Integer sourceImageIndex   // images[].index 중 하나. 없으면 null
    ) {
    }
}
