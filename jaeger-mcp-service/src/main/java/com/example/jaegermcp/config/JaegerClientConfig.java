package com.example.jaegermcp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class JaegerClientConfig {

    @Value("${jaeger.base-url:http://jaeger:16686}")
    private String jaegerBaseUrl;

    @Bean
    public RestTemplate jaegerRestTemplate() {
        return new RestTemplate();
    }

    public String getJaegerBaseUrl() {
        return jaegerBaseUrl;
    }
}