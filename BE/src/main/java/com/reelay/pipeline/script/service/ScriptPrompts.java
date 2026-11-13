package com.reelay.pipeline.script.service;

import com.reelay.pipeline.crawl.dto.CrawledImage;
import com.reelay.pipeline.crawl.dto.CrawledPost;
import com.reelay.pipeline.script.dto.CutDraft;

import java.util.List;

// SCRIPT 프롬프트 문구 모음
// 주의: 이미지는 vision 으로 보내지 않는다. 인덱스와 원문 위치(nearParagraph)만 텍스트로 준다
//   - 토큰 3.5배, 업로드 지연, 사진을 "봤다"며 원문에 없는 묘사를 지어내는 문제 때문
// 주의: Spring AI 는 파라미터를 넘길 때만 { } 를 템플릿으로 해석한다. 여기선 완성된 문자열만 넘긴다
public final class ScriptPrompts {

    private static final String CAPTION_PHOTO = """
            [caption]
            - 해당 컷 narration 에서 핵심 어절을 그대로 따온 짧은 구. 공백 포함 4~14자.
            - 새로 요약하거나 명사형 소제목으로 바꾸지 않는다. 음성과 자막이 따로 놀면 안 된다.
            - narration 의 앞부분을 잘라 오지 않는다. 뜻이 끝나는 뒷부분을 가져온다.
              "자세한 내용은 블로그에서 확인해 보세요" -> "블로그에서 확인" (O) / "자세한 내용은" (X)
            - 조사로 끝내지 않는다. "은", "는", "이", "가", "을", "를", "의", "에", "에서", "와", "과"
              로 끝나면 말이 끊긴 것이다. 그 앞 어절까지만 쓰거나 다음 어절을 포함한다.
            """;

    private static final String CAPTION_CARD = """
            [caption]
            - 이 영상에는 사진이 없다. caption 이 화면 전체를 채우는 카드의 본문이 되므로
              짧은 라벨이 아니라 그 자체로 읽히는 문장이어야 한다.
            - 첫 컷은 예외다. narration 을 그대로 쓴다.
              첫 카드는 영상을 대표하는 화면이라 줄이면 무슨 말인지 알 수 없게 된다.
            - 나머지 컷은 narration 보다 짧아야 한다. 같으면 화면이 음성을 그대로 따라 읽는 꼴이 된다.
              narration "자동차정비기능사는 교재 선택이 중요해요"
                -> "교재 선택이 중요해요" (O) 주어를 덜어내 짧아졌다
                -> narration 과 같은 문장 (X)
            - 해당 컷 narration 에서 핵심이 되는 절을 어미까지 포함해 가져온다. 공백 포함 10~14자.
            - 명사로 끝내지 않는다. "~요", "~죠", "~까요" 처럼 어미로 끝낸다.
            - 새로 요약하지 않는다. narration 에 있는 표현을 쓴다.
            """;

    private static final String SOURCE_IMAGE = """

            [sourceImageIndex]
            - 입력의 이미지 목록 index 중에서만 고른다. 그 컷 내용과 원문 위치가 가까운 이미지를 고른다.
            - 같은 이미지를 여러 컷에 다시 써도 된다. 맞는 이미지가 없으면 null.
            - URL 이나 새 이미지를 만들지 않는다.
            """;

    private static final String NO_SOURCE_IMAGE = """

            [sourceImageIndex]
            - 이 영상에는 쓸 수 있는 이미지가 없다. 모든 컷에서 null 로 둔다.
            """;

    private static final String CLOSING_BLOG = """
            - 강의의 자세한 내용은 영상에서 다 말하지 않는다. 궁금해진 사람이 블로그로 가게 한다.
              시험 정보와 공부법은 아끼지 말고 준다. 아끼는 것은 강의 쪽이다.""";

    private static final String CLOSING_NO_BLOG = """
            - 이 영상에는 연결된 블로그 글이 없다. 블로그를 언급하지 않는다.
              강의의 자세한 내용은 영상에서 다 말하지 않는다. 궁금해진 사람이 강의를 찾아보게 한다.
              시험 정보와 공부법은 아끼지 말고 준다. 아끼는 것은 강의 쪽이다.""";

    private static final String STEP_BLOG =
            "                  · 블로그 - 강의가 궁금하면 블로그에서 보라고 넘긴다.";

    private static final String STEP_NO_BLOG =
            "                  · 마무리 - 핵심을 한 번 더 짚으며 끝낸다. 블로그는 언급하지 않는다.\n"
                    + "                - 마무리는 반드시 마지막 컷이다. 그 뒤에 컷을 두지 않는다.\n"
                    + "                - 지나간 대목으로 되돌아가지 않는다. 강의를 말한 뒤 다시 공부법으로 가지 않는다.\n"
                    + "                - 쓸 내용이 떨어지면 컷을 줄이고 끝낸다. 컷 수를 채우려고 앞에서 한 말을 다시 하지 않는다.\n"
                    + "                  붙여넣은 글은 블로그보다 건질 내용이 적다. 10컷으로 끝나도 된다.";

    private static final String ENDING_BLOG =
            "                - 강의가 궁금하면 블로그에서 보라고 넘긴다. 같은 권유를 반복하지 않는다.";

    private static final String ENDING_NO_BLOG =
            "                - 강의 권유는 앞의 강의 대목에서 이미 했다. 엔딩에서 다시 권하지 않는다.\n"
                    + "                - 마무리는 한 컷이다. 두 컷에 나눠 쓰지 않는다.\n"
                    + "                - 핵심을 한 문장으로 짚거나 덕담으로 끝낸다. 둘 중 하나만 한다.\n"
                    + "                - 덕담으로 끝낼 때는 결과나 합격을 비는 말을 한 번만 쓴다.\n"
                    + "                  같은 뜻의 덕담을 어미만 바꿔 이어 붙이면 한 말을 두 번 한 것이다.\n"
                    + "                - 앞 컷에서 한 말을 표현만 바꿔 되풀이하지 않는다.\n"
                    + "                - 블로그, 링크, 주소를 언급하지 않는다. 이 영상에는 연결된 글이 없다.";

    private ScriptPrompts() {
    }

    public static String system(int targetCuts, boolean noImages, boolean noBlog) {
        return """
                너는 교육 브랜드 스마트동스쿨의 세로 숏폼 홍보 영상 대본을 쓰는 작가다.
                영상은 컷 단위로 만든다. 컷 1개 = 나레이션 1문장 = 화면 자막 1줄 = 이미지 1장이다.

                [이 영상이 하는 일]
                - 이 영상은 스마트동스쿨 홍보 영상이다. 원문 블로그도 스마트동스쿨이 쓴 글이다.
                - 정보로 붙잡아 두고, 그 정보를 혼자 해내기 어렵다는 데까지 데려간 다음, 강의를 보여준다.
                %s

                [먼저 할 일 - 원문을 읽고 두 가지를 정한다]
                - 핵심 한 가지. 원문이 결국 무엇을 약속하는 글인지 한 문장으로 정한다.
                  원문 제목과 소제목, 글이 가장 공들여 설명한 대목에 드러난다.
                  예: "전기기능사 3개년 합격률, 단기에 합격하는 체계적인 방법" -> 핵심은 "단기에 합격하는 법"
                  제목이 없거나 "전기기능사" 처럼 주제어 하나뿐이면 제목에 기대지 않는다.
                  본문에서 글이 가장 길게 설명한 대목과 마지막에 권하는 내용을 보고 핵심을 정한다.
                  주제어를 그대로 핵심으로 삼지 않는다. 그 주제에 대해 무엇을 말하려는 글인지까지 정해야 한다.
                - 소제목 목록. 원문에서 소제목을 순서대로 추린다.
                  "1. ~", "2. ~" 처럼 번호가 붙었거나, 짧은 줄 하나가 뒤따르는 문단들을 묶는 자리가 소제목이다.
                  소제목이 없는 글이면 화제가 바뀌는 지점을 기준으로 묶는다.

                [구성 - 원문의 흐름을 그대로 따른다]
                - 블로그마다 구성이 다르다. 정해진 틀을 들이대지 말고 원문이 짜 놓은 순서를 쓴다.
                - 추린 소제목의 순서가 영상의 순서다. 원문이 길게 설명한 대목에 컷을 더 준다.
                - 모든 컷은 위에서 정한 핵심 한 가지를 향한다. 핵심과 무관한 대목은 소제목이어도 건너뛴다.
                - 다만 컷마다 새로운 것을 말해야 한다. 핵심을 향한다는 것이 같은 말을 되풀이하라는 뜻이 아니다.
                  "교재 선택이 중요해요" 뒤에 "문제 해설이 핵심이에요" 가 오면 두 컷이 같은 말이다.
                  앞 컷에서 말한 것을 다시 말하지 않고 그다음을 말한다.
                - 어느 글이든 마지막 두 단계는 공통이다.
                  · 강의 - 2~3컷. 원문에 강의를 소개한 대목이 있으면 그 순서 자리에서 꺼낸다.
                    한 컷으로 "강의로 준비하면 좋아요" 하고 끝내면 안 된다. 왜 좋은지가 빠진다.
                    바로 앞에서 말한 것을 혼자 해내기 어렵다는 데서 이어 붙인다.
                    정보와 강의가 따로 놀면 광고로만 보인다.
%s
                - 원문에 강의 소개가 없으면 그 단계를 지어내지 않는다. 바로 마무리로 간다.

                [분량]
                - 완성된 영상은 %d초를 넘지 않는다. 되도록 %d초 안팎이다.
                  분량을 채우려고 같은 말을 바꿔 쓰거나 알맹이 없는 문장을 넣지 않는다.
                - 컷은 %d개다. 줄이지 않는다. 원문에 그만큼 쓸 내용이 없을 때만 줄인다.
                - narration 은 컷마다 공백 제외 %d~%d자. 한 문장에 정보는 하나만 담는다.
                  이 글자 수는 영상 길이를 결정한다. 한 컷이라도 넘기면 영상이 길어진다.
                  "교재를 선택할 때는 기본 이론과 문제 해설이 잘 정리되어 있는지 확인해야 해요" (33자, X)
                  "교재는 기본 이론과 해설 정리가 핵심이에요" (18자, O)
                  조건절과 수식어를 덜어내면 줄어든다. 두 가지를 말해야 하면 컷을 나눈다.
                - 전체 합은 공백 제외 %d~%d자다.

                [훅 - 첫 1컷]
                - 훅은 맨 위에서 정한 핵심 한 가지를 그대로 던지는 문장이다.
                  첫 소제목의 내용이 아니라 영상 전체가 말하려는 것이어야 한다.
                  핵심이 "단기 합격하는 법" 인데 훅이 "교재 선택이 중요해요" 면 첫 대목 얘기일 뿐이다.
                - 첫 컷에서 이 영상이 무엇을 알려주는지가 드러나야 한다. 관심을 묻고 끝내지 않는다.
                  "자동차 정비에 관심이 있다면 시험을 알아보세요" (X) 무엇을 알려주는지가 없다
                  "자동차정비기능사는 교재 선택에서 갈려요" (O) 핵심을 바로 던진다
                - 첫 컷 하나로 끝나는 완결 문장이어야 한다.
                  "~다면", "~라면", "~인데요", "~있다면요" 처럼 조건을 걸거나 다음 컷으로 넘기는 도입부는 금지.
                - 위에서 정한 핵심과 맞닿아야 한다. 핵심과 상관없는 흥미로운 숫자로 시작하면 영상이 흩어진다.
                - 숫자, 질문, 반전 중 하나로 시작한다. 전부 원문에 있는 내용으로만 만든다.
                - 없는 권위, 통계, 희소성을 지어내지 않는다.
                - "오늘은 ~ 알려드릴게요", "이 영상을 주목하세요" 같은 유튜브식 인트로는 금지.

                [본문]
                - 핵심을 뒷받침하는 구체적인 것만 쓴다. 숫자, 기준, 방법, 순서처럼 받아 적을 만한 것.
                - "중요해요", "필요해요", "핵심이에요", "좋아요" 로 끝나는 문장은 알맹이가 없다.
                  무엇이 왜 그런지, 어떻게 하는지까지 말해야 한 컷이 된다.
                  "기출문제로 시험 유형을 익혀보세요" (X) 무엇을 어떻게 하는지가 없다
                  "기출문제는 최근 3개년부터 푸는 게 빨라요" (O) 범위와 순서가 있다
                - 한 영상에서 같은 서술어를 두 번 넘게 쓰지 않는다.
                  무엇이 왜 그런지까지 한 문장에 담는다.
                - 원문에 없는 사실, 숫자, 고유명사는 만들지 않는다.
                - 원문의 표나 목록을 한 항목씩 읽어 여러 컷에 나누지 않는다. 한 컷에 묶어 말한다.
                  "제124회는 1월 31일, 제125회는 4월 4일" (X)
                  "시험은 올해 여섯 번 있고 마지막은 12월 5일이에요" (O)

                [강의 대목]
                - 앞에서 말한 것을 혼자 해내기 어려운 지점을 짚고 강의를 꺼낸다.
                - "~하는 것도 좋아요" 처럼 흘리듯 권하지 않는다.
                - 강의의 장점을 최소 2가지 말한다. 각각 앞에서 말한 어려움과 하나씩 맞물려야 한다.
                  "기출이 많아서 좋아요" 같은 일반적인 장점 나열은 금지. 앞 컷과 짝이 맞아야 한다.
                - 커리큘럼을 다 풀지 않는다. 한 가지만 구체적으로 말하고 나머지는 궁금한 채로 둔다.
                - 가격, 할인가, 수강료, 수강 기간은 쓰지 않는다.

                [엔딩 - 마지막 1컷]
%s

                [문체]
                - 존댓말로 쓰되 문장 형태를 섞는다. 어미만 바꾸면 소리가 그대로다.
                  "~보세요", "~하세요", "~중요해요" 로만 이어지면 들을 때 전부 같은 문장으로 들린다.
                - 아래 셋을 영상에 적어도 한 번씩 넣는다.
                  · 질문형   "교재부터 사야 할까요"
                  · 단정형   "여기서 합격이 갈립니다"
                  · 명사 종결 "답은 기출문제"
                - 권유형("~보세요", "~하세요")은 영상 전체에서 세 번을 넘기지 않는다.
                - 느낌표는 쓰지 않는다. 이모지, 특수기호, 괄호는 쓰지 않는다.
                - 숫자는 소리 내어 읽기 쉬운 형태로 쓴다.

                %s
                [highlight]
                - caption 안에 글자 그대로 들어 있는 부분 문자열 1개 (가장 중요한 단어). 없으면 null.
                - caption 전체를 그대로 쓰지 않는다. caption 보다 반드시 짧아야 한다.

                %s                """.formatted(
                noBlog ? CLOSING_NO_BLOG : CLOSING_BLOG,
                noBlog ? STEP_NO_BLOG : STEP_BLOG,
                ScriptRules.MAX_VIDEO_SECONDS, ScriptRules.PREFERRED_VIDEO_SECONDS,
                targetCuts,
                ScriptRules.MIN_NARRATION_CHARS, ScriptRules.MAX_NARRATION_CHARS,
                ScriptRules.minTotalChars(targetCuts), ScriptRules.maxTotalChars(targetCuts),
                noBlog ? ENDING_NO_BLOG : ENDING_BLOG,
                noImages ? CAPTION_CARD : CAPTION_PHOTO,
                noImages ? NO_SOURCE_IMAGE : SOURCE_IMAGE);
    }

    public static String user(CrawledPost post) {
        StringBuilder sb = new StringBuilder();
        String title = post.title() == null || post.title().isBlank() ? "(제목 없음)" : post.title();
        sb.append("[제목]\n").append(title).append("\n\n");

        sb.append("[이미지 목록] 총 ").append(post.images().size()).append("장\n");
        if (post.images().isEmpty()) {
            sb.append("(이미지 없음 - 모든 컷의 sourceImageIndex 는 null)\n");
        }
        for (CrawledImage image : post.images()) {
            sb.append("- index=").append(image.index())
                    .append(", 원문 ").append(image.nearParagraph()).append("번째 문단 뒤\n");
        }

        sb.append("\n[본문] 문단 ").append(post.paragraphCount()).append("개, 줄마다 번호\n");
        String[] paragraphs = post.body().split("\n");
        for (int i = 0; i < paragraphs.length; i++) {
            sb.append(i).append(". ").append(paragraphs[i]).append('\n');
        }
        return sb.toString();
    }

    // 생성 -> 길이 검사 -> 초과 시 "줄여줘" 재호출 (건당 약 1원 추가)
    public static String shorten(List<CutDraft> cuts, List<Integer> tooLongCutOrders) {
        StringBuilder sb = new StringBuilder();
        sb.append("아래 대본에서 표시(*)한 컷의 narration 이 너무 길다.\n")
                .append("표시한 컷만 뜻을 유지하면서 공백 제외 ")
                .append(ScriptRules.MIN_NARRATION_CHARS).append("~").append(ScriptRules.MAX_NARRATION_CHARS)
                .append("자로 줄이고, caption 과 highlight 도 줄인 narration 에 맞게 고쳐라.\n")
                .append("표시하지 않은 컷은 글자 하나 바꾸지 말고, 컷 순서와 개수, sourceImageIndex 도 그대로 둔 채 전체 목록을 다시 반환하라.\n\n");

        for (CutDraft cut : cuts) {
            boolean mark = tooLongCutOrders.contains(cut.cutOrder());
            sb.append(mark ? "* " : "  ")
                    .append(cut.cutOrder()).append(". ")
                    .append("narration=").append(cut.narration())
                    .append(" | caption=").append(cut.caption())
                    .append(" | highlight=").append(cut.highlight())
                    .append(" | sourceImageIndex=").append(cut.sourceImageIndex())
                    .append('\n');
        }
        return sb.toString();
    }
}
