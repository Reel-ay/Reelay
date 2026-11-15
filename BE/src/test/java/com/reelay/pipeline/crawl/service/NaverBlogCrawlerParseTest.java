package com.reelay.pipeline.crawl.service;

import com.reelay.common.exception.BusinessException;
import com.reelay.common.exception.ErrorCode;
import com.reelay.pipeline.crawl.dto.CrawledImage;
import com.reelay.pipeline.crawl.dto.CrawledPost;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NaverBlogCrawlerParseTest {

    private static final String SOURCE_URL = "https://blog.naver.com/copper567/224453934072";
    private static final String BLOG_ID = "copper567";
    private static final String LOG_NO = "224453934072";

    private static String sampleHtml;

    private final NaverBlogCrawler crawler = new NaverBlogCrawler();

    @BeforeAll
    static void loadSample() throws IOException {
        try (InputStream in = NaverBlogCrawlerParseTest.class
                .getResourceAsStream("/crawl/postview-sample.html")) {
            if (in == null) {
                throw new IllegalStateException(
                        "src/test/resources/crawl/postview-sample.html이 없습니다.");
            }
            sampleHtml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private CrawledPost parseSample() {
        return crawler.parse(SOURCE_URL, BLOG_ID, LOG_NO, sampleHtml);
    }

    private static String html(String body) {
        return "<html><body><div class=\"se-main-container\">" + body + "</div></body></html>";
    }

    @Test
    @DisplayName("제목과 본문을 뽑는다")
    void extractsTitleAndBody() {
        CrawledPost post = parseSample();

        assertThat(post.sourceUrl()).isEqualTo(SOURCE_URL);
        assertThat(post.blogId()).isEqualTo(BLOG_ID);
        assertThat(post.logNo()).isEqualTo(LOG_NO);
        assertThat(post.title()).isNotBlank();
        assertThat(post.body()).hasSizeGreaterThan(1000);
        assertThat(post.paragraphCount()).isGreaterThan(50);
    }

    @Test
    @DisplayName("제로폭 공백이 남지 않는다")
    void removesZeroWidthSpace() {
        CrawledPost post = parseSample();

        assertThat(post.body()).doesNotContain("\u200b");
        assertThat(post.title()).doesNotContain("\u200b");
    }

    @Test
    @DisplayName("본문에 빈 줄이 없다")
    void hasNoBlankLineInBody() {
        CrawledPost post = parseSample();

        assertThat(post.body().lines()).allMatch(line -> !line.isBlank());
    }

    @Test
    @DisplayName("같은 이미지를 두 번 담지 않는다")
    void deduplicatesImages() {
        CrawledPost post = parseSample();

        assertThat(post.images()).hasSize(7);
        assertThat(post.images())
                .extracting(CrawledImage::url)
                .doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("src의 data-lazy-src를 읽는다")
    void readsLazySrcNotBlurThumbnail() {
        CrawledPost post = parseSample();

        assertThat(post.images())
                .extracting(CrawledImage::url)
                .allSatisfy(url -> {
                    assertThat(url).endsWith("?type=w966");
                    assertThat(url).doesNotContain("w80_blur");
                });
    }

    @Test
    @DisplayName("index 는 0부터 1씩 늘어난다")
    void assignsSequentialImageIndex() {
        CrawledPost post = parseSample();

        for (int i = 0; i < post.images().size(); i++) {
            assertThat(post.images().get(i).index()).isEqualTo(i);
        }
    }

    @Test
    @DisplayName("nearParagraph 는 줄어들지 않고 문단 수를 넘지 않는다")
    void keepsNearParagraphNonDecreasing() {
        CrawledPost post = parseSample();

        assertThat(post.images())
                .extracting(CrawledImage::nearParagraph)
                .isSorted();

        assertThat(post.images())
                .allSatisfy(image -> assertThat(image.nearParagraph())
                        .isLessThanOrEqualTo(post.paragraphCount()));
    }

    @Test
    @DisplayName("images는 수정할 수 없다")
    void returnsImmutableImages() {
        CrawledPost post = parseSample();

        assertThatThrownBy(() -> post.images().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("본문 영역이 없으면 CRAWL_EMPTY_BODY")
    void failsWhenBodyContainerMissing() {
        String noContainer = "<html><body><p>본문 영역 없음</p></body></html>";

        assertThatThrownBy(() -> crawler.parse(SOURCE_URL, BLOG_ID, LOG_NO, noContainer))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.CRAWL_EMPTY_BODY));
    }

    @Test
    @DisplayName("본문이 너무 짧으면 CRAWL_EMPTY_BODY")
    void failsWhenBodyTooShort() {
        String shortBody = html("""
                <p class="se-text-paragraph">짧은 글</p>
                <img data-lazy-src="https://x/a.jpg?type=w773">
                """);

        assertThatThrownBy(() -> crawler.parse(SOURCE_URL, BLOG_ID, LOG_NO, shortBody))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.CRAWL_EMPTY_BODY));
    }

    @Test
    @DisplayName("제목이 없어도 실패하지 않는다")
    void allowsMissingTitle() {
        CrawledPost post = crawler.parse(SOURCE_URL, BLOG_ID, LOG_NO,
                html("<p class=\"se-text-paragraph\">" + "가".repeat(150) + "</p>"));

        assertThat(post.title()).isEmpty();
        assertThat(post.body()).hasSize(150);
    }

    @Test
    @DisplayName("이미지가 없어도 실패하지 않는다")
    void allowsNoImages() {
        CrawledPost post = crawler.parse(SOURCE_URL, BLOG_ID, LOG_NO,
                html("<p class=\"se-text-paragraph\">" + "나".repeat(150) + "</p>"));

        assertThat(post.images()).isEmpty();
    }

    @Test
    @DisplayName("빈 문단이 nearParagraph를 밀지 않는다")
    void ignoresBlankParagraphsWhenCountingNearParagraph() {
        CrawledPost post = crawler.parse(SOURCE_URL, BLOG_ID, LOG_NO, html("""
                <p class="se-text-paragraph">%s</p>
                <p class="se-text-paragraph">\u200b</p>
                <p class="se-text-paragraph"></p>
                <img data-lazy-src="https://x/a.jpg?type=w773">
                """.formatted("다".repeat(150))));

        assertThat(post.paragraphCount()).isEqualTo(1);
        assertThat(post.images()).hasSize(1);
        assertThat(post.images().get(0).nearParagraph()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 사진이 다른 type으로 나와도 한 장이다")
    void deduplicatesSameImageWithDifferentType() {
        CrawledPost post = crawler.parse(SOURCE_URL, BLOG_ID, LOG_NO, html("""
                <p class="se-text-paragraph">%s</p>
                <img data-lazy-src="https://x/a.jpg?type=w80_blur">
                <img data-lazy-src="https://x/a.jpg?type=w773">
                <img data-lazy-src="https://x/b.jpg">
                """.formatted("라".repeat(150))));

        assertThat(post.images())
                .extracting(CrawledImage::url)
                .containsExactly("https://x/a.jpg?type=w966", "https://x/b.jpg?type=w966");
    }

    @Test
    @DisplayName("data-lazy-src가 없는 img는 무시한다")
    void ignoresImgWithoutLazySrc() {
        CrawledPost post = crawler.parse(SOURCE_URL, BLOG_ID, LOG_NO, html("""
                <p class="se-text-paragraph">%s</p>
                <img src="https://x/thumb.jpg?type=w80_blur">
                <img data-lazy-src="https://x/real.jpg?type=w773">
                """.formatted("마".repeat(150))));

        assertThat(post.images())
                .extracting(CrawledImage::url)
                .containsExactly("https://x/real.jpg?type=w966");
    }
}