package com.academicplanner.ai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Getter
@Setter
@Configuration
public class AiConfig {

    @Value("${ai.enabled:true}")
    private boolean enabled;

    @Value("${ai.provider:openai}")
    private String provider;

    @Value("${ai.model:gpt-4o-mini}")
    private String model;

    @Value("${ai.api-key:${OPENAI_API_KEY:}}")
    private String apiKey;

    @Value("${ai.base-url:https://api.openai.com/v1}")
    private String baseUrl;

    @Value("${ai.timeout-ms:25000}")
    private int timeoutMs;

    @Bean(name = "aiRestClient")
    public RestClient aiRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }
}
