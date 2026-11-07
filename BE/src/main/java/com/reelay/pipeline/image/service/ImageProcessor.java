package com.reelay.pipeline.image.service;

import com.reelay.pipeline.script.dto.CutDraft;
import com.reelay.pipeline.image.dto.CutImage;

import java.util.List;
import java.util.UUID;

// 이미지 다운로드와 컷 배정. 크롭은 VideoRenderer 가 한다
public interface ImageProcessor {

    List<CutImage> process(UUID jobId, List<String> imageUrls, List<CutDraft> cuts);
}
