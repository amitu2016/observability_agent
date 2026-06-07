package com.example.caller;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class DownstreamClient {

    private final RestClient restClient;

    public DownstreamClient(RestClient.Builder builder) {
        this.restClient = builder.baseUrl("http://downstream-service:8080").build();
    }

    public String callDownstream() {
        return restClient.get().uri("/hello").retrieve().body(String.class);
    }
}