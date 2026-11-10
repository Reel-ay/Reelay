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

                [keyword - 첫 줄, 흰 글씨]
                - 블로그 글의 핵심 주제어. 블로그 내용에서 벗어나지 않는다. 공백 포함 %d자 이내.
                - 원문 제목에서 SEO 수식어(합격률, 총정리, 꿀팁, 후기, 난이도 같은 덧붙임)를 걷어낸 형태.
                - 자격증·시험·제품 이름은 줄이지 말고 온전히 쓴다. 넘치면 뒤의 설명어를 버린다.
                - 명사구. 문장부호·이모지를 쓰지 않는다.

                [hooks - 둘째 줄 후보 5개, 노란 형광펜]
                - 블로그 문장이 아니라, 시청자가 스크롤을 멈추고 누르게 만드는 '유입용 추천 문구'다. 각각 공백 포함 %d자 이내.
                - 블로그 원문 제목이나 본문에 있는 단어를 그대로 옮기지 않는다. 첫 줄(keyword)의 단어도 되풀이하지 않는다.
                - 주제 범주는 글과 어긋나지 않게 한다. (자격증 글이면 공부·합격 관련, 제품 글이면 사용·구매 관련)
                - 원문에 없는 구체적 숫자·결과·약속은 지어내지 않는다 (낚시 금지). 숫자는 본문에서 셀 수 있는 개수만.
                - 5개는 서로 다른 유형으로 만든다. 가장 끌리는 것을 맨 앞에 둔다.
                  · 숫자형: TOP5, 3단계 정리, 딱 이것만
                  · 이득형: 합격 지름길, 시간 절약법
                  · 손실회피형: 모르면 손해, 이건 피하세요
                  · 대상지정형: 초보 필수, 직장인 필독
                  · 궁금증형: 진짜 이유, 의외의 정답
                - 물음표·느낌표는 하나까지 쓸 수 있다. 따옴표·이모지·해시태그는 쓰지 않는다.

                [예시]
                - 원문 "클로드 AI 200%% 활용하는 방법 총정리" -> keyword "클로드 사용법", hooks ["TOP9", "모르면 손해", ...]
                - 원문 "자동차정비기능사 합격률｜필기시험 난이도와 합격 공부방법" -> keyword "자동차정비기능사", hooks ["합격 지름길", "독학 3단계", ...]
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
