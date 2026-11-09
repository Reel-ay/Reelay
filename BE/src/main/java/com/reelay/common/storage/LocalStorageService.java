package com.reelay.common.storage;

import com.reelay.common.exception.BusinessException;
import com.reelay.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

@Component
public class LocalStorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalStorageService.class);

    private static final String IMAGES = "images";
    private static final String AUDIO = "audio";
    private static final String TMP = "tmp";
    private static final String SUBTITLE_FILE = "subs.ass";
    private static final String OUTPUT_FILE = "out.mp4";
    private static final String IMAGE_EXT = ".jpg";
    private static final String AUDIO_EXT = ".wav";

    private final Path root;

    public LocalStorageService(@Value("${reelay.storage.root:${user.home}/.reelay/storage}") String root) {
        this.root = Paths.get(root).toAbsolutePath().normalize();
        log.info("저장 루트 : {}", this.root);
    }

    @Override
    public Path imagePath(UUID jobId, int sourceImageIndex) {
        return jobDir(jobId).resolve(IMAGES).resolve(sourceImageIndex + IMAGE_EXT);
    }

    @Override
    public Path audioPath(UUID jobId, int cutOrder) {
        return jobDir(jobId).resolve(AUDIO).resolve(cutOrder + AUDIO_EXT);
    }

    @Override
    public Path subtitlePath(UUID jobId) {
        return jobDir(jobId).resolve(SUBTITLE_FILE);
    }

    @Override
    public Path outputPath(UUID jobId) {
        return jobDir(jobId).resolve(OUTPUT_FILE);
    }

    @Override
    public Path tmpDir(UUID jobId) {
        return jobDir(jobId).resolve(TMP);
    }

    // 작업 시작 시 폴더 생성
    @Override
    public void prepare(UUID jobId) {
        createDirectories(jobDir(jobId).resolve(IMAGES));
        createDirectories(jobDir(jobId).resolve(AUDIO));
        createDirectories(tmpDir(jobId));
    }

    // 파일을 열어 읽기 스트림 반환
    @Override
    public InputStream read(Path path) {
        Path target = insideRoot(path);
        if(!Files.isRegularFile(target)) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        try {
            return Files.newInputStream(target);
        }
        catch(IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // 파일 크기(바이트), 다운로드 응답의 Content-Length 용
    // 파일이 없거나 접근 불가 -> FILE_NOT_FOUND
    @Override
    public long size(Path path) {
        Path target = insideRoot(path);
        try {
            return Files.size(target);
        }
        catch(IOException e) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
    }

    // 읽을 수 있는 파일인지 확인
    @Override
    public boolean isFile(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        return normalized.startsWith(root) && Files.isRegularFile(normalized);
    }

    // 임시 파일 제거
    @Override
    public void cleanTmp(UUID jobId) {
        Path tmp = tmpDir(jobId);
        if(!Files.isDirectory(tmp)) {
            return;
        }

        try(Stream<Path> walk = Files.walk(tmp)) {
            walk.sorted(Comparator.reverseOrder())
                    .filter(p -> !p.equals(tmp))
                    .forEach(this::deleteQuietly);
        }
        catch(IOException e) {
            log.warn("임시 디렉터리 순회 실패 - {}", tmp, e);
        }
    }

    // 경로 조합
    private Path jobDir(UUID jobId) {
        return root.resolve(jobId.toString());
    }

    // 루트 밖 접근 차단
    private Path insideRoot(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if(!normalized.startsWith(root)) {
            log.warn("루트 밖 접근 차단 - {}", normalized);
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        return normalized;
    }

    // 부모 폴더까지 생성
    private void createDirectories(Path dir) {
        try {
            Files.createDirectories(dir);
        }
        catch(IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // 삭제 실패는 로그만
    private void deleteQuietly(Path path) {
        try {
            Files.delete(path);
        }
        catch(IOException e) {
            log.warn("임시 파일 삭제 실패 - {}", path, e);
        }
    }
}
