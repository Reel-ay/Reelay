package com.reelay.pipeline.render.service;

import com.reelay.common.ffmpeg.Ffmpeg;
import com.reelay.common.brand.BrandAssets;
import com.reelay.common.font.FontDir;
import com.reelay.common.storage.StorageService;
import com.reelay.video.dto.CutInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class FfmpegRenderer implements VideoRenderer {
    private static final Logger log = LoggerFactory.getLogger(FfmpegRenderer.class);

    private static final int WIDTH = 1080;
    private static final int HEIGHT = 1920;
    private static final int FPS = 30;

    private static final Duration CUT_TIMEOUT = Duration.ofMinutes(2);
    private static final Duration WHOLE_TIMEOUT = Duration.ofMinutes(10);

    private static final int MIN_AUDIO_MS = 200;

    private static final String CARD_FILTER =
            "fps=" + FPS + ",setsar=1,format=yuv420p";

    // 사진 띠: y 440~1380 (1080x940) 고정. 위는 제목, 띠 안 아래쪽에 자막이 겹친다
    private static final int BAND_Y = 440;
    private static final int BAND_HEIGHT = 940;
    private static final int BLUR_RADIUS = 20;

    private static final int SAFE_BOTTOM = 1540;
    private static final int LOGO_WIDTH = 350;

    // 사진 비율과 상관없이 항상 같은 띠 안에 넣는다
    // - 배경: 사진을 띠에 꽉 차게 키워 자른 뒤 흐리게 (cover + blur)
    // - 전경: 사진 전체가 보이도록 띠 안에 맞춤 (contain). 가로 배너도 잘리지 않는다
    // - 띠 밖은 검정
    private static final String FIT_FILTER =
            "split[bg][fg]"
                    + ";[bg]scale=" + WIDTH + ":" + BAND_HEIGHT + ":force_original_aspect_ratio=increase:out_range=tv"
                    + ",crop=" + WIDTH + ":" + BAND_HEIGHT + ",boxblur=" + BLUR_RADIUS + ":2[blur]"
                    + ";[fg]scale=" + WIDTH + ":" + BAND_HEIGHT + ":force_original_aspect_ratio=decrease:out_range=tv[img]"
                    + ";[blur][img]overlay=(W-w)/2:(H-h)/2"
                    + ",pad=" + WIDTH + ":" + HEIGHT + ":0:" + BAND_Y + ":black"
                    + ",fps=" + FPS + ",setsar=1,format=yuv420p";

    private final Ffmpeg ffmpeg;
    private final StorageService storage;
    private final FontDir fontDir;
    private final BrandAssets brandAssets;

    public FfmpegRenderer(Ffmpeg ffmpeg, StorageService storage, FontDir fontDir,
                          BrandAssets brandAssets) {
        this.ffmpeg = ffmpeg;
        this.storage = storage;
        this.fontDir = fontDir;
        this.brandAssets = brandAssets;
    }

    // 영상 조립
    @Override
    public Path render(UUID jobId, List<CutInput> cuts, Path subtitleFile) {
        validate(cuts);

        Path tmp = storage.tmpDir(jobId);

        List<Path> segments = new ArrayList<>(cuts.size());
        boolean cardMode = cuts.stream().allMatch(cut -> cut.imagePath() == null);
        for(int i = 0; i < cuts.size(); i++) {
            segments.add(renderCut(tmp, cuts.get(i), i, cuts.size(), cardMode));
        }

        Path video = concat(tmp, segments, "video.txt", "joined.mp4");
        if(subtitleFile != null) {
            video = burnSubtitles(tmp, video, subtitleFile, cardMode ? orangeCutRanges(cuts) : null);
        }

        List<Path> wavs = cuts.stream().map(CutInput::audioPath).toList();
        Path audio = concat(tmp, wavs, "audio.txt", "joined.wav");

        return mux(video, audio, storage.outputPath(jobId));
    }

    // 입력 검사 : 오디오 파일 여부 판단
    private void validate(List<CutInput> cuts) {
        if(cuts.isEmpty()) {
            throw new IllegalArgumentException("컷이 비어 있음");
        }

        for(CutInput cut : cuts) {
            if(cut.audioPath() == null) {
                throw new IllegalArgumentException("audioPath 없음 - 컷 " + cut.cutOrder());
            }

            if(cut.audioMs() < MIN_AUDIO_MS) {
                throw new IllegalArgumentException("audioMs가 비정상 - 컷 " + cut.cutOrder() + " : " + cut.audioMs() + "ms");
            }
        }
    }

    // 컷 1개 -> 1080*1920 영상
    private Path renderCut(Path tmp, CutInput cut, int index, int total, boolean cardMode) {
        Path out = tmp.resolve("cut" + cut.cutOrder() + ".mp4");

        List<String> args = new ArrayList<>();
        if(cardMode) {
            args.addAll(List.of("-f", "lavfi", "-i",
                    "color=c=" + CardStyle.of(index, total).base + ":s=" + WIDTH + "x" + HEIGHT + ":r=" + FPS));
        }
        else if(cut.imagePath() == null) {
            args.addAll(List.of("-f", "lavfi", "-i",
                    "color=c=black:s=" + WIDTH + "x" + HEIGHT + ":r=" + FPS));
        }
        else {
            args.addAll(List.of("-loop", "1", "-i", cut.imagePath().toString()));
        }

        args.addAll(List.of(
                "-vf", cardMode ? CARD_FILTER : FIT_FILTER,
                "-frames:v", String.valueOf(frameCount(cut.audioMs())),
                "-r", String.valueOf(FPS),
                "-c:v", "libx264", "-pix_fmt", "yuv420p",
                "-preset", "veryfast", "-crf", "18",
                out.toString()));

        ffmpeg.run(args, CUT_TIMEOUT);
        return out;
    }

    // 배경은 단색만 깔고 원·막대·글자는 전부 ASS 에서 그린다 (AssSubtitleWriter.cardLine)
    private enum CardStyle {
        TITLE("0xF26B1A"),
        LIGHT("0xF7F3EC"),
        DARK("0x151515");

        private final String base;

        CardStyle(String base) {
            this.base = base;
        }

        // 첫 컷은 제목, 마지막 컷은 마무리. 사이 본문은 밝고 어두운 배경을 번갈아 쓴다
        static CardStyle of(int index, int total) {
            if(index == 0) {
                return TITLE;
            }
            if(index == total - 1) {
                return DARK;
            }
            return index % 2 == 1 ? LIGHT : DARK;
        }
    }

    // 프레임 수
    private int frameCount(int audioMs) {
        return Math.max(1, (int) Math.round(audioMs / 1000.0 * FPS));
    }

    // concat demuxer로 이어 붙임
    private Path concat(Path tmp, List<Path> files, String listName, String outName) {
        Path list = tmp.resolve(listName);
        Path out = tmp.resolve(outName);

        StringBuilder sb = new StringBuilder();
        for(Path file : files) {
            // 따옴표 안이라 윈도우 역슬래시가 그대로 통함(실측)
            sb.append("file '").append(file.toAbsolutePath()).append("'").append('\n');
        }

        try {
            Files.writeString(list, sb.toString(), StandardCharsets.UTF_8);
        }
        catch(IOException e) {
            // 체크 예외라 그대로 못 올림 -> 감싸기만. 매핑은 VideoPipeline 이 함
            throw new UncheckedIOException(e);
        }

        ffmpeg.run(List.of("-f", "concat", "-safe", "0",
                "-i", list.toString(), "-c", "copy", out.toString()), WHOLE_TIMEOUT);
        return out;
    }

    // 자막 파일 -> 영상
    private Path burnSubtitles(Path tmp, Path video, Path subtitleFile, String orangeRanges) {
        Path out = tmp.resolve("subbed.mp4");

        String place = "overlay=(W-w)/2:'" + BAND_Y + "+" + BAND_HEIGHT
                + "+(" + (SAFE_BOTTOM - BAND_Y - BAND_HEIGHT) + "-h)/2':shortest=1";

        String filter;
        List<String> inputs;
        if(orangeRanges == null) {
            filter = "[1:v]scale=" + LOGO_WIDTH + ":-1[lg];[0:v][lg]" + place;
            inputs = List.of("-i", video.toString(),
                    "-loop", "1", "-i", brandAssets.logo().toString());
        }
        else {
            // 오렌지 카드에서만 로고가 배경에 묻혀서 그 구간만 흰 로고를 깐다
            filter = "[1:v]scale=" + LOGO_WIDTH + ":-1[dk];[2:v]scale=" + LOGO_WIDTH + ":-1[wt]"
                    + ";[0:v][dk]" + place + ":enable='not(" + orangeRanges + ")'[a]"
                    + ";[a][wt]" + place + ":enable='" + orangeRanges + "'";
            inputs = List.of("-i", video.toString(),
                    "-loop", "1", "-i", brandAssets.logo().toString(),
                    "-loop", "1", "-i", brandAssets.logoWhite().toString());
        }

        filter += ",ass=" + Ffmpeg.filterPath(subtitleFile)
                + ":fontsdir=" + Ffmpeg.filterPath(fontDir.path()) + "[v]";

        List<String> args = new ArrayList<>(inputs);
        args.addAll(List.of(
                "-filter_complex", filter,
                "-map", "[v]",
                "-c:v", "libx264", "-pix_fmt", "yuv420p",
                "-preset", "medium", "-crf", "20",
                "-r", String.valueOf(FPS),
                out.toString()));

        ffmpeg.run(args, WHOLE_TIMEOUT);
        return out;
    }

    // 오렌지 배경 카드가 나오는 시간 구간. CardStyle.of 와 같은 규칙이어야 한다
    private String orangeCutRanges(List<CutInput> cuts) {
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < cuts.size(); i++) {
            if(CardStyle.of(i, cuts.size()) != CardStyle.TITLE) {
                continue;
            }
            CutInput cut = cuts.get(i);
            if(!sb.isEmpty()) {
                sb.append("+");
            }
            sb.append("between(t,%.3f,%.3f)"
                    .formatted(cut.startMs() / 1000.0, (cut.startMs() + cut.audioMs()) / 1000.0));
        }
        return sb.isEmpty() ? "0" : sb.toString();
    }

    // 영상 파일 + 소리 파일
    private Path mux(Path video, Path audio, Path output) {
        ffmpeg.run(List.of(
                "-i", video.toString(),
                "-i", audio.toString(),
                "-c:v", "copy", "-c:a", "aac", "-b:a", "128k",
                "-shortest", output.toString()), WHOLE_TIMEOUT);
        return output;
    }
}