package com.reelay.pipeline.image.service;

import com.reelay.common.storage.LocalStorageService;
import com.reelay.common.storage.StorageService;
import com.reelay.pipeline.image.dto.CutImage;
import com.reelay.pipeline.image.enums.ImageRatio;
import com.reelay.pipeline.script.dto.CutDraft;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("HttpImageProcessor")
class HttpImageProcessorTest {

    private static final String URL_0 = "https://postfiles.pstatic.net/a/0.jpg?type=w966";
    private static final String URL_1 = "https://postfiles.pstatic.net/a/1.jpg?type=w966";
    private static final List<String> URLS = List.of(URL_0, URL_1);

    private final UUID jobId = UUID.randomUUID();

    private ImageDownloader downloader;
    private StorageService storage;
    private HttpImageProcessor processor;

    @BeforeEach
    void setUp(@TempDir Path root) {
        downloader = mock(ImageDownloader.class);
        storage = new LocalStorageService(root.toString());
        processor = new HttpImageProcessor(downloader, storage);
    }

    @Test
    @DisplayName("여러 컷이 같은 이미지를 써도 한 번만 받는다")
    void downloadsEachSourceOnce() throws Exception {
        byte[] bytes = jpeg(800, 600);
        when(downloader.download(URL_0)).thenReturn(Optional.of(bytes));

        processor.process(jobId, URLS, drafts(0, 0, 0));

        verify(downloader, times(1)).download(URL_0);
        verify(downloader, never()).download(URL_1);
    }

    @Test
    @DisplayName("파일은 컷이 아니라 원본 인덱스 이름으로 저장된다")
    void savesBySourceIndex() throws Exception {
        byte[] bytes = jpeg(800, 600);
        when(downloader.download(URL_1)).thenReturn(Optional.of(bytes));

        List<CutImage> images = processor.process(jobId, URLS, drafts(1, 1));

        Path expected = storage.imagePath(jobId, 1);
        assertThat(Files.exists(expected)).isTrue();
        assertThat(images).allSatisfy(i -> assertThat(i.imagePath()).isEqualTo(expected));
    }

    @Test
    @DisplayName("가로·세로·정사각 비율을 기록한다")
    void recordsRatio() throws Exception {
        byte[] landscape = jpeg(900, 410);
        byte[] portrait = jpeg(790, 1120);
        when(downloader.download(URL_0)).thenReturn(Optional.of(landscape));
        when(downloader.download(URL_1)).thenReturn(Optional.of(portrait));

        List<CutImage> images = processor.process(jobId, URLS, drafts(0, 1));

        assertThat(images.get(0).ratio()).isEqualTo(ImageRatio.LANDSCAPE);
        assertThat(images.get(1).ratio()).isEqualTo(ImageRatio.PORTRAIT);
    }

    @Test
    @DisplayName("같은 이미지가 연속되면 다른 KenBurns 변형을 준다")
    void variesKenBurnsOnConsecutiveReuse() throws Exception {
        byte[] bytes = jpeg(800, 600);
        when(downloader.download(URL_0)).thenReturn(Optional.of(bytes));

        List<CutImage> images = processor.process(jobId, URLS, drafts(0, 0));

        assertThat(images.get(0).kenburns()).isNotNull();
        assertThat(images.get(1).kenburns()).isNotEqualTo(images.get(0).kenburns());
    }

    @Test
    @DisplayName("같은 이미지를 다시 쓸 때마다 변형을 돌린다")
    void rotatesVariantOnEveryReuse() throws Exception {
        byte[] bytes = jpeg(800, 600);
        when(downloader.download(URL_0)).thenReturn(Optional.of(bytes));

        List<CutImage> images = processor.process(jobId, URLS, drafts(0, null, 0, 0));

        assertThat(images.get(0).kenburns()).isNotNull();
        assertThat(images.get(2).kenburns()).isNotEqualTo(images.get(0).kenburns());
        assertThat(images.get(3).kenburns()).isNotEqualTo(images.get(2).kenburns());
    }

    @Test
    @DisplayName("내려받기에 실패한 이미지를 쓰는 컷은 텍스트 카드가 된다")
    void fallsBackToTextCardOnDownloadFailure() {
        when(downloader.download(URL_0)).thenReturn(Optional.empty());

        List<CutImage> images = processor.process(jobId, URLS, drafts(0));

        assertThat(images.get(0).imagePath()).isNull();
        assertThat(images.get(0).kenburns()).isNull();
        assertThat(images.get(0).ratio()).isNull();
    }

    @Test
    @DisplayName("이미지로 열리지 않는 바이트는 텍스트 카드가 된다")
    void fallsBackToTextCardOnBrokenImage() {
        byte[] broken = "not-an-image".getBytes(StandardCharsets.UTF_8);
        when(downloader.download(URL_0)).thenReturn(Optional.of(broken));

        List<CutImage> images = processor.process(jobId, URLS, drafts(0));

        assertThat(images.get(0).imagePath()).isNull();
        assertThat(Files.exists(storage.imagePath(jobId, 0))).isFalse();
    }

    @Test
    @DisplayName("sourceImageIndex 가 null 이면 받지 않는다")
    void skipsNullIndex() {
        List<CutImage> images = processor.process(jobId, URLS, drafts(null, null));

        assertThat(images).allSatisfy(i -> assertThat(i.imagePath()).isNull());
        verifyNoInteractions(downloader);
    }

    @Test
    @DisplayName("범위를 벗어난 인덱스는 받지 않는다")
    void skipsOutOfRangeIndex() {
        List<CutImage> images = processor.process(jobId, URLS, drafts(9, -1));

        assertThat(images).allSatisfy(i -> assertThat(i.imagePath()).isNull());
        verifyNoInteractions(downloader);
    }

    @Test
    @DisplayName("이미지가 0장이면 전 컷이 텍스트 카드가 된다")
    void allTextCardsWhenNoImages() {
        List<CutImage> images = processor.process(jobId, List.of(), drafts(0, 1, 2));

        assertThat(images).hasSize(3);
        assertThat(images).allSatisfy(i -> assertThat(i.imagePath()).isNull());
        verifyNoInteractions(downloader);
    }

    @Test
    @DisplayName("컷 순서와 개수를 그대로 돌려준다")
    void keepsCutOrderAndSize() throws Exception {
        byte[] bytes = jpeg(800, 600);
        when(downloader.download(URL_0)).thenReturn(Optional.of(bytes));

        List<CutImage> images = processor.process(jobId, URLS, drafts(0, null, 0, null));

        assertThat(images).hasSize(4);
        assertThat(images).extracting(CutImage::cutOrder).containsExactly(0, 1, 2, 3);
    }

    private List<CutDraft> drafts(Integer... sourceImageIndexes) {
        List<CutDraft> cuts = new java.util.ArrayList<>();
        for(int i = 0; i < sourceImageIndexes.length; i++) {
            cuts.add(new CutDraft(i, "내레이션", "자막", null, sourceImageIndexes[i]));
        }
        return cuts;
    }

    private byte[] jpeg(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }
}