package com.reelay.pipeline.manual.dto;

import com.reelay.pipeline.crawl.dto.CrawledImage;
import com.reelay.pipeline.crawl.dto.CrawledPost;
import com.reelay.video.dto.VideoTitle;
import com.reelay.video.enums.TitleMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ManualInputTest {

    private static final String BODY = "첫 문단입니다\r\n\r\n  둘째 문단입니다  \n셋째 문단\n\n넷째 문단";

    @Test
    @DisplayName("줄바꿈으로 문단을 나누고 빈 줄은 버린다")
    void splitsParagraphs() {
        CrawledPost post = new ManualInput(TitleMode.AUTO, null, null, BODY, List.of()).toPost();

        assertThat(post.paragraphCount()).isEqualTo(4);
        assertThat(post.body()).isEqualTo("첫 문단입니다\n둘째 문단입니다\n셋째 문단\n넷째 문단");
        assertThat(post.sourceUrl()).isNull();
        assertThat(post.images()).isEmpty();
    }

    @Test
    @DisplayName("사진은 올린 순서대로 문단에 고르게 배치한다")
    void spreadsImages() {
        CrawledPost post = new ManualInput(TitleMode.AUTO, null, null, BODY,
                List.of("file:/a/0.jpg", "file:/a/1.jpg")).toPost();

        assertThat(post.images()).extracting(CrawledImage::index).containsExactly(0, 1);
        assertThat(post.images()).extracting(CrawledImage::nearParagraph).containsExactly(0, 2);
        assertThat(post.images().get(1).url()).isEqualTo("file:/a/1.jpg");
    }

    @Test
    @DisplayName("직접 작성이면 그 제목이 글 제목·영상 제목, 자동 생성이면 둘 다 null")
    void titleByMode() {
        ManualInput manual = new ManualInput(TitleMode.MANUAL, "제주 한달살기", "비용 총정리", BODY, List.of());
        ManualInput auto = new ManualInput(TitleMode.AUTO, null, null, BODY, List.of());

        assertThat(manual.fixedTitle()).isEqualTo(new VideoTitle("제주 한달살기", "비용 총정리"));
        assertThat(manual.toPost().title()).isEqualTo("제주 한달살기");
        assertThat(auto.fixedTitle()).isNull();
        assertThat(auto.toPost().title()).isNull();
    }
}
