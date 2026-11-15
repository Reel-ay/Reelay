package com.reelay.pipeline.caption.service;

import com.reelay.video.dto.CutInput;
import com.reelay.video.dto.VideoTitle;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

// ASS 자막 파일 생성
public interface SubtitleWriter {

    Path write(UUID jobId, String title, List<CutInput> cuts);

    // 두 줄 제목 (첫 줄 키워드 + 둘째 줄 강조 문구)
    // 기존 write 와 이름을 다르게 둔 이유: write(jobId, null, cuts) 호출이 모호해지지 않게
    // 기본 구현은 첫 줄만 기존 방식으로 쓴다
    default Path writeWithTitle(UUID jobId, VideoTitle title, List<CutInput> cuts) {
        return write(jobId, title == null ? null : title.keyword(), cuts);
    }
}
