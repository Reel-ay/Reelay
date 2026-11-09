package com.reelay.pipeline.image.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@Component
public class ImageDownloader {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_ATTEMPTS = 3;
    private static final int MAX_REDIRECTS = 3;
    private static final int MAX_BYTES = 10 * 1024 * 1024;
    private static final long BACKOFF_MS = 300;
    private static final long MAX_BACKOFF_MS = 2_000;
    private static final int BUFFER = 8192;

    private final HttpClient client;
    private final String allowedDomain;

    // 허용 도메인, HTTP Client 초기화
    @Autowired
    public ImageDownloader(
            @Value("${reelay.image.allowed-domain:pstatic.net}") String allowedDomain) {
        this(allowedDomain, HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    }

    // 테스트용 생성자
    ImageDownloader(String allowedDomain, HttpClient client) {
        String domain = allowedDomain == null ? "" : allowedDomain.trim().toLowerCase(Locale.ROOT);
        this.allowedDomain = domain.startsWith(".") ? domain.substring(1) : domain;
        this.client = client;
    }

    // 이미지 URL 검증, 다운로드 및 재시도 제어
    public Optional<byte[]> download(String url) {
        URI uri = parse(url);
        if(uri == null) {
            return Optional.empty();
        }

        for(int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            Attempt result = tryOnce(uri, attempt);
            if(result.body() != null) {
                return Optional.of(result.body());
            }
            if(!result.retryable()) {
                return Optional.empty();
            }
            if(!sleepBefore(attempt)) {
                return Optional.empty();
            }
        }

        log.warn("이미지 {}회 모두 실패 : {}", MAX_ATTEMPTS, uri);
        return Optional.empty();
    }

    // HTTP 요청 -> 응답에 따른 다운로드, 리다이렉트, 재시도 처리
    private Attempt tryOnce(URI start, int attempt) {
        URI uri = start;
        try {
            for(int hop = 0; hop <= MAX_REDIRECTS; hop++) {
                HttpResponse<InputStream> res =
                        client.send(request(uri), HttpResponse.BodyHandlers.ofInputStream());

                try(InputStream body = res.body()) {
                    int status = res.statusCode();

                    if(status == 200) {
                        return verify(uri, res, body);
                    }
                    if(isRedirect(status)) {
                        URI next = nextHop(uri, res);
                        if(next == null) {
                            return Attempt.GIVE_UP;
                        }
                        uri = next;
                        continue;
                    }
                    if(permanent(status)) {
                        log.warn("이미지 {} - 재시도하지 않음 : {}", status, uri);
                        return Attempt.GIVE_UP;
                    }
                    log.warn("이미지 {} - {}/{} 시도 : {}", status, attempt, MAX_ATTEMPTS, uri);
                    return Attempt.RETRY;
                }
            }

            log.warn("리다이렉트 {}회 초과 : {}", MAX_REDIRECTS, start);
            return Attempt.GIVE_UP;
        }
        catch(IOException e) {
            log.warn("이미지 내려받기 실패 - {}/{} 시도 : {}", attempt, MAX_ATTEMPTS, uri, e);
            return Attempt.RETRY;
        }
        catch(InterruptedException e) {
            Thread.currentThread().interrupt();
            return Attempt.GIVE_UP;
        }
    }

    // 리다이렉트 주소 추출 -> 허용 도메인 여부 확인
    private URI nextHop(URI from, HttpResponse<InputStream> res) {
        String location = res.headers().firstValue("location").orElse(null);
        if(location == null) {
            log.warn("리다이렉트에 location 이 없음 : {}", from);
            return null;
        }

        URI resolved;
        try {
            resolved = from.resolve(location);
        }
        catch(IllegalArgumentException e) {
            log.warn("리다이렉트 주소 형식 오류 - {} : {}", location, from);
            return null;
        }

        URI next = parse(resolved.toString());
        if(next == null) {
            log.warn("리다이렉트를 따라가지 않음 : {} -> {}", from, location);
        }
        return next;
    }

    // 지정된 URI -> GET(타임아웃 O)
    private HttpRequest request(URI uri) {
        return HttpRequest.newBuilder(uri).GET().timeout(TIMEOUT).build();
    }

    // HTTP 상태 코드 -> 리다이렉트 응답 여부 확인
    private boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303
                || status == 307 || status == 308;
    }

    // 영구적인 HTTP 오류 여부 판단
    private boolean permanent(int status) {
        return status >= 400 && status < 500 && status != 408 && status != 429;
    }

    // 응답의 이미지 형식, 데이트 크기 검증
    private Attempt verify(URI uri, HttpResponse<InputStream> res, InputStream body)
            throws IOException {
        String type = res.headers().firstValue("content-type").orElse("");
        if(!type.startsWith("image/")) {
            log.warn("이미지가 아닌 응답 - {} : {}", type, uri);
            return Attempt.GIVE_UP;
        }

        long declared = res.headers().firstValueAsLong("content-length").orElse(-1);
        if(declared > MAX_BYTES) {
            log.warn("이미지가 너무 큼 - {}B : {}", declared, uri);
            return Attempt.GIVE_UP;
        }

        byte[] bytes = readBounded(body);
        if(bytes == null) {
            log.warn("이미지가 {}B 를 넘어 중단 : {}", MAX_BYTES, uri);
            return Attempt.GIVE_UP;
        }
        if(bytes.length == 0) {
            log.warn("이미지 본문이 빔 : {}", uri);
            return Attempt.GIVE_UP;
        }
        return Attempt.ok(bytes);
    }

    // 이미지 데이터 -> 스트리밍 -> 최대 용량 제한
    private byte[] readBounded(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[BUFFER];
        int n;
        while((n = in.read(buf)) != -1) {
            if(out.size() + n > MAX_BYTES) {
                return null;
            }
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    // 재시도 횟수 -> 지수 백오프 방식 대기
    private boolean sleepBefore(int attempt) {
        long wait = Math.min(BACKOFF_MS * (1L << (attempt - 1)), MAX_BACKOFF_MS);
        try {
            Thread.sleep(wait);
            return true;
        }
        catch(InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    // URL 형식, HTTPS, 허용 도메인 검증
    private URI parse(String url) {
        if(url == null || url.isBlank()) {
            return null;
        }

        try {
            URI uri = URI.create(url);
            if(!"https".equalsIgnoreCase(uri.getScheme()) || !allowedHost(uri.getHost())) {
                log.warn("허용하지 않는 이미지 주소 : {}", url);
                return null;
            }
            return uri;
        }
        catch(IllegalArgumentException e) {
            log.warn("이미지 주소 형식 오류 : {}", url);
            return null;
        }
    }

    // 호스트 검증
    private boolean allowedHost(String host) {
        if(host == null || allowedDomain.isEmpty()) {
            return false;
        }

        String h = host.toLowerCase(Locale.ROOT);
        return h.equals(allowedDomain) || h.endsWith("." + allowedDomain);
    }

    private record Attempt(byte[] body, boolean retryable) {

        private static final Attempt GIVE_UP = new Attempt(null, false);
        private static final Attempt RETRY = new Attempt(null, true);

        private static Attempt ok(byte[] body) {
            return new Attempt(body, false);
        }
    }
}