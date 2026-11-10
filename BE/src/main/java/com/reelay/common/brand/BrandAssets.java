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

@Component
public class BrandAssets {
    private static final Logger log = LoggerFactory.getLogger(BrandAssets.class);

    private static final String CLASSPATH_DIR = "/brand/";
    private static final String TMP_DIR_NAME = "reelay-brand";
    private static final String LOGO_FILE = "logo.png";

    private final Path logo;

    public BrandAssets() {
        this.logo = extract(LOGO_FILE);
    }

    public Path logo() {
        return logo;
    }

    private Path extract(String name) {
        Path dir = Path.of(System.getProperty("java.io.tmpdir"), TMP_DIR_NAME);
        Path target = dir.resolve(name);
        try {
            Files.createDirectories(dir);
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
