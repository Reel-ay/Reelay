package com.reelay.pipeline.image.dto;

import com.reelay.pipeline.image.enums.ImageRatio;
import com.reelay.pipeline.render.enums.KenBurnsVariant;

import java.nio.file.Path;

// IMAGE 산출물. imagePath null = 타이포그래피 카드
public record CutImage(
        int cutOrder,
        Path imagePath,
        KenBurnsVariant kenburns,
        ImageRatio ratio
) {
    // 타이포그래피 카드용
    public CutImage(int cutOrder, Path imagePath, KenBurnsVariant kenburns) {
        this(cutOrder, imagePath, kenburns, null);
    }
}