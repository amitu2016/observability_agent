package com.example.downstream;

import io.opentelemetry.api.trace.Span;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @GetMapping("/hello")
    public ResponseEntity<String> hello() {
        String traceId = Span.current().getSpanContext().getTraceId();
        return ResponseEntity.ok()
                .header("X-Trace-Id", traceId)
                .body("Hello from downstream!");
    }
}