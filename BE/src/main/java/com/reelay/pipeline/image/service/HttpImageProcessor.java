package com.reelay.pipeline.image.service;

import com.reelay.common.storage.StorageService;
import com.reelay.pipeline.image.dto.CutImage;
import com.reelay.pipeline.image.enums.ImageRatio;
import com.reelay.pipeline.render.enums.KenBurnsVariant;
import com.reelay.pipeline.script.dto.CutDraft;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static com.reelay.pipeline.render.enums.KenBurnsVariant.*;

@Slf4j
@Component
public class HttpImageProcessor implements ImageProcessor {

    private static final List<KenBurnsVariant> LANDSCAPE_ORDER =
            List.of(PAN_LEFT_TO_RIGHT, ZOOM_IN_CENTER, ZOOM_OUT_CENTER);
    private static final List<KenBurnsVariant> PORTRAIT_ORDER =
            List.of(PAN_TOP_TO_BOTTOM, ZOOM_IN_CENTER, ZOOM_OUT_CENTER);
    private static final List<KenBurnsVariant> SQUARE_ORDER =
            List.of(ZOOM_IN_CENTER, ZOOM_OUT_CENTER, PAN_LEFT_TO_RIGHT);

    private final ImageDownloader downloader;
    private final StorageService storage;

    public HttpImageProcessor(ImageDownloader downloader, StorageService storage) {
        this.downloader = downloader;
        this.storage = storage;
    }

    @Override
    public List<CutImage> process(UUID jobId, List<String> imageUrls, List<CutDraft> cuts) {
        return assign(cuts, fetchUsed(jobId, imageUrls, cuts));
    }

    // 필요한 이미지 선별
    private Map<Integer, Saved> fetchUsed(UUID jobId, List<String> urls, List<CutDraft> cuts) {
        Set<Integer> used = new TreeSet<>();
        for(CutDraft cut : cuts) {
            Integer index = cut.sourceImageIndex();
            if(index != null && index >= 0 && index < urls.size()) {
                used.add(index);
            }
        }

        Map<Integer, Saved> saved = new HashMap<>();
        for(int index : used) {
            save(jobId, index, urls.get(index)).ifPresent(s -> saved.put(index, s));
        }

        log.info("[{}] 이미지 {}/{}장 확보 (원본 {}장)", jobId, saved.size(), used.size(), urls.size());
        return saved;
    }

    // 이미지 다운로드 및 저장
    private Optional<Saved> save(UUID jobId, int index, String url) {
        Optional<byte[]> bytes = downloader.download(url);
        if(bytes.isEmpty()) {
            return Optional.empty();
        }

        BufferedImage decoded = decode(bytes.get(), url);
        if(decoded == null) {
            return Optional.empty();
        }

        Path path = storage.imagePath(jobId, index);
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, bytes.get());
        }
        catch(IOException e) {
            log.warn("[{}] 이미지 저장 실패 - {}", jobId, path, e);
            return Optional.empty();
        }

        return Optional.of(new Saved(path, ImageRatio.of(decoded.getWidth(), decoded.getHeight())));
    }

    // 이미지 데이터 검증
    private BufferedImage decode(byte[] bytes, String url) {
        try(InputStream in = new ByteArrayInputStream(bytes)) {
            BufferedImage image = ImageIO.read(in);
            if(image == null) {
                log.warn("이미지로 열리지 않음 : {}", url);
                return null;
            }
            if(image.getWidth() <= 0 || image.getHeight() <= 0) {
                log.warn("크기가 비정상 - {}x{} : {}", image.getWidth(), image.getHeight(), url);
                return null;
            }
            return image;
        }
        catch(IOException | RuntimeException e) {
            log.warn("이미지 디코딩 실패 : {}", url, e);
            return null;
        }
    }

    // 장면별 이미지 및 효과 배정
    private List<CutImage> assign(List<CutDraft> cuts, Map<Integer, Saved> saved) {
        List<CutImage> result = new ArrayList<>();
        Integer previousIndex = null;
        KenBurnsVariant previousVariant = null;

        for(CutDraft cut : cuts) {
            Integer index = cut.sourceImageIndex();
            Saved image = index == null ? null : saved.get(index);

            if(image == null) {
                result.add(new CutImage(cut.cutOrder(), null, null));
                previousIndex = null;
                previousVariant = null;
                continue;
            }

            KenBurnsVariant variant = pick(image.ratio(),
                    index.equals(previousIndex) ? previousVariant : null);
            result.add(new CutImage(cut.cutOrder(), image.path(), variant, image.ratio()));
            previousIndex = index;
            previousVariant = variant;
        }
        return result;
    }

    // Ken Burns 효과 선택
    private KenBurnsVariant pick(ImageRatio ratio, KenBurnsVariant avoid) {
        List<KenBurnsVariant> order = switch(ratio) {
            case LANDSCAPE -> LANDSCAPE_ORDER;
            case PORTRAIT -> PORTRAIT_ORDER;
            case SQUARE -> SQUARE_ORDER;
        };

        for(KenBurnsVariant variant : order) {
            if(variant != avoid) {
                return variant;
            }
        }
        return order.get(0);
    }

    // 이미지 저장 결과 관리
    private record Saved(Path path, ImageRatio ratio) {
    }
}