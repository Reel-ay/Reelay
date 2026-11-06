package com.reelay.pipeline.crawl.service;

import com.reelay.common.exception.BusinessException;
import com.reelay.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class NaverBlogCrawlerUrlTest {
    private final NaverBlogCrawler crawler = new NaverBlogCrawler();

    @ParameterizedTest
    @CsvSource({
            "https://blog.naver.com/copper567/224453934072, copper567, 224453934072",
            "https://blog.naver.com/copper567/224453934072/, copper567, 224453934072",
            "https://m.blog.naver.com/copper567/224453934072, copper567, 224453934072",
            "https://BLOG.NAVER.COM/copper567/224453934072, copper567, 224453934072",
            "https://blog.me/copper567/224453934072, copper567, 224453934072",
            "https://blog.naver.com/PostView.naver?blogId=copper567&logNo=224453934072, copper567, 224453934072"
    })
    @DisplayName("경로형과 쿼리형 URL에서 blogId 와 logNo를 뽑는다")
    void parseBlogIdLogNo(String url, String blogId, String logNo) {
        NaverBlogCrawler.Target target = crawler.parseTarget(url);

        assertThat(target.blogId()).isEqualTo(blogId);
        assertThat(target.logNo()).isEqualTo(logNo);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://tistory.com/copper567/224453934072",
            "https://velog.io/@x/224453934072",
            "https://evil.com/?x=blog.naver.com",
            "https://blog.naver.com/copper567",
            "https://blog.naver.com/copper567/about",
            "https://blog.naver.com",
            "https://blog.naver.com/PostView.naver?blogId=&logNo=224453934072",
            "https://blog.naver.com/PostView.naver?logNo=224453934072",
            "https://blog.naver.com/PostView.naver?blogId=copper567&logNo=abc",
            "copper567/224453934072",
            "테스트 용 텍스트입니다."
    })
    @DisplayName("네이버 블로그 글 주소가 아니면 INVALID_URL")
    void rejectsNonNaverPostUrl(String url) {
        assertThatThrownBy(() -> crawler.parseTarget(url))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_URL));
    }
}
