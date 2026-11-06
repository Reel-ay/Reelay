package com.reelay.pipeline.image.dto;

import com.reelay.pipeline.render.enums.KenBurnsVariant;

import java.nio.file.Path;

// IMAGE 산출물. imagePath null = 타이포그래피 카드
public record CutImage(
        int cutOrder,
        Path imagePath,
        KenBurnsVariant kenburns
) {
}
