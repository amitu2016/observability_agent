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
public class MetricsStep implements InvestigationStep {

    private static final Logger log = LoggerFactory.getLogger(MetricsStep.class);

    private static final String SYSTEM_PROMPT = """
            You are a PromQL assistant. Use the `query_prometheus` tool to run scoped queries
            for the given service and time window against the discovered metrics.

            For each metric:
            - Run a PromQL query scoped to the service name and time window.
            - Look for anomalous values (high error rates, latency spikes, drops in throughput).
            - Extract any `trace_id` exemplars or OpenTelemetry trace references embedded in
              the metric results.

            Return ONLY a JSON object with this exact shape — no extra text:
            {
              "promqlResults": ["raw result line 1", "raw result line 2"],
              "traceIds": ["trace_id_abc", "trace_id_xyz"]
            }
            """;

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MetricsStep(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public String getName() {
        return "MetricsStep";
    }

    @Override
    public void execute(InvestigationState state) {
        String userPrompt = buildUserPrompt(state);

        String response = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userPrompt)
                .call()
                .content();

        log.info("[MetricsStep] Raw response: {}", response);

        InvestigationState.StepAudit audit = new InvestigationState.StepAudit();
        audit.setStepName(getName());
        audit.setSystemPrompt(SYSTEM_PROMPT);
        audit.setUserPrompt(userPrompt);
        audit.setModelResponse(response);

        try {
            JsonNode root = objectMapper.readTree(response);

            List<String> promqlResults = new ArrayList<>();
            if (root.has("promqlResults") && root.get("promqlResults").isArray()) {
                root.get("promqlResults").forEach(n -> promqlResults.add(n.asText()));
            }

            List<String> traceIds = new ArrayList<>();
            if (root.has("traceIds") && root.get("traceIds").isArray()) {
                root.get("traceIds").forEach(n -> traceIds.add(n.asText()));
            }

            state.setAnomalousMetricResults(promqlResults);
            state.setExemplarTraceIds(traceIds);

            log.info("[MetricsStep] Parsed — {} results, {} trace IDs",
                    promqlResults.size(), traceIds.size());

        } catch (Exception e) {
            log.warn("[MetricsStep] JSON parse failed: {}", e.getMessage());
            List<String> fallback = new ArrayList<>();
            fallback.add(response);
            state.setAnomalousMetricResults(fallback);
        }

        state.getAuditTrail().add(audit);
    }

    private String buildUserPrompt(InvestigationState state) {
        StringBuilder sb = new StringBuilder();
        sb.append("Run PromQL queries for the following scope:\n\n");
        sb.append("- Service: ").append(state.getService() != null ? state.getService() : "unknown").append("\n");
        sb.append("- Time window: ").append(state.getTimeWindow() != null ? state.getTimeWindow() : "15m").append("\n");
        sb.append("- Discovered metrics:\n");
        for (String m : state.getDiscoveredMetrics()) {
            sb.append("  - ").append(m).append("\n");
        }
        sb.append("- Discovered labels:\n");
        for (String l : state.getDiscoveredLabels()) {
            sb.append("  - ").append(l).append("\n");
        }
        return sb.toString();
    }
}