package com.reelay.pipeline.crawl.dto;

import java.util.List;

public record CrawledPost(
        String sourceUrl,
        String blogId,
        String logNo,
        String title,
        String body,
        int paragraphCount,
        List<CrawledImage> images
) {
}
