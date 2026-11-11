package com.reelay.pipeline.manual.service;

import com.reelay.common.exception.BusinessException;
import com.reelay.common.exception.ErrorCode;
import com.reelay.common.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// 본문 직접 입력 때 올린 사진 처리
// 1) decode: 요청 스레드에서 바로 열어 본다 -> 사진이 아니면 job 을 만들기 전에 400
// 2) save  : {jobId}/uploads/{n}.jpg 에 JPEG 로 저장 (PNG 투명 배경은 흰색으로 채움)
//    렌더러는 이미지 파일을 확장자(.jpg)로 해석하므로 형식을 JPEG 로 통일한다
@Component
public class ManualImageStore {

    private static final Logger log = LoggerFactory.getLogger(ManualImageStore.class);

    public static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    public static final long MAX_TOTAL_BYTES = 50L * 1024 * 1024;

    private final StorageService storage;

    public ManualImageStore(StorageService storage) {
        this.storage = storage;
    }

    // 빈 파일 칸(선택 안 한 input)은 건너뛴다
    public List<BufferedImage> decode(List<MultipartFile> files) {
        List<BufferedImage> images = new ArrayList<>();
        if (files == null) {
            return images;
        }
        long total = 0;
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            total += file.getSize();
            if (file.getSize() > MAX_IMAGE_BYTES || total > MAX_TOTAL_BYTES) {
                throw new BusinessException(ErrorCode.IMAGE_TOO_LARGE);
            }
            images.add(read(file));
        }
        ManualInputValidator.imageCount(images.size());
        return images;
    }

    // 저장한 파일 주소(file:...) 목록. IMAGE 단계가 이 주소로 읽는다
    public List<String> save(UUID jobId, List<BufferedImage> images) {
        List<String> uris = new ArrayList<>();
        for (int i = 0; i < images.size(); i++) {
            Path path = storage.uploadPath(jobId, i);
            write(path, toRgb(images.get(i)));
            uris.add(path.toUri().toString());
        }
        return uris;
    }

    private BufferedImage read(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            BufferedImage image = ImageIO.read(in);
            if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
                log.debug("사진으로 열리지 않음: {} ({})", file.getOriginalFilename(), file.getContentType());
                throw new BusinessException(ErrorCode.INVALID_IMAGE);
            }
            return image;
        }
        catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE, e);
        }
    }

    // JPEG 는 투명도를 못 담는다 -> 흰 배경에 그린다
    private static BufferedImage toRgb(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_RGB) {
            return src;
        }
        BufferedImage rgb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, src.getWidth(), src.getHeight());
            g.drawImage(src, 0, 0, null);
        }
        finally {
            g.dispose();
        }
        return rgb;
    }

    private static void write(Path path, BufferedImage image) {
        try {
            Files.createDirectories(path.getParent());
            if (!ImageIO.write(image, "jpg", path.toFile())) {
                throw new IOException("JPEG writer 없음");
            }
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
