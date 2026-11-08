package com.reelay.common.font;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class FontDirTest {

    @Test
    @DisplayName("폰트를 임시 폴더로 풀어내고 그 경로를 돌려준다")
    void extractsFontToRealPath() {
        FontDir fontDir = new FontDir();

        Path dir = fontDir.path();
        assertThat(dir).isDirectory();

        Path font = dir.resolve("BlackHanSans-Regular.ttf");
        assertThat(font).isRegularFile();
        assertThat(font.toFile().length()).isGreaterThan(100_000L);
    }

    @Test
    @DisplayName("여러 번 만들어도 안전하다")
    void isIdempotent() {
        Path first = new FontDir().path();

        assertThatCode(FontDir::new).doesNotThrowAnyException();
        assertThat(new FontDir().path()).isEqualTo(first);
    }
}