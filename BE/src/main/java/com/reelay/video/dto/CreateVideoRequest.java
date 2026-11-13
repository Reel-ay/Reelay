package com.reelay.video.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateVideoRequest(
        @Schema(description = "네이버 블로그 글 주소",
                example = "https://blog.naver.com/copper567/224453934072")
        @NotBlank
        @Size(max = 1024)
        String sourceUrl
) {
}
