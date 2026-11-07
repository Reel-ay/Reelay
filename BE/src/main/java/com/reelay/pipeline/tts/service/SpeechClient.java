package com.reelay.pipeline.tts.service;

import com.reelay.common.exception.BusinessException;
import com.reelay.common.exception.ErrorCode;
import com.reelay.common.exception.OpenAiErrors;
import com.reelay.common.exception.OpenAiTransientException;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.audio.tts.TextToSpeechResponse;
import org.springframework.ai.openai.OpenAiAudioSpeechOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;

// 문장 1개 -> wav 바이트. 재시도 단위가 "문장 1개"가 되도록 TtsService 와 분리했다
// 모델·목소리는 application.properties 의 reelay.tts.* 에서 정한다
@Component
public class SpeechClient {

    // gpt-4o-mini-tts 는 읽는 톤을 지시할 수 있다
    static final String DEFAULT_INSTRUCTIONS =
            "한국어 숏폼 나레이션입니다. 또렷하고 자연스럽게, 조금 빠른 속도로 읽어 주세요.";

    private final TextToSpeechModel model;
    private final OpenAiAudioSpeechOptions options;

    // 주의: 요청 옵션에 비워 둔 값은 Spring AI 기본값(alloy, 1.0 등)으로 채워져 설정값을 덮어쓴다.
    //       그래서 모델·목소리·속도를 전부 여기서 명시한다
    public SpeechClient(TextToSpeechModel model,
                        @Value("${reelay.tts.model:gpt-4o-mini-tts}") String ttsModel,
                        @Value("${reelay.tts.voice:nova}") String voice,
                        @Value("${reelay.tts.speed:1.0}") double speed,
                        @Value("${reelay.tts.instructions:" + DEFAULT_INSTRUCTIONS + "}") String instructions) {
        this.model = model;
        this.options = OpenAiAudioSpeechOptions.builder()
                .model(ttsModel)
                .voice(voice)
                .speed(speed)
                .instructions(instructions.isBlank() ? null : instructions)
                // B 와의 약속: TTS 는 반드시 wav
                // mp3 는 이음새마다 약 48ms(24kHz) 씩 어긋나 18컷이면 약 816ms 누적 -> 자막 타이밍 붕괴
                // (파일별 길이는 정확히 나와서 audioMs 로는 안 잡힌다. 이어 붙인 뒤에만 틀어진다)
                .responseFormat(OpenAiAudioSpeechOptions.AudioResponseFormat.WAV)
                .build();
    }

    // 문장별 3회 재시도 (1s / 2s / 4s)
    @Retryable(includes = OpenAiTransientException.class, maxRetries = 3, delay = 1000, multiplier = 2)
    public byte[] synthesize(String text) {
        TextToSpeechResponse response;
        try {
            response = model.call(new TextToSpeechPrompt(text, options));
        }
        catch (RuntimeException e) {
            throw OpenAiErrors.translate(e, ErrorCode.TTS_FAILED);
        }
        if (response == null || response.getResult() == null) {
            throw new BusinessException(ErrorCode.TTS_FAILED);
        }
        byte[] audio = response.getResult().getOutput();
        if (audio == null || audio.length == 0) {
            throw new BusinessException(ErrorCode.TTS_FAILED);
        }
        return audio;
    }
}
