package com.example.triageagent.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes the fully-built ChatClient bean so steps can inject it directly.
 * Spring AI auto-configures ChatClient.Builder; we register the built instance
 * with MCP tool callbacks globally.
 */
@Configuration
public class ChatClientConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder chatClientBuilder,
                                 ToolCallbackProvider... toolCallbackProviders) {
        return chatClientBuilder
                .defaultToolCallbacks(toolCallbackProviders)
                .build();
    }
}
