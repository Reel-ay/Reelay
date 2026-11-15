package com.reelay.pipeline.image.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("ImageDownloader")
class ImageDownloaderTest {

    private static final String URL = "https://postfiles.pstatic.net/MjAy/Image.jpg?type=w966";
    private static final byte[] JPEG = "fake-jpeg".getBytes(StandardCharsets.UTF_8);

    private HttpClient client;
    private ImageDownloader downloader;

    @BeforeEach
    void setUp() {
        client = mock(HttpClient.class);
        downloader = new ImageDownloader(".pstatic.net", client);
    }

    @Test
    @DisplayName("200이면 바이트를 돌려준다")
    void returnsBytesOnOk() throws Exception {
        HttpResponse<InputStream> res = ok(JPEG);
        when(client.<InputStream>send(any(), any())).thenReturn(res);

        assertThat(downloader.download(URL)).contains(JPEG);
    }

    @Test
    @DisplayName("Referer 헤더를 붙이지 않는다")
    void sendsNoReferer() throws Exception {
        HttpResponse<InputStream> res = ok(JPEG);
        when(client.<InputStream>send(any(), any())).thenReturn(res);

        downloader.download(URL);

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(captor.capture(), any());
        assertThat(captor.getValue().headers().firstValue("Referer")).isEmpty();
    }

    @Test
    @DisplayName("일시 실패 뒤 성공하면 바이트를 돌려준다")
    void retriesUntilSuccess() throws Exception {
        HttpResponse<InputStream> res = ok(JPEG);
        when(client.<InputStream>send(any(), any()))
                .thenThrow(new IOException("연결 끊김"))
                .thenReturn(res);

        assertThat(downloader.download(URL)).contains(JPEG);
        verify(client, times(2)).send(any(), any());
    }

    @Test
    @DisplayName("3회 모두 실패하면 빈 값을 돌려준다")
    void givesUpAfterThreeAttempts() throws Exception {
        when(client.<InputStream>send(any(), any())).thenThrow(new IOException("연결 끊김"));

        assertThat(downloader.download(URL)).isEmpty();
        verify(client, times(3)).send(any(), any());
    }

    @Test
    @DisplayName("404는 재시도하지 않는다")
    void doesNotRetryOnNotFound() throws Exception {
        HttpResponse<InputStream> res = status(404);
        when(client.<InputStream>send(any(), any())).thenReturn(res);

        assertThat(downloader.download(URL)).isEmpty();
        verify(client, times(1)).send(any(), any());
    }

    @Test
    @DisplayName("503은 재시도한다")
    void retriesOnServerError() throws Exception {
        HttpResponse<InputStream> res = status(503);
        when(client.<InputStream>send(any(), any())).thenReturn(res);

        assertThat(downloader.download(URL)).isEmpty();
        verify(client, times(3)).send(any(), any());
    }

    @Test
    @DisplayName("본문을 쓰지 않는 응답에서도 스트림을 닫는다")
    void closesBodyOnNonOkResponse() throws Exception {
        InputStream stream = spy(new ByteArrayInputStream(new byte[0]));
        HttpResponse<InputStream> res = responseWith(404, Map.of(), stream);
        when(client.<InputStream>send(any(), any())).thenReturn(res);

        downloader.download(URL);

        verify(stream).close();
    }

    @Test
    @DisplayName("CDN 안으로 리다이렉트하면 따라간다")
    void followsRedirectInsideCdn() throws Exception {
        HttpResponse<InputStream> moved = redirect("https://blogfiles.pstatic.net/b/Image.jpg");
        HttpResponse<InputStream> res = ok(JPEG);
        when(client.<InputStream>send(any(), any())).thenReturn(moved).thenReturn(res);

        assertThat(downloader.download(URL)).contains(JPEG);
        verify(client, times(2)).send(any(), any());
    }

    @Test
    @DisplayName("CDN 밖으로 리다이렉트하면 따라가지 않는다")
    void doesNotFollowRedirectOutsideCdn() throws Exception {
        HttpResponse<InputStream> moved = redirect("https://evil.example.com/a.jpg");
        when(client.<InputStream>send(any(), any())).thenReturn(moved);

        assertThat(downloader.download(URL)).isEmpty();
        verify(client, times(1)).send(any(), any());
    }

    @Test
    @DisplayName("리다이렉트 주소가 깨졌으면 예외 없이 포기한다")
    void givesUpOnMalformedRedirect() throws Exception {
        HttpResponse<InputStream> moved = redirect("ht tp://[broken");
        when(client.<InputStream>send(any(), any())).thenReturn(moved);

        assertThat(downloader.download(URL)).isEmpty();
        verify(client, times(1)).send(any(), any());
    }

    @Test
    @DisplayName("content-length가 한도를 넘으면 받지 않는다")
    void rejectsOversizedByHeader() throws Exception {
        HttpResponse<InputStream> res = response(200,
                Map.of("content-type", List.of("image/jpeg"),
                        "content-length", List.of("20971520")),
                JPEG);
        when(client.<InputStream>send(any(), any())).thenReturn(res);

        assertThat(downloader.download(URL)).isEmpty();
    }

    @Test
    @DisplayName("이미지가 아닌 응답은 버린다")
    void rejectsNonImage() throws Exception {
        HttpResponse<InputStream> res =
                response(200, Map.of("content-type", List.of("text/html")), JPEG);
        when(client.<InputStream>send(any(), any())).thenReturn(res);

        assertThat(downloader.download(URL)).isEmpty();
    }

    @Test
    @DisplayName("CDN 밖 주소는 요청하지 않는다")
    void rejectsForeignHost() throws Exception {
        assertThat(downloader.download("https://evil.example.com/a.jpg")).isEmpty();
        verify(client, never()).send(any(), any());
    }

    @Test
    @DisplayName("도메인 경계를 넘는 유사 호스트는 거른다")
    void rejectsLookalikeHost() throws Exception {
        assertThat(downloader.download("https://evilpstatic.net/a.jpg")).isEmpty();
        assertThat(downloader.download("https://pstatic.net.evil.com/a.jpg")).isEmpty();
        verify(client, never()).send(any(), any());
    }

    @Test
    @DisplayName("도메인 자신도 허용한다")
    void allowsBareDomain() throws Exception {
        HttpResponse<InputStream> res = ok(JPEG);
        when(client.<InputStream>send(any(), any())).thenReturn(res);

        assertThat(downloader.download("https://pstatic.net/a.jpg")).contains(JPEG);
    }

    @Test
    @DisplayName("하위 도메인도 허용한다")
    void allowsSubdomain() throws Exception {
        HttpResponse<InputStream> res = ok(JPEG);
        when(client.<InputStream>send(any(), any())).thenReturn(res);

        assertThat(downloader.download("https://blogfiles.pstatic.net/a.jpg")).contains(JPEG);
    }

    @Test
    @DisplayName("http 주소는 요청하지 않는다")
    void rejectsPlainHttp() throws Exception {
        assertThat(downloader.download("http://postfiles.pstatic.net/a.jpg")).isEmpty();
        verify(client, never()).send(any(), any());
    }

    private HttpResponse<InputStream> ok(byte[] body) {
        return response(200, Map.of("content-type", List.of("image/jpeg")), body);
    }

    private HttpResponse<InputStream> status(int code) {
        return response(code, Map.of(), new byte[0]);
    }

    private HttpResponse<InputStream> redirect(String location) {
        return response(302, Map.of("location", List.of(location)), new byte[0]);
    }

    private HttpResponse<InputStream> response(int status,
                                               Map<String, List<String>> headers,
                                               byte[] body) {
        return responseWith(status, headers, new ByteArrayInputStream(body));
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<InputStream> responseWith(int status,
                                                   Map<String, List<String>> headers,
                                                   InputStream body) {
        HttpResponse<InputStream> res = mock(HttpResponse.class);
        when(res.statusCode()).thenReturn(status);
        when(res.body()).thenReturn(body);
        when(res.headers()).thenReturn(HttpHeaders.of(headers, (a, b) -> true));
        return res;
    }
}