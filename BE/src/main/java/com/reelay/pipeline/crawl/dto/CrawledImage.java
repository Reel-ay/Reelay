package com.reelay.pipeline.crawl.dto;

public record CrawledImage(
        int index,
        String url,
        int nearParagraph   // 이미지 앞까지 등장한 문장 수
) {
}
