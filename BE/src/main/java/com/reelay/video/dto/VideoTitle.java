package com.reelay.video.dto;

// 영상 상단 제목 (A -> B 경계 데이터)
// keyword: 첫 줄. 블로그 내용에서 벗어나지 않는 핵심 키워드 (흰색)
// hook:    둘째 줄. 유입을 끄는 강조 문구 (강조색). null 이면 한 줄 제목
// LLM 제목이 없으면 블로그 원문 제목을 keyword 에 담고 hook 은 null (자막 쪽에서 원문 제목 다듬기 로직을 탄다)
public record VideoTitle(
        String keyword,
        String hook
) {

    public static VideoTitle singleLine(String title) {
        return new VideoTitle(title, null);
    }

    public boolean hasHook() {
        return hook != null && !hook.isBlank();
    }
}
