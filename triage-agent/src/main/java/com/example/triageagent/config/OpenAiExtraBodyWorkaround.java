package com.example.triageagent.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Workaround for Spring AI 1.1.4 issue where OpenAI rejects requests containing
 * an empty {@code extra_body} field. This strips that field from outgoing JSON bodies.
 *
 * <p>Safe to remove once this project upgrades to Spring AI 1.1.6+ where the
 * upstream fix is available. Tracked by spring-projects/spring-ai#5196.</p>
 */
@Configuration
@ConditionalOnProperty(prefix = "spring.ai.openai", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OpenAiExtraBodyWorkaround {

    @Bean
    public RestClientCustomizer openAiRestClientCustomizer(ObjectMapper objectMapper) {
        return builder -> builder.requestInterceptor((request, body, execution) -> {
            byte[] modifiedBody = body;
            if (body != null && body.length > 0) {
                try {
                    Map<String, Object> map = objectMapper.readValue(body, new TypeReference<>() {});
                    if (map.containsKey("extra_body")) {
                        map.remove("extra_body");
                        modifiedBody = objectMapper.writeValueAsBytes(map);
                    }
                } catch (Exception e) {
                    // If parsing fails, send the original body unchanged
                }
            }
            return execution.execute(request, modifiedBody);
        });
    }
}
