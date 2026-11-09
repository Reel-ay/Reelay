package com.reelay.pipeline.image.enums;

// 이미지 비율 분류. 용도 : KenBurns 변형 선택, #24 레이아웃의 contain·블러 분기
public enum ImageRatio {
    LANDSCAPE,
    PORTRAIT,
    SQUARE;

    // 1:1 에서 5% 안쪽은 정사각형으로 간주
    private static final double SQUARE_TOLERANCE = 0.05;

    public static ImageRatio of(int width, int height) {
        if(width <= 0 || height <= 0) {
            throw new IllegalArgumentException("이미지 크기가 비정상 - " + width + "x" + height);
        }

        double ratio = (double) width / height;
        if(Math.abs(ratio - 1.0) <= SQUARE_TOLERANCE) {
            return SQUARE;
        }
        return ratio > 1.0 ? LANDSCAPE : PORTRAIT;
    }
}