package com.training.portfolio.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AppProperties appProperties;

    @Bean
    public RestClient restClient() {
        return RestClient.create();
    }

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        List<String> origins =
                new ArrayList<>(
                        Arrays.stream(appProperties.getCors().getAllowedOrigins().split(","))
                                .map(String::trim)
                                .filter(s -> !s.isEmpty())
                                .toList());
        if (origins.isEmpty()) {
            origins.add("http://localhost:5173");
            origins.add("http://127.0.0.1:5173");
        }
        // 使用 setAllowedOrigins 而不是 setAllowedOriginPatterns 以支持凭证
        config.setAllowedOrigins(origins);
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        config.setMaxAge(3600L);  // 预检请求缓存 1 小时
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
