package com.reelay.common.font;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

// ffmpeg의 fontsdir에 넘길 실제 폴더 경로
@Component
public class FontDir {
    private static final Logger log = LoggerFactory.getLogger(FontDir.class);

    private static final String CLASSPATH_DIR = "/fonts/";
    private static final String TMP_DIR_NAME = "reelay-fonts";

    private static final List<String> FONTS = List.of("BlackHanSans-Regular.ttf");

    private final Path dir;

    public FontDir() {
        this.dir = extract();
    }

    public Path path() {
        return dir;
    }

    public Path fontFile() {
        return dir.resolve(FONTS.get(0));
    }

    private Path extract() {
        Path target = Path.of(System.getProperty("java.io.tmpdir"), TMP_DIR_NAME);
        try {
            Files.createDirectories(target);
            for(String name : FONTS) {
                try(InputStream in = getClass().getResourceAsStream(CLASSPATH_DIR + name)) {
                    if(in == null) {
                        throw new IllegalStateException("폰트 리소스 없음 - " + CLASSPATH_DIR + name);
                    }
                    Files.copy(in, target.resolve(name), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        catch(IOException e) {
            throw new UncheckedIOException(e);
        }

        log.info("폰트 디렉터리 : {}", target);
        return target;
    }
}
