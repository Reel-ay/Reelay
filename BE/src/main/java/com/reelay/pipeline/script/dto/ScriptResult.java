package com.reelay.pipeline.script.dto;

import java.util.List;

// ScriptValidator 결과
// tooLongCutOrders: 글자 수 상한을 넘은 컷 번호. 비어 있지 않으면 "줄여줘" 재호출 대상
public record ScriptResult(
        List<CutDraft> cuts,
        List<Integer> tooLongCutOrders
) {

    public boolean hasTooLong() {
        return !tooLongCutOrders.isEmpty();
    }
}
