package com.reelay.common.exception;

public class CrawlFailedException extends BusinessException{
    public CrawlFailedException() {
        super(ErrorCode.CRAWL_FAILED);
    }
}
