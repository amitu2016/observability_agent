package com.example.triageagent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.ai.chat.client.ChatClient;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Context load test for triage-agent.
 * Verifies that the Spring application context starts successfully
 * with test configuration (dummy API key).
 */
@SpringBootTest(properties = {
    "spring.ai.mcp.client.enabled=false",
    "spring.ai.openai.api-key=dummy-test-key"
})
class TriageAgentApplicationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void contextLoads() {
        assertNotNull(applicationContext, "ApplicationContext should not be null");
    }

    @Test
    void chatClientBuilderBeanExists() {
        Object chatClientBuilder = applicationContext.getBean("chatClientBuilder");
        assertNotNull(chatClientBuilder, "ChatClient.Builder bean should be present");
    }

    @Test
    void triageControllerBeanExists() {
        Object triageController = applicationContext.getBean("triageController");
        assertNotNull(triageController, "TriageController bean should be present");
    }
}
