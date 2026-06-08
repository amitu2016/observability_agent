package com.example.jaegermcp.config;

import com.example.jaegermcp.tool.JaegerTraceTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JaegerToolConfiguration {

    @Bean
    public ToolCallbackProvider jaegerToolCallbackProvider(JaegerTraceTools jaegerTraceTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(jaegerTraceTools)
                .build();
    }
}
