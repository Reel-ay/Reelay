package com.reelay.common.font;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FontDirTest {

    @Test
    @DisplayName("폰트를 임시 폴더로 풀어내고 그 경로를 돌려준다")
    void extractsFontToRealPath() {
        FontDir fontDir = new FontDir();

        Path dir = fontDir.path();
        assertThat(dir).isDirectory();

        for(String name : List.of(FontDir.TITLE_FILE, FontDir.SUB_FILE)) {
            Path font = dir.resolve(name);
            assertThat(font).as(name).isRegularFile();
            assertThat(font.toFile().length()).as(name).isGreaterThan(100_000L);
        }
    }

    @Test
    @DisplayName("제목용과 자막용 폰트를 각각 돌려준다")
    void loadsBothFonts() {
        FontDir fontDir = new FontDir();

        assertThat(fontDir.font(FontDir.TITLE_FILE).getFontName()).contains("Black");
        assertThat(fontDir.font(FontDir.SUB_FILE).getFontName()).contains("Bold");
    }

    @Test
    @DisplayName("등록하지 않은 폰트를 달라고 하면 거절한다")
    void rejectsUnknownFont() {
        FontDir fontDir = new FontDir();

        assertThatThrownBy(() -> fontDir.font("NotRegistered.ttf"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("여러 번 만들어도 안전하다")
    void isIdempotent() {
        Path first = new FontDir().path();

        assertThatCode(FontDir::new).doesNotThrowAnyException();
        assertThat(new FontDir().path()).isEqualTo(first);
    }
}