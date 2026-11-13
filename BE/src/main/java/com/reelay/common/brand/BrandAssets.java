package com.reelay.common.brand;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

@Component
public class BrandAssets {
    private static final Logger log = LoggerFactory.getLogger(BrandAssets.class);

    private static final String CLASSPATH_DIR = "/brand/";
    private static final String TMP_DIR_NAME = "reelay-brand";
    private static final String LOGO_FILE = "logo.png";
    private static final String COVER_FILE = "card/cover.png";
    private static final int CARD_TEMPLATE_COUNT = 2;

    private final Path logo;
    private final Path cover;
    private final List<Path> cardTemplates;

    public BrandAssets() {
        this.logo = extract(LOGO_FILE);
        this.cover = extract(COVER_FILE);

        List<Path> templates = new ArrayList<>(CARD_TEMPLATE_COUNT);
        for(int i = 1; i <= CARD_TEMPLATE_COUNT; i++) {
            templates.add(extract("card/card" + i + ".png"));
        }
        this.cardTemplates = List.copyOf(templates);
    }

    public Path logo() {
        return logo;
    }

    // 첫 컷은 인물 표지, 나머지는 본문 템플릿을 컷 번호대로 돌려 쓴다
    public Path cardTemplate(int index) {
        if(index == 0) {
            return cover;
        }
        return cardTemplates.get(Math.floorMod(index - 1, cardTemplates.size()));
    }

    private Path extract(String name) {
        Path dir = Path.of(System.getProperty("java.io.tmpdir"), TMP_DIR_NAME);
        Path target = dir.resolve(name);
        try {
            Files.createDirectories(target.getParent());
            try(InputStream in = getClass().getResourceAsStream(CLASSPATH_DIR + name)) {
                if(in == null) {
                    throw new IllegalStateException("브랜드 리소스 없음 - " + CLASSPATH_DIR + name);
                }
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        catch(IOException e) {
            throw new UncheckedIOException(e);
        }

        log.info("브랜드 자산 : {}", target);
        return target;
    }
}
