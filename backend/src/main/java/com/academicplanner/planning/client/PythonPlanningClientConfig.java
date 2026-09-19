package com.academicplanner.planning.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class PythonPlanningClientConfig {

    @Bean
    public RestClient pythonPlanningRestClient(
            @Value("${planning.python-service.base-url:http://localhost:8000}") String baseUrl,
            @Value("${planning.python-service.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${planning.python-service.read-timeout-ms:15000}") int readTimeoutMs) {

        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();

        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}