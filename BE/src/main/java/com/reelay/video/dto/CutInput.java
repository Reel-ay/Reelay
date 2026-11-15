package com.reelay.video.dto;

import com.reelay.pipeline.render.enums.KenBurnsVariant;

import java.nio.file.Path;

// CAPTION·RENDER 입력. imagePath null = 타이포그래피 카드
public record CutInput(
        int cutOrder,
        String narration,
        String caption,
        String highlight,
        Path imagePath,
        Path audioPath,
        int audioMs,
        int startMs,
        KenBurnsVariant kenburns
) {
}
