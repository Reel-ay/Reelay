package com.reelay.pipeline.caption.service;

import com.reelay.video.dto.CutInput;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

// ASS 자막 파일 생성
public interface SubtitleWriter {

    Path write(UUID jobId, String title, List<CutInput> cuts);
}
