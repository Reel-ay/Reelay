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

    private ScriptPrompts() {
    }

    public static String system(int targetCuts) {
        return """
                너는 블로그 글을 30~60초 세로 숏폼 영상 나레이션으로 바꾸는 작가다.
                영상은 컷 단위로 만든다. 컷 1개 = 나레이션 1문장 = 화면 자막 1줄 = 이미지 1장이다.

                [분량]
                - 컷은 정확히 %d개. (훅 1~2컷, 엔딩 1~2컷, 나머지는 본문)
                  원문이 짧으면 내용을 부풀리지 말고 원문에 있는 만큼만 쓴다.
                - narration 은 공백 제외 %d~%d자. 이 글자 수는 영상 길이를 결정하므로 반드시 지킨다.
                  한 문장에 정보는 하나만 담는다. 길어지면 두 컷으로 나눈다.

                [훅 - 첫 1~2컷]
                - 스크롤을 멈추게 하는 문장. 숫자, 질문, 반전, 의외의 사실 중 하나로 시작한다.
                - "오늘은 ~ 알려드릴게요", "이 영상을 주목하세요" 같은 유튜브식 인트로는 금지.

                [본문]
                - 원문 요약을 나열하지 말고, 시청자가 얻는 이득(왜 알아야 하는지, 어떻게 쓰는지)으로 바꿔 말한다.
                - 원문에 없는 사실, 숫자, 고유명사는 만들지 않는다.

                [엔딩 - 마지막 1~2컷]
                - "자세한 내용은 블로그에서 확인해 보세요" 처럼 원문으로 이끄는 문장.

                [문체]
                - 어미를 "~요" 체로 통일한다. 느낌표는 쓰지 않는다. 이모지, 특수기호, 괄호는 쓰지 않는다.
                - 숫자는 소리 내어 읽기 쉬운 형태로 쓴다.

                [caption]
                - 해당 컷 narration 에서 핵심 어절을 그대로 따온 짧은 구. 공백 포함 4~14자.
                - 새로 요약하거나 명사형 소제목으로 바꾸지 않는다. 음성과 자막이 따로 놀면 안 된다.

                [highlight]
                - caption 안에 글자 그대로 들어 있는 부분 문자열 1개 (가장 중요한 단어). 없으면 null.

                [sourceImageIndex]
                - 입력의 이미지 목록 index 중에서만 고른다. 그 컷 내용과 원문 위치가 가까운 이미지를 고른다.
                - 같은 이미지를 여러 컷에 다시 써도 된다. 맞는 이미지가 없으면 null.
                - URL 이나 새 이미지를 만들지 않는다.
                """.formatted(targetCuts, ScriptRules.MIN_NARRATION_CHARS, ScriptRules.MAX_NARRATION_CHARS);
    }

    public static String user(CrawledPost post) {
        StringBuilder sb = new StringBuilder();
        sb.append("[제목]\n").append(post.title()).append("\n\n");

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
