package com.reelay.pipeline.tts.service;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

// PCM wav 헤더 읽기·보정
// OpenAI TTS 의 wav 는 스트리밍으로 만들어져서 RIFF/data 크기 칸이 0xFFFFFFFF 같은 자리표시 값으로 오는 경우가 있다.
// 그대로 두면 헤더를 믿는 도구(ffprobe 포함)가 길이를 엉뚱하게 읽는다 -> 실제 파일 크기로 고쳐 쓴다
final class WavFiles {

    private WavFiles() {
    }

    record Info(int sampleRate, int channels, int bitsPerSample, int byteRate, int dataOffset, long dataBytes) {

        // 샘플 수로 계산한 정확한 길이 (ms)
        int durationMs() {
            if (byteRate <= 0) {
                return 0;
            }
            return (int) Math.round(dataBytes * 1000.0 / byteRate);
        }
    }

    // 헤더를 해석한다. data 길이는 헤더 값이 아니라 실제로 남은 바이트로 계산한다
    static Info parse(byte[] wav) {
        if (wav.length < 44 || !"RIFF".equals(ascii(wav, 0)) || !"WAVE".equals(ascii(wav, 8))) {
            throw new IllegalArgumentException("wav 형식이 아닙니다");
        }
        ByteBuffer buf = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);

        int sampleRate = 0;
        int channels = 0;
        int bits = 0;
        int byteRate = 0;
        int pos = 12;
        while (pos + 8 <= wav.length) {
            String id = ascii(wav, pos);
            long size = Integer.toUnsignedLong(buf.getInt(pos + 4));
            int body = pos + 8;

            if ("fmt ".equals(id)) {
                channels = buf.getShort(body + 2);
                sampleRate = buf.getInt(body + 4);
                byteRate = buf.getInt(body + 8);
                bits = buf.getShort(body + 14);
            }
            else if ("data".equals(id)) {
                long remaining = wav.length - body;
                return new Info(sampleRate, channels, bits, byteRate, body, remaining);
            }
            // 청크는 짝수 바이트 정렬
            long next = body + size + (size & 1);
            if (next > wav.length || next <= pos) {
                break;
            }
            pos = (int) next;
        }
        throw new IllegalArgumentException("wav data 청크가 없습니다");
    }

    // RIFF 크기와 data 크기를 실제 값으로 덮어쓴다 (원본 배열을 고친다)
    static void fixHeader(byte[] wav, Info info) {
        ByteBuffer buf = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(4, wav.length - 8);
        buf.putInt(info.dataOffset() - 4, (int) info.dataBytes());
    }

    private static String ascii(byte[] b, int offset) {
        return new String(b, offset, 4, StandardCharsets.US_ASCII);
    }
}
