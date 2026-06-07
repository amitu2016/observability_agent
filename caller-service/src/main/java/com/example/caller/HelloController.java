package com.example.caller;

import io.opentelemetry.api.trace.Span;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@RestController
public class HelloController {

    private final RestTemplate restTemplate;

    public HelloController(RestTemplateBuilder builder) {
        this.restTemplate = builder.rootUri("http://downstream-service:8080").build();
    }

    @GetMapping("/hello")
    public ResponseEntity<String> hello() {
        String body = restTemplate.getForObject("/hello", String.class);
        String traceId = Span.current().getSpanContext().getTraceId();
        return ResponseEntity.ok()
                .header("X-Trace-Id", traceId)
                .body(body);
    }
}
