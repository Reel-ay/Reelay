package com.reelay.pipeline.render.service;

import com.reelay.common.ffmpeg.Ffmpeg;
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

    private static final String CARD_COLOR = "black";

    private static final String FIT_FILTER =
            "scale=" + WIDTH + ":" + HEIGHT + ":force_original_aspect_ratio=decrease"
                    + ",pad=" + WIDTH + ":" + HEIGHT + ":(ow-iw)/2:(oh-ih)/2:black"
                    + ",fps=" + FPS + ",setsar=1";

    private final Ffmpeg ffmpeg;
    private final StorageService storage;
    private final FontDir fontDir;

    public FfmpegRenderer(Ffmpeg ffmpeg, StorageService storage, FontDir fontDir) {
        this.ffmpeg = ffmpeg;
        this.storage = storage;
        this.fontDir = fontDir;
    }

    // 영상 조립
    @Override
    public Path render(UUID jobId, List<CutInput> cuts, Path subtitleFile) {
        validate(cuts);

        Path tmp = storage.tmpDir(jobId);

        List<Path> segments = new ArrayList<>(cuts.size());
        for(CutInput cut : cuts) {
            segments.add(renderCut(tmp, cut));
        }

        Path video = concat(tmp, segments, "video.txt", "joined.mp4");
        if(subtitleFile != null) {
            video = burnSubtitles(tmp, video, subtitleFile);
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
    private Path renderCut(Path tmp, CutInput cut) {
        Path out = tmp.resolve("cut" + cut.cutOrder() + ".mp4");

        List<String> args = new ArrayList<>();
        if(cut.imagePath() == null) {
            args.addAll(List.of("-f", "lavfi", "-i", "color=c=" + CARD_COLOR +
                    ":s=" + WIDTH + "x" + HEIGHT + ":r=" + FPS));
        }
        else {
            args.addAll(List.of("-loop", "1", "-i", cut.imagePath().toString()));
        }

        args.addAll(List.of(
                "-vf", FIT_FILTER,
                "-frames:v", String.valueOf(frameCount(cut.audioMs())),
                "-r", String.valueOf(FPS),
                "-c:v", "libx264", "-pix_fmt", "yuv420p",
                "-preset", "veryfast", "-crf", "18",
                out.toString()));

        ffmpeg.run(args, CUT_TIMEOUT);
        return out;
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
    private Path burnSubtitles(Path tmp, Path video, Path subtitleFile) {
        Path out = tmp.resolve("subbed.mp4");
        ffmpeg.run(List.of(
                "-i", video.toString(),
                "-vf", "ass=" + Ffmpeg.filterPath(subtitleFile) +
                        ":fontsdir=" + Ffmpeg.filterPath(fontDir.path()),
                "-c:v", "libx264", "-pix_fmt", "yuv420p",
                "-preset", "medium", "-crf", "20",
                "-r", String.valueOf(FPS),
                out.toString()), WHOLE_TIMEOUT);
        return out;
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