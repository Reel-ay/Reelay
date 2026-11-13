package com.reelay.common.font;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ffmpeg의 fontsdir에 넘길 실제 폴더 경로
@Component
public class FontDir {
    private static final Logger log = LoggerFactory.getLogger(FontDir.class);

    private static final String CLASSPATH_DIR = "/fonts/";
    private static final String TMP_DIR_NAME = "reelay-fonts";

    public static final String TITLE_FILE = "Paperlogy-9Black.ttf";
    public static final String SUB_FILE = "Paperlogy-7Bold.ttf";

    public static final String TITLE_FAMILY = "Paperlogy 9 Black";
    public static final String SUB_FAMILY = "Paperlogy 7 Bold";

    private static final List<String> FONTS = List.of(TITLE_FILE, SUB_FILE);

    private final Path dir;
    private final Map<String, Font> fonts;

    public FontDir() {
        this.dir = extract();
        this.fonts = loadFonts();
    }

    public Path path() {
        return dir;
    }

    public Font font(String fileName) {
        Font font = fonts.get(fileName);
        if(font == null) {
            throw new IllegalArgumentException("등록되지 않은 폰트 - " + fileName);
        }
        return font;
    }

    private Map<String, Font> loadFonts() {
        Map<String, Font> loaded = new LinkedHashMap<>();
        for(String name : FONTS) {
            try(InputStream in = getClass().getResourceAsStream(CLASSPATH_DIR + name)) {
                if(in == null) {
                    throw new IllegalStateException("폰트 리소스 없음 - " + CLASSPATH_DIR + name);
                }
                loaded.put(name, Font.createFont(Font.TRUETYPE_FONT, in));
            }
            catch(Exception e) {
                throw new IllegalStateException("폰트를 읽지 못함 - " + name, e);
            }
        }
        return Map.copyOf(loaded);
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
