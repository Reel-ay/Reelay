package com.reelay.pipeline.script.service;

import com.reelay.pipeline.crawl.dto.CrawledPost;
import com.reelay.pipeline.script.dto.CutDraft;

import java.util.List;

// 영상 상단 두 줄 제목 프롬프트
// 화면: 9:16 상단, 첫 줄 흰 글씨(블로그 핵심 키워드) + 둘째 줄 노란 형광펜(유입 문구)
// 대본과 따로 부르는 이유: 대본 프롬프트에 섞으면 둘째 줄을 블로그 제목에서 그대로 따오는 경향이 강했다
public final class TitlePrompts {

    // 본문은 앞부분만 준다. 제목 키워드를 잡는 데 전문이 필요 없고 토큰을 아낀다
    private static final int BODY_PREVIEW_CHARS = 1200;

    private TitlePrompts() {
    }

    public static String system() {
        int n = ScriptRules.MAX_TITLE_LINE_CHARS;
        return """
                너는 인스타그램 릴스·유튜브 쇼츠 썸네일 제목을 만드는 카피라이터다.
                9:16 세로 영상 상단에 두 줄 제목이 영상 내내 고정으로 뜬다.
                영상은 교육 브랜드의 마케팅 영상이다. 제목은 그 영상 전체를 대표해야 한다.
                [영상 나레이션] 을 처음부터 끝까지 읽고 만든다. 앞쪽 몇 컷만 보고 짓지 않는다.

                [두 줄이 함께 말해야 하는 것]
                - 제목은 원문이 결국 무엇을 약속하는 글인지를 담는다. 영상 속 사실 하나를 집어 오는 것이 아니다.
                  원문 "전기기능사 3개년 합격률, 단기에 합격하는 체계적인 방법"
                    -> "전기기능사" + "단기에 합격하는 법"   (O) 글의 약속을 담았다
                    -> "전기기능사" + "합격률은 2배"        (X) 본문 속 사실 한 조각일 뿐이다
                - 두 줄은 세로로 붙어 있어 시청자가 이어서 읽는다. 따로 만들면 안 된다.
                - 둘째 줄은 그 자체로 끝나는 말이어야 한다. 첫 줄에 이어 붙여야 뜻이 완성되는 토막은 실패다.
                - 대본의 특정 컷 문장을 그대로 옮기지 않는다. 그건 제목이 아니라 자막의 반복이다.

                [keyword - 첫 줄, 흰 글씨]
                - 영상 전체가 무엇에 관한 것인지 알려주는 주제어. 공백 포함 %d자 이내.
                  원문 제목이 아니라 영상 내용을 기준으로 고른다.
                  원문 제목이 없거나 주제어 하나뿐이면 나레이션 전체에서 반복되는 대상을 keyword 로 쓴다.
                - 원문 제목에서 SEO 수식어(합격률, 총정리, 꿀팁, 후기, 난이도 같은 덧붙임)를 걷어낸 형태.
                - 자격증·시험·제품 이름은 줄이지 말고 온전히 쓴다. 넘치면 뒤의 설명어를 버린다.
                - 명사구. 문장부호·이모지를 쓰지 않는다.

                [hooks - 둘째 줄 후보 5개, 노란 형광펜]
                - [영상 나레이션] 의 첫 문장이 영상의 핵심을 던지는 훅이다. 제목은 그 핵심을 담는다.
                  중간 대목 하나를 집어 오면 영상 전체를 대표하지 못한다.
                  나레이션이 교재·이론·기출·강의를 거쳐 "단기 합격" 으로 간다면
                    -> "단기 합격 공부법" (O) 전체를 관통한다
                    -> "기본 개념 이해법" (X) 중간 한 대목일 뿐이다
                - 둘째 줄은 "이 영상을 보면 무엇을 얻는가" 다. 원문이 약속하는 것을 말한다.
                  각각 공백 포함 %d자 이내.
                  원문 "전기기능사 3개년 합격률, 단기에 합격하는 체계적인 공부방법"
                    -> "단기 합격 공부법", "합격 팁", "합격 공부 순서"   (O) 글의 약속을 담았다
                    -> "실기 합격률", "응시자 6만명"                   (X) 본문 속 사실 한 조각
                - 원문 제목의 단어를 써도 된다. 핵심이 거기 들어 있다.
                  다만 원문 제목을 통째로 잘라 붙이지는 않는다. 짧은 말로 다시 쓴다.
                - 라벨이 아니라 읽히는 말이어야 한다. "시험 준비법", "공부 방법" 처럼
                  무엇에 관한 글인지만 알려주는 명사구는 후킹이 아니다.
                  주어진 글자 수를 짧게 남기지 말고 충분히 쓴다.
                - 조사로 끝내지 않는다. "실기 합격률은" 처럼 끝나면 말이 잘린 것이다.
                - 대본의 특정 컷 문장을 그대로 옮기지 않는다. 그건 제목이 아니라 자막의 반복이다.
                - 5개는 서로 다른 각도로 만든다. 가장 끌리는 것을 맨 앞에 둔다.
                  · 얻는 것: 보고 나면 무엇이 생기는지
                  · 방법: 어떻게 하면 되는지
                  · 기간이나 수치: 원문에 있는 것만
                  · 손실회피: 모르고 넘어가면 잃는 것
                  · 궁금증: 답을 알고 싶게 만드는 질문
                - 없는 권위, 통계, 희소성을 지어내지 않는다.
                - 물음표·느낌표는 하나까지 쓸 수 있다. 따옴표·이모지·해시태그는 쓰지 않는다.

                [예시 - keyword 를 어떻게 줄이는지만 보여준다. hooks 는 여기 없으니 직접 만든다]
                - 원문 "클로드 AI 200%% 활용하는 방법 총정리" -> keyword "클로드 사용법"
                - 원문 "자동차정비기능사 합격률｜필기시험 난이도와 합격 공부방법" -> keyword "자동차정비기능사"
                  (hooks 에 "필기시험", "합격률", "난이도" 처럼 원문 제목 단어를 그대로 쓰면 안 된다)
                """.formatted(n, n);
    }

    public static String user(CrawledPost post, List<CutDraft> cuts) {
        StringBuilder sb = new StringBuilder();
        String title = post.title() == null || post.title().isBlank()
                ? "(제목 없음 - keyword 는 본문에서 핵심 주제어를 뽑아 만든다)"
                : post.title();
        sb.append("[블로그 원문 제목]\n").append(title).append("\n\n");

        String body = post.body() == null ? "" : post.body();
        if (body.length() > BODY_PREVIEW_CHARS) {
            body = body.substring(0, BODY_PREVIEW_CHARS) + " ...";
        }
        sb.append("[본문 앞부분]\n").append(body).append("\n\n");

        if (cuts != null && !cuts.isEmpty()) {
            sb.append("[영상 나레이션]\n");
            for (CutDraft cut : cuts) {
                sb.append("- ").append(cut.narration()).append('\n');
            }
        }
        return sb.toString();
    }

    // 후보가 전부 탈락했을 때 한 번 더 부르는 요청
    public static String retry(String rejectedReason) {
        return "\n[다시 요청]\n앞서 준 hooks 는 모두 쓸 수 없었다 (" + rejectedReason + ").\n"
                + "블로그 원문 제목·본문 단어를 쓰지 말고, 유입용 추천 문구로 hooks 5개를 다시 만들어라.";
    }
}
