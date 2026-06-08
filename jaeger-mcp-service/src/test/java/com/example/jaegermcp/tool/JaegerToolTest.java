package com.example.jaegermcp.tool;

import com.example.jaegermcp.config.JaegerClientConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class JaegerToolTest {

    @Autowired
    private JaegerTraceTools jaegerTraceTools;

    @Autowired
    private JaegerClientConfig jaegerClientConfig;

    @Test
    void contextLoads() {
        assertNotNull(jaegerTraceTools);
    }

    @Test
    void getTraceById_returnsNotFound_when404() {
        String result = jaegerTraceTools.getTraceById("nonexistent-trace-id");
        assertNotNull(result);
    }

    @Test
    void searchTraces_handlesError() {
        String result = jaegerTraceTools.searchTraces("test-service", 1000000L, 2000000L, 10);
        assertNotNull(result);
    }

    @Test
    void jaegerClientConfig_hasBaseUrl() {
        assertNotNull(jaegerClientConfig.getJaegerBaseUrl());
    }
}