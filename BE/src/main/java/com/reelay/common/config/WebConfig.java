package com.reelay.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// FE 개발 서버(Vite 기본 5173)에서 API 를 부를 수 있게 허용한다
// 출처를 바꾸려면 REELAY_CORS_ALLOWED_ORIGINS 환경변수에 쉼표로 나열
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public WebConfig(@Value("${reelay.cors.allowed-origins:http://localhost:5173}") String[] allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*")
                // 다운로드 파일명, 영상 구간 탐색에 필요한 헤더를 FE 에서 읽을 수 있게 노출
                .exposedHeaders("Content-Disposition", "Accept-Ranges", "Content-Range", "Content-Length")
                .maxAge(3600);
    }
}
