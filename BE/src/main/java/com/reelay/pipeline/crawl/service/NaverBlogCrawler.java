package com.reelay.pipeline.crawl.service;

import com.reelay.common.exception.BusinessException;
import com.reelay.common.exception.CrawlFailedException;
import com.reelay.common.exception.ErrorCode;
import com.reelay.pipeline.crawl.dto.CrawledImage;
import com.reelay.pipeline.crawl.dto.CrawledPost;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class NaverBlogCrawler implements BlogCrawler{

    private static final String POSTVIEW =
            "https://blog.naver.com/PostView.naver"
                    + "?blogId=%s&logNo=%s"
                    + "&redirect=Dlog&widgetTypeCall=true&directAccess=false";

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    private static final int TIMEOUT_MS = 20_000;

    // 최소 텍스트 분량
    private static final int MIN_BODY_CHARS = 100;

    private static final String BODY_SELECTOR = ".se-main-container";
    private static final String TOKEN_SELECTOR = "p.se-text-paragraph, img[data-lazy-src]";
    private static final String TITLE_SELECTOR = "meta[property=og:title]";

    private static final String ZERO_WIDTH_SPACE = "\u200b";
    private static final String IMAGE_SIZE_QUERY = "?type=w966";

    @Override
    @Retryable(includes = CrawlFailedException.class, multiplier = 2.0)
    public CrawledPost crawl(String sourceUrl) {
        Target target = parseTarget(sourceUrl);
        String html = fetch(String.format(POSTVIEW, target.blogId(), target.logNo()));
        return parse(sourceUrl, target.blogId(), target.logNo(), html);
    }

    Target parseTarget(String sourceUrl) {
        UriComponents components;
        try {
            components = UriComponentsBuilder.fromUriString(sourceUrl).build();
        }
        catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_URL);
        }

        String host = components.getHost();
        if(host == null) {
            throw new BusinessException(ErrorCode.INVALID_URL);
        }

        host = host.toLowerCase(Locale.ROOT);

        if(!host.endsWith("blog.naver.com") && !host.endsWith("blog.me")) {
            throw new BusinessException(ErrorCode.INVALID_URL);
        }

        var query = components.getQueryParams();
        String blogId = query.getFirst("blogId");
        String logNo = query.getFirst("logNo");
        if(blogId != null && !blogId.isBlank() && isNumeric(logNo)) {
            return new Target(blogId, logNo);
        }

        String path = components.getPath() == null ? "" : components.getPath();
        List<String> segments = new ArrayList<>();
        for(String part : path.split("/")) {
            if(!part.isBlank()) {
                segments.add(part);
            }
        }

        if(segments.size() >= 2 && isNumeric(segments.get(segments.size() - 1))) {
            return new Target(segments.get(segments.size() - 2), segments.get(segments.size() - 1));
        }

        throw new BusinessException(ErrorCode.INVALID_URL);
    }

    private boolean isNumeric(String s) {
        if(s==null || s.isEmpty()) {
            return false;
        }

        for(int i=0; i<s.length(); i++) {
            if(!Character.isDigit(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private String fetch(String url) {
        try {
            return Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .execute()
                    .body();
        }
        catch(HttpStatusException e) {
            if(e.getStatusCode() == 404) {
                throw new BusinessException(ErrorCode.CRAWL_EMPTY_BODY);
            }
            throw new CrawlFailedException();
        }
        catch(IOException e) {
            throw new CrawlFailedException();
        }
    }

    CrawledPost parse(String sourceUrl, String blogId, String logNo, String html) {
        Document document = Jsoup.parse(html);

        Element container = document.selectFirst(BODY_SELECTOR);
        if(container == null) {
            throw new BusinessException(ErrorCode.CRAWL_EMPTY_BODY);
        }

        Element titleMeta = document.selectFirst(TITLE_SELECTOR);
        String title = titleMeta == null ? "" : titleMeta.attr("content").trim();

        List<String> paragraphs = new ArrayList<>();
        List<CrawledImage> images = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for(Element element : container.select(TOKEN_SELECTOR)) {
            if("img".equals(element.tagName())) {
                String url = normalizeImageUrl(element.attr("data-lazy-src"));
                if(url == null || !seen.add(url)) {
                    continue;
                }
                images.add(new CrawledImage(images.size(), url, paragraphs.size()));
                continue;
            }

            String text = clean(element.text());
            if(!text.isEmpty()) {
                paragraphs.add(text);
            }
        }

        String body = String.join("\n", paragraphs);
        if(body.length() < MIN_BODY_CHARS) {
            throw new BusinessException(ErrorCode.CRAWL_EMPTY_BODY);
        }

        return new CrawledPost(sourceUrl, blogId, logNo, title, body, paragraphs.size(), List.copyOf(images));
    }

    private String clean(String text) {
        return text.replace(ZERO_WIDTH_SPACE, "").trim();
    }

    private String normalizeImageUrl(String raw) {
        if(raw == null || raw.isBlank()) {
            return null;
        }

        int q = raw.indexOf('?');
        String base = q < 0 ? raw : raw.substring(0, q);
        return base + IMAGE_SIZE_QUERY;
    }

    record Target(String blogId, String logNo) {
    }
}
