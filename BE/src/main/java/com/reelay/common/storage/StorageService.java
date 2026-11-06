package com.reelay.common.storage;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.UUID;

// 경로 규칙
//   {jobId}/images/{cutOrder}.jpg
//   {jobId}/audio/{cutOrder}.wav
//   {jobId}/subs.ass
//   {jobId}/out.mp4
//   {jobId}/tmp/
public interface StorageService {

    Path imagePath(UUID jobId, int cutOrder);

    Path audioPath(UUID jobId, int cutOrder);

    Path subtitlePath(UUID jobId);

    Path outputPath(UUID jobId);

    Path tmpDir(UUID jobId);

    void prepare(UUID jobId);

    InputStream read(Path path);

    long size(Path path);

    boolean exists(Path path);

    void cleanTmp(UUID jobId);
}
