package com.example.triageagent.investigation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ScopeStep implements InvestigationStep {

    private static final Logger log = LoggerFactory.getLogger(ScopeStep.class);

    private static final String SYSTEM_PROMPT = """
            You are a discovery assistant for production incidents.

            Use the following MCP tools to understand the environment before returning results:
            1. list_datasources — to find available Grafana datasources
            2. list_prometheus_metric_names — to discover metrics related to the symptom
            3. list_prometheus_label_names and list_prometheus_label_values — to understand label taxonomy

            Identify:
            - The affected service name from the user's symptom
            - The relevant time window (e.g., "15m", "1h")
            - Relevant metric names
            - Relevant label names

            Return ONLY a JSON object with this exact shape — no extra text:
            {
              "service": "string or null",
              "timeWindow": "string (e.g. '15m') or null",
              "relevantMetrics": ["metric_name1", "metric_name2"],
              "relevantLabels": ["label_name1", "label_name2"]
            }
            """;

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ScopeStep(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public String getName() {
        return "ScopeStep";
    }

    @Override
    public void execute(InvestigationState state) {
        String userPrompt = "Investigate this symptom and return scope information:\n" + state.getQuestion();

        String response = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userPrompt)
                .call()
                .content();

        log.info("[ScopeStep] Raw response: {}", response);

        InvestigationState.StepAudit audit = new InvestigationState.StepAudit();
        audit.setStepName(getName());
        audit.setSystemPrompt(SYSTEM_PROMPT);
        audit.setUserPrompt(userPrompt);
        audit.setModelResponse(response);

        try {
            JsonNode root = objectMapper.readTree(response);

            String service = safeText(root, "service");
            String timeWindow = safeText(root, "timeWindow");

            List<String> metrics = new ArrayList<>();
            if (root.has("relevantMetrics") && root.get("relevantMetrics").isArray()) {
                root.get("relevantMetrics").forEach(n -> metrics.add(n.asText()));
            }

            List<String> labels = new ArrayList<>();
            if (root.has("relevantLabels") && root.get("relevantLabels").isArray()) {
                root.get("relevantLabels").forEach(n -> labels.add(n.asText()));
            }

            state.setService(service);
            state.setTimeWindow(timeWindow);
            state.setDiscoveredMetrics(metrics);
            state.setDiscoveredLabels(labels);

            log.info("[ScopeStep] Parsed — service={}, timeWindow={}, metrics={}, labels={}",
                    service, timeWindow, metrics, labels);

        } catch (Exception e) {
            log.warn("[ScopeStep] JSON parse failed, storing raw response: {}", e.getMessage());
            // Fallback: stash raw response in a list so HypothesisStep can still reason over it
            List<String> fallback = new ArrayList<>();
            fallback.add(response);
            state.setDiscoveredMetrics(fallback);
        }

        state.getAuditTrail().add(audit);
    }

    private static String safeText(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : null;
    }
}