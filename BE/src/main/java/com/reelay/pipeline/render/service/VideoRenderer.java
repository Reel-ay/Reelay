package com.reelay.pipeline.render.service;

import com.reelay.video.dto.CutInput;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

// FFmpeg 합성. 1080x1920 / 30fps
public interface VideoRenderer {

    Path render(UUID jobId, List<CutInput> cuts, Path subtitleFile);
}
