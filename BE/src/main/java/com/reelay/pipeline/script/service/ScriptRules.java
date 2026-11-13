package com.reelay.pipeline.script.service;

// 대본 수치 규칙. 공통 문서 4절
// gpt-4o-mini-tts(nova) 실측: speed 1.0 에서 4.5자/초, speed 1.2 에서 6.0자/초
// 16컷 x 14~18자 = 224~288자 -> speed 1.2 기준 37~48초
// 30초로 조였더니 정보 컷이 8개뿐이라 내용이 비었다. 늘어짐의 원인은 길이가 아니라 홍보 반복이었다
public final class ScriptRules {

    public static final int PREFERRED_VIDEO_SECONDS = 45;
    public static final int MAX_VIDEO_SECONDS = 60;

    public static final int TARGET_CUTS = 16;
    public static final int MAX_CUTS = 25;

    // 짧은 글은 실패시키지 않고 컷 수를 줄인다 (CRAWL 하한이 100자라 짧은 글도 들어온다)
    // 실측 정상 글 1,472~2,290자(중간값 1,855) -> 본문 100자당 1컷이면 정상 글은 전부 상한 16컷
    public static final int CHARS_PER_CUT = 100;
    public static final int MIN_TARGET_CUTS = 4;

    // LLM 이 목표보다 적게 줘도 이 정도는 받아준다. 이보다 적으면 대본 실패
    public static final int MIN_CUTS = 3;

    // 영상 제목은 두 줄 (첫 줄 키워드 + 둘째 줄 강조 문구). 줄마다 공백 포함 11자
    // 세이프존 폭 878px 기준 11자는 폰트 97. AssSubtitleWriter 가 폭에 맞춰 줄여 그린다
    public static final int MAX_TITLE_LINE_CHARS = 11;
    // video_title, video_title_hook 컬럼 길이. LLM 이 제약을 어겨도 이 이상은 저장하지 않는다
    public static final int VIDEO_TITLE_COLUMN_LENGTH = 50;

    public static final int MIN_NARRATION_CHARS = 14;
    public static final int MAX_NARRATION_CHARS = 18;

    public static int minTotalChars(int targetCuts) {
        return targetCuts * MIN_NARRATION_CHARS;
    }

    public static int maxTotalChars(int targetCuts) {
        return targetCuts * MAX_NARRATION_CHARS;
    }

    // 길이 초과 판정에 여유를 조금 둔다. 26자 하나 때문에 재호출까지 할 필요는 없다
    public static final int NARRATION_TOLERANCE = 2;

    private ScriptRules() {
    }

    // 본문 길이에 맞춘 목표 컷 수. 4 ~ 18
    public static int targetCuts(int bodyLength) {
        int byLength = bodyLength / CHARS_PER_CUT;
        return Math.max(MIN_TARGET_CUTS, Math.min(TARGET_CUTS, byLength));
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
