package com.reelay.common.storage;

import com.reelay.common.exception.BusinessException;
import com.reelay.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatCode;

public class LocalStorageServiceTest {

    @TempDir
    Path tempDir;

    @TempDir
    Path outside;

    private LocalStorageService storage;
    private UUID jobId;

    @BeforeEach
    void setUp() {
        storage = new LocalStorageService(tempDir.toString());
        jobId = UUID.randomUUID();
    }

    @Test
    @DisplayName("경로 규칙대로 주소를 만든다")
    void buildsPathsByConvention() {
        Path jobDir = tempDir.resolve(jobId.toString());

        assertThat(storage.imagePath(jobId, 0)).isEqualTo(jobDir.resolve("images").resolve("0.jpg"));
        assertThat(storage.audioPath(jobId, 3)).isEqualTo(jobDir.resolve("audio").resolve("3.wav"));
        assertThat(storage.subtitlePath(jobId)).isEqualTo(jobDir.resolve("subs.ass"));
        assertThat(storage.outputPath(jobId)).isEqualTo(jobDir.resolve("out.mp4"));
        assertThat(storage.tmpDir(jobId)).isEqualTo(jobDir.resolve("tmp"));

        assertThat(Files.exists(jobDir)).isFalse();
    }

    @Test
    @DisplayName("prepare는 폴더를 만들고 여러 번 불러도 안전하다")
    void prepareCreatesDirectoriesAndIsIdempotent() {
        storage.prepare(jobId);
        storage.prepare(jobId);

        Path jobDir = tempDir.resolve(jobId.toString());
        assertThat(jobDir.resolve("images")).isDirectory();
        assertThat(jobDir.resolve("audio")).isDirectory();
        assertThat(jobDir.resolve("tmp")).isDirectory();
    }

    @Test
    @DisplayName("루트 밖 파일은 읽지 못한다")
    void blocksAccessOutsideRoot() throws IOException {
        Path secret = Files.writeString(outside.resolve("secret.txt"), "비밀");

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> storage.read(secret))
                .satisfies(e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FILE_NOT_FOUND));

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> storage.size(secret));

        assertThat(storage.isFile(secret)).isFalse();
        assertThat(Files.exists(secret)).isTrue();
    }

    @Test
    @DisplayName("..로 루트를 빠져나가는 경로를 막는다")
    void blocksParentTraversal() throws IOException {
        Path secret = Files.writeString(outside.resolve("secret.txt"), "비밀");
        Path disguised = tempDir.resolve("..")
                .resolve(outside.getFileName())
                .resolve("secret.txt");

        assertThat(disguised.startsWith(tempDir)).isTrue();
        assertThat(disguised.normalize()).isEqualTo(secret);

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> storage.read(disguised));
        assertThat(storage.isFile(disguised)).isFalse();
    }

    @Test
    @DisplayName("cleanTmp는 내용만 비우고 디렉터리는 남긴다")
    void cleanTmpEmptiesButKeepsDirectory() throws IOException {
        storage.prepare(jobId);
        Path tmp = storage.tmpDir(jobId);

        Files.writeString(tmp.resolve("cut0.mp4"), "x");
        Path nested = Files.createDirectories(tmp.resolve("sub"));
        Files.writeString(nested.resolve("x.txt"), "y");

        storage.cleanTmp(jobId);

        assertThat(tmp).isDirectory();
        try(Stream<Path> children = Files.list(tmp)) {
            assertThat(children).isEmpty();
        }
    }

    @Test
    @DisplayName("tmp가 없어도 cleanTmp는 조용히 끝난다")
    void cleanTmpIgnoresMissingDirectory() {
        assertThatCode(() -> storage.cleanTmp(jobId)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("루트 안 파일은 내용을 읽는다")
    void readsFileInsideRoot() throws IOException {
        storage.prepare(jobId);
        Path image = storage.imagePath(jobId, 0);
        Files.writeString(image, "이미지 내용");

        assertThat(storage.isFile(image)).isTrue();
        assertThat(storage.size(image)).isEqualTo(Files.size(image));

        try(InputStream in = storage.read(image)) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8))
                    .isEqualTo("이미지 내용");
        }
    }
}