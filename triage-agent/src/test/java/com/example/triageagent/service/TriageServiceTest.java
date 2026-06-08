package com.example.triageagent.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

import org.springframework.ai.tool.ToolCallbackProvider;

@ExtendWith(MockitoExtension.class)
class TriageServiceTest {

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec responseSpec;

    private TriageService triageService;

    @BeforeEach
    void setUp() {
        // Stub before constructor runs to avoid NPE when .build() is called
        when(chatClientBuilder.defaultToolCallbacks(any(ToolCallbackProvider[].class))).thenReturn(chatClientBuilder);
        when(chatClientBuilder.build()).thenReturn(chatClient);
        triageService = new TriageService(chatClientBuilder);
    }

    @Test
    void shouldReturnInvestigationResult() {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("CPU spike detected");

        String result = triageService.investigate("Why is CPU high?");

        assertThat(result).isEqualTo("CPU spike detected");
        verify(requestSpec).user("Why is CPU high?");
    }

    @Test
    void shouldPassSystemPromptToChatClient() {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("OK");

        triageService.investigate("test question");

        verify(requestSpec).system(contains("observability expert"));
    }
}