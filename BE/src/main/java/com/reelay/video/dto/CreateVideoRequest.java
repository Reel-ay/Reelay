package com.reelay.video.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateVideoRequest(
        @NotBlank
        @Size(max = 1024)
        String sourceUrl
) {
}
