package com.reelay.pipeline.crawl.service;

import com.reelay.common.exception.BusinessException;
import com.reelay.common.exception.ErrorCode;
import com.reelay.pipeline.crawl.dto.CrawledImage;
import com.reelay.pipeline.crawl.dto.CrawledPost;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("network")
class NaverBlogCrawlerNetworkTest {

    private static final String REAL_POST = "https://blog.naver.com/copper567/224453934072";
    private static final String DELETED_POST = "https://blog.naver.com/copper567/999999999999";
    private static final String NO_SUCH_BLOG = "https://blog.naver.com/thisblogdoesnotexist12345/224453934072";

    private final NaverBlogCrawler crawler = new NaverBlogCrawler();

    @Test
    @DisplayName("실제 글을 끝까지 읽는다")
    void crawlsRealPost() {
        CrawledPost post = crawler.crawl(REAL_POST);

        assertThat(post.title()).isNotBlank();
        assertThat(post.body()).hasSizeGreaterThan(500);
        assertThat(post.paragraphCount()).isGreaterThan(10);
        assertThat(post.images()).isNotEmpty();

        assertThat(post.images())
                .extracting(CrawledImage::url)
                .allSatisfy(url -> {
                    assertThat(url).startsWith("https://");
                    assertThat(url).endsWith("?type=w966");
                    assertThat(url).doesNotContain("w80_blur");
                })
                .doesNotHaveDuplicates();

        assertThat(post.images())
                .extracting(CrawledImage::nearParagraph)
                .isSorted();
    }

    @Test
    @DisplayName("없는 글은 CRAWL_EMPTY_BODY 로 실패한다")
    void failsOnDeletedPost() {
        assertThatThrownBy(() -> crawler.crawl(DELETED_POST))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.CRAWL_EMPTY_BODY));
    }

    @Test
    @DisplayName("없는 블로그는 CRAWL_EMPTY_BODY 로 실패한다")
    void failsOnMissingBlog() {
        assertThatThrownBy(() -> crawler.crawl(NO_SUCH_BLOG))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.CRAWL_EMPTY_BODY));
    }
}