package com.reelay.pipeline.script.service;

// 대본 수치 규칙. 공통 문서 4절
// 한국어 나레이션 약 5.5자/초 -> 15~25자 = 2.7~4.5초 = 숏폼 컷 1개
// 60초 = 18문장 = 18컷
public final class ScriptRules {

    public static final int TARGET_CUTS = 18;
    public static final int MIN_CUTS = 15;
    public static final int MAX_CUTS = 25;

    public static final int MIN_NARRATION_CHARS = 15;
    public static final int MAX_NARRATION_CHARS = 25;

    // 길이 초과 판정에 여유를 조금 둔다. 26자 하나 때문에 재호출까지 할 필요는 없다
    public static final int NARRATION_TOLERANCE = 2;

    private ScriptRules() {
    }

    // 공백을 뺀 글자 수. 읽는 시간은 공백이 아니라 음절 수에 비례한다
    public static int length(String text) {
        if (text == null) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isWhitespace(text.charAt(i))) {
                count++;
            }
        }
        return count;
    }

    public static boolean isTooLong(String narration) {
        return length(narration) > MAX_NARRATION_CHARS + NARRATION_TOLERANCE;
    }
}
