package com.reelay.pipeline.script.dto;

import com.reelay.video.dto.VideoTitle;

import java.util.List;

// SCRIPT 단계 산출물
// videoTitle: 영상 상단 두 줄 제목 (LLM 생성). 못 만들었으면 null
public record Script(
        VideoTitle videoTitle,
        List<CutDraft> cuts
) {
}
