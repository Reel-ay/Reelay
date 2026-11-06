package com.reelay.pipeline.caption;

import com.reelay.pipeline.dto.CutInput;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

// ASS 자막 파일 생성
public interface AssSubtitleWriter {

    Path write(UUID jobId, String title, List<CutInput> cuts);
}
