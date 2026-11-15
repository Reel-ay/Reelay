package com.reelay.pipeline.tts.dto;

import java.nio.file.Path;

// TTS 산출물. audioMs 는 실제 wav 파일에서 잰 길이 = 컷 길이 = 자막 구간
public record CutAudio(
        int cutOrder,
        Path audioPath,
        int audioMs
) {
}
