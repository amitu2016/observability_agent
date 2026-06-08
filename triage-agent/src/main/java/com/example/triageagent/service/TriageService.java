package com.example.triageagent.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class TriageService {

    private static final String SYSTEM_PROMPT = """
            You are an observability expert assisting with incident triage.

            CRITICAL GROUNDING DISCIPLINE:
            1. ALWAYS use discovery tools first: list_datasources, list_prometheus_metric_names,
               list_prometheus_label_names, list_prometheus_label_values to understand what metrics,
               labels, and services exist before generating any PromQL or LogQL.
            2. Only generate queries using identifiers confirmed by those discovery tools.
            3. Scope every query by service name and time window.
            4. Explain your reasoning step by step before providing final answers.

            Your goal is to help users diagnose and investigate production incidents using
            Prometheus metrics, Grafana dashboards, and Jaeger traces.
            """;

    private final ChatClient chatClient;

    public TriageService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public String investigate(String question) {
        return chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(question)
                .call()
                .content();
    }
}