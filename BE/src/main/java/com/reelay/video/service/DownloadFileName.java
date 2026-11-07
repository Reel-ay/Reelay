package com.reelay.video.service;

import java.util.UUID;

// 다운로드 파일명 = 글 제목.mp4
// 제목에 이모지·경로 문자(\ / : * ? " < > |)가 들어오므로 걸러낸다
// 한글은 Content-Disposition 의 filename*=UTF-8'' (RFC 5987) 로 내보낸다 (컨트롤러에서 처리)
public final class DownloadFileName {

    private static final int MAX_LENGTH = 80;

    private DownloadFileName() {
    }

    public static String of(String title, UUID jobId) {
        String cleaned = sanitize(title);
        if (cleaned.isEmpty()) {
            return "reelay-" + jobId.toString().substring(0, 8) + ".mp4";
        }
        return cleaned + ".mp4";
    }

    static String sanitize(String title) {
        if (title == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        title.codePoints().forEach(cp -> {
            if (isAllowed(cp)) {
                sb.appendCodePoint(cp);
            } else if (Character.isWhitespace(cp)) {
                sb.append(' ');
            }
        });
        String result = sb.toString().replaceAll("\\s+", " ").strip();
        // 윈도우는 끝의 점·공백을 허용하지 않는다
        result = result.replaceAll("[. ]+$", "");
        if (result.codePointCount(0, result.length()) > MAX_LENGTH) {
            result = result.substring(0, result.offsetByCodePoints(0, MAX_LENGTH)).strip();
        }
        return result;
    }

    private static boolean isAllowed(int cp) {
        if (Character.isISOControl(cp) || Character.isWhitespace(cp)) {
            return false;
        }
        if ("\\/:*?\"<>|".indexOf(cp) >= 0) {
            return false;
        }
        // 이모지·기호 (보조 평면 문자, 기타 기호, 변형 선택자, ZWJ)
        if (Character.isSupplementaryCodePoint(cp)) {
            return false;
        }
        int type = Character.getType(cp);
        if (type == Character.OTHER_SYMBOL || type == Character.SURROGATE
                || type == Character.FORMAT || type == Character.PRIVATE_USE) {
            return false;
        }
        return !(cp >= 0xFE00 && cp <= 0xFE0F);
    }
}
