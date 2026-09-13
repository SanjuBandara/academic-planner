package com.academicplanner.planning.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Wires the {@link RestClient} used by {@link PythonPlanningClient}.
 *
 * <p>
 * Uses {@link JdkClientHttpRequestFactory} directly (built on
 * java.net.http.HttpClient, available since Spring Framework 6.1) rather
 * than Spring Boot's newer {@code ClientHttpRequestFactoryBuilder}
 * (Boot 3.4+), so this compiles on any Boot 3.2/3.3/3.4+ project.
 */
@Configuration
public class PythonPlanningClientConfig {

        @Bean
        public RestClient pythonPlanningRestClient(
                        @Value("${planning.python-service.base-url:http://localhost:8000}") String baseUrl,
                        @Value("${planning.python-service.connect-timeout-ms:2000}") int connectTimeoutMs,
                        @Value("${planning.python-service.read-timeout-ms:15000}") int readTimeoutMs) {
                HttpClient httpClient = HttpClient.newBuilder()
                                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                                .build();

                JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
                requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

                return RestClient.builder()
                                .baseUrl(baseUrl)
                                .requestFactory(requestFactory)
                                .build();
        }
}