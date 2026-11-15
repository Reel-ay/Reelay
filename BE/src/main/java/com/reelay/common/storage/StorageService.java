package com.reelay.common.storage;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.UUID;

// 경로 규칙
//   {jobId}/images/{cutOrder}.jpg
//   {jobId}/audio/{cutOrder}.wav
//   {jobId}/uploads/{n}.jpg      (본문 직접 입력 때 올린 사진)
//   {jobId}/subs.ass
//   {jobId}/out.mp4
//   {jobId}/tmp/
public interface StorageService {

    Path imagePath(UUID jobId, int sourceImageIndex);

    Path audioPath(UUID jobId, int cutOrder);

    // 본문 직접 입력 모드에서 사용자가 올린 사진 (JPEG 로 변환해 저장)
    Path uploadPath(UUID jobId, int uploadIndex);

    Path subtitlePath(UUID jobId);

    Path outputPath(UUID jobId);

    Path tmpDir(UUID jobId);

    void prepare(UUID jobId);

    InputStream read(Path path);

    long size(Path path);

    boolean isFile(Path path);

    void cleanTmp(UUID jobId);
}
