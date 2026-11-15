package com.reelay.pipeline.manual.service;

import com.reelay.common.exception.BusinessException;
import com.reelay.common.exception.ErrorCode;
import com.reelay.common.storage.LocalStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ManualImageStoreTest {

    private LocalStorageService storage;
    private ManualImageStore store;

    @BeforeEach
    void setUp(@TempDir Path root) {
        storage = new LocalStorageService(root.toString());
        store = new ManualImageStore(storage);
    }

    @Test
    @DisplayName("사진 없음·빈 파일 칸은 0장으로 통과한다")
    void noImages() {
        assertThat(store.decode(null)).isEmpty();
        assertThat(store.decode(List.of(new MockMultipartFile("images", "", "image/png", new byte[0])))).isEmpty();
    }

    @Test
    @DisplayName("PNG(투명)도 JPEG 로 {jobId}/uploads/{n}.jpg 에 저장한다")
    void savesAsJpeg() throws IOException {
        UUID jobId = UUID.randomUUID();
        List<BufferedImage> images = store.decode(List.of(
                file("a.png", "png", BufferedImage.TYPE_INT_ARGB),
                file("b.jpg", "jpg", BufferedImage.TYPE_INT_RGB)));

        List<String> uris = store.save(jobId, images);

        assertThat(uris).hasSize(2);
        Path first = Path.of(URI.create(uris.get(0)));
        assertThat(first).isEqualTo(storage.uploadPath(jobId, 0));
        assertThat(Files.exists(first)).isTrue();
        assertThat(ImageIO.read(first.toFile())).isNotNull();
    }

    @Test
    @DisplayName("사진이 아니면 INVALID_IMAGE")
    void rejectsNonImage() {
        MultipartFile text = new MockMultipartFile("images", "a.txt", "text/plain", "hello".getBytes());
        assertThatThrownBy(() -> store.decode(List.of(text)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_IMAGE);
    }

    @Test
    @DisplayName("11장 이상이면 TOO_MANY_IMAGES")
    void rejectsTooMany() throws IOException {
        List<MultipartFile> files = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            files.add(file(i + ".jpg", "jpg", BufferedImage.TYPE_INT_RGB));
        }
        assertThatThrownBy(() -> store.decode(files))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.TOO_MANY_IMAGES);
    }

    private static MultipartFile file(String name, String format, int type) throws IOException {
        BufferedImage image = new BufferedImage(40, 30, type);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return new MockMultipartFile("images", name, "image/" + format, out.toByteArray());
    }
}
