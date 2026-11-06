package com.reelay.pipeline.dto;

// SCRIPT 산출물. highlight 는 caption 안의 부분 문자열
public record CutDraft(
        int cutOrder,
        String narration,
        String caption,
        String highlight,
        Integer sourceImageIndex
) {
}
