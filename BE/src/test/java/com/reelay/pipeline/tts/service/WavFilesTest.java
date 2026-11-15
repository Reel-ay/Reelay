package com.reelay.pipeline.tts.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WavFilesTest {

    // 24kHz 16bit mono (OpenAI TTS wav 와 같은 형식)
    private static byte[] wav(int dataBytes, int declaredDataSize) {
        int sampleRate = 24_000;
        int channels = 1;
        int bits = 16;
        int byteRate = sampleRate * channels * bits / 8;

        ByteBuffer b = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        b.putInt(declaredDataSize == -1 ? -1 : 36 + declaredDataSize);
        b.put("WAVE".getBytes(StandardCharsets.US_ASCII));
        b.put("fmt ".getBytes(StandardCharsets.US_ASCII));
        b.putInt(16);
        b.putShort((short) 1);
        b.putShort((short) channels);
        b.putInt(sampleRate);
        b.putInt(byteRate);
        b.putShort((short) (channels * bits / 8));
        b.putShort((short) bits);
        b.put("data".getBytes(StandardCharsets.US_ASCII));
        b.putInt(declaredDataSize);
        return b.array();
    }

    @Test
    @DisplayName("data 바이트 수로 길이를 계산한다 (24kHz 16bit mono 48,000바이트 = 1초)")
    void duration() {
        WavFiles.Info info = WavFiles.parse(wav(48_000, 48_000));

        assertThat(info.sampleRate()).isEqualTo(24_000);
        assertThat(info.durationMs()).isEqualTo(1000);
    }

    @Test
    @DisplayName("헤더 크기 칸이 0xFFFFFFFF(스트리밍 자리표시)여도 실제 길이를 잰다")
    void placeholderSize() {
        byte[] bytes = wav(72_000, -1);

        WavFiles.Info info = WavFiles.parse(bytes);
        assertThat(info.durationMs()).isEqualTo(1500);

        WavFiles.fixHeader(bytes, info);
        ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        assertThat(b.getInt(4)).isEqualTo(bytes.length - 8);
        assertThat(b.getInt(40)).isEqualTo(72_000);
    }

    @Test
    @DisplayName("wav 가 아니면 예외 (예: mp3 가 온 경우)")
    void notWav() {
        byte[] mp3 = new byte[100];
        mp3[0] = 'I';
        mp3[1] = 'D';
        mp3[2] = '3';

        assertThatThrownBy(() -> WavFiles.parse(mp3)).isInstanceOf(IllegalArgumentException.class);
    }
}
