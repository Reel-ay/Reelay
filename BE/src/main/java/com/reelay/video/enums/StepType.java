package com.reelay.video.enums;

public enum StepType {
    CRAWL(1),
    SCRIPT(2),
    IMAGE(3),
    TTS(4),
    CAPTION(5),
    RENDER(6);

    private final int order;

    StepType(int order) {
        this.order = order;
    }

    public int getOrder() {
        return order;
    }
}
