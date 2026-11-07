package com.reelay.pipeline.crawl.service;

import com.reelay.pipeline.crawl.dto.CrawledPost;

public interface BlogCrawler {
    CrawledPost crawl(String sourceUrl);
}
