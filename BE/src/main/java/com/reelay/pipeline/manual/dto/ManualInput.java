package com.reelay.pipeline.manual.dto;

import com.reelay.pipeline.crawl.dto.CrawledImage;
import com.reelay.pipeline.crawl.dto.CrawledPost;
import com.reelay.video.dto.VideoTitle;
import com.reelay.video.enums.TitleMode;

import java.util.ArrayList;
import java.util.List;

// 본문 직접 입력 값 (검증·사진 저장이 끝난 상태)
// title / titleHook : titleMode=MANUAL 일 때 사용자가 쓴 제목. AUTO 면 null
// imageUris         : 업로드 사진을 JPEG 로 저장한 파일 주소 (file:...). 올린 순서
public record ManualInput(
        TitleMode titleMode,
        String title,
        String titleHook,
        String body,
        List<String> imageUris
) {

    // 직접 작성한 제목. AUTO 면 null -> 파이프라인이 LLM 으로 만든다
    public VideoTitle fixedTitle() {
        if (titleMode != TitleMode.MANUAL) {
            return null;
        }
        return new VideoTitle(title, titleHook);
    }

    // 크롤링 결과와 같은 모양으로 바꿔 이후 단계(SCRIPT~RENDER)를 그대로 쓴다
    // - 문단: 줄바꿈 기준, 빈 줄은 버림
    // - 사진 위치(nearParagraph): 올린 순서대로 문단에 고르게 나눠 준다
    public CrawledPost toPost() {
        List<String> paragraphs = new ArrayList<>();
        for (String line : body.split("\\R")) {
            String text = line.strip();
            if (!text.isEmpty()) {
                paragraphs.add(text);
            }
        }

        List<CrawledImage> images = new ArrayList<>();
        int count = imageUris == null ? 0 : imageUris.size();
        for (int i = 0; i < count; i++) {
            int near = count == 0 ? 0 : (int) ((long) i * paragraphs.size() / count);
            images.add(new CrawledImage(i, imageUris.get(i), near));
        }

        String postTitle = titleMode == TitleMode.MANUAL ? title : null;
        return new CrawledPost(null, null, null, postTitle,
                String.join("\n", paragraphs), paragraphs.size(), List.copyOf(images));
    }
}
