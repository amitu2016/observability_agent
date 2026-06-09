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
public class HypothesisStep implements InvestigationStep {

    private static final Logger log = LoggerFactory.getLogger(HypothesisStep.class);

    private static final String SYSTEM_PROMPT = """
            You are a root-cause analysis assistant. Synthesize only the evidence collected by the
            previous investigation steps — do not infer problems that are not supported by data.

            Rules:
            - Only assert a root cause when there is concrete evidence: non-zero HTTP 5xx error rates,
              elevated latency, error log lines, or failing spans. Zero values, empty results,
              and missing data are NOT evidence of a problem.
            - If metrics are all zero/normal and logs/traces are empty, set rootCause to
              "No anomaly detected — all signals appear normal" and confidence to 0.0.
            - Never invent a root cause from idle metrics (zero active threads, zero pool size,
              zero disk usage change, etc.) — these are normal baseline values.
            - A Kafka consumer rebalance count of 1 is normal at service startup — do not flag it
              as a problem unless the count is continuously increasing or paired with consumer lag.
            - Confidence must reflect the strength of actual evidence: 0.0 if no anomaly,
              0.5 if weak/ambiguous signals, 0.8+ only if there is clear sustained error evidence.

            Use the `generate_deeplink` tool to create Grafana deep links to relevant views.

            Explicitly list what you did NOT check (e.g., network policies, secrets rotation,
            dependency health, deployment events) so the operator knows what else to investigate.

            Return ONLY a JSON object with this exact shape — no extra text:
            {
              "rootCause": "string describing the most likely root cause, or 'No anomaly detected — all signals appear normal'",
              "confidence": 0.0,
              "deepLinks": ["https://grafana.example.com/explore?...", "..."],
              "notChecked": ["thing you could not verify", "..."]
            }
            """;

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public HypothesisStep(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public String getName() {
        return "HypothesisStep";
    }

    @Override
    public void execute(InvestigationState state) {
        String userPrompt = buildUserPrompt(state);

        String response = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userPrompt)
                .call()
                .content();

        log.info("[HypothesisStep] Raw response: {}", response);

        InvestigationState.StepAudit audit = new InvestigationState.StepAudit();
        audit.setStepName(getName());
        audit.setSystemPrompt(SYSTEM_PROMPT);
        audit.setUserPrompt(userPrompt);
        audit.setModelResponse(response);

        try {
            JsonNode root = objectMapper.readTree(InvestigationStep.extractJson(response));

            String rootCause = safeText(root, "rootCause");
            double confidence = root.has("confidence") ? root.get("confidence").asDouble() : 0.0;

            List<String> deepLinks = new ArrayList<>();
            if (root.has("deepLinks") && root.get("deepLinks").isArray()) {
                root.get("deepLinks").forEach(n -> deepLinks.add(n.asText()));
            }

            List<String> notChecked = new ArrayList<>();
            if (root.has("notChecked") && root.get("notChecked").isArray()) {
                root.get("notChecked").forEach(n -> notChecked.add(n.asText()));
            }

            state.setRootCauseHypothesis(rootCause);
            state.setConfidence(confidence);
            state.setDeepLinks(deepLinks);
            state.setNotChecked(notChecked);

            log.info("[HypothesisStep] Parsed — rootCause={}, confidence={}, deepLinks={}, notChecked={}",
                    rootCause, confidence, deepLinks.size(), notChecked.size());

        } catch (Exception e) {
            log.warn("[HypothesisStep] JSON parse failed: {}", e.getMessage());
            state.setRootCauseHypothesis("Parse error — see raw model response in audit trail.");
        }

        state.getAuditTrail().add(audit);
    }

    private String buildUserPrompt(InvestigationState state) {
        StringBuilder sb = new StringBuilder();
        sb.append("Synthesize the following investigation evidence into a root-cause hypothesis.\n\n");

        sb.append("--- SCOPE ---\n");
        sb.append("Service: ").append(state.getService() != null ? state.getService() : "unknown").append("\n");
        sb.append("Time window: ").append(state.getTimeWindow() != null ? state.getTimeWindow() : "unknown").append("\n");

        sb.append("\n--- DISCOVERED METRICS ---\n");
        if (state.getDiscoveredMetrics().isEmpty()) {
            sb.append("  (none)\n");
        } else {
            state.getDiscoveredMetrics().forEach(m -> sb.append("  - ").append(m).append("\n"));
        }

        sb.append("\n--- DISCOVERED LABELS ---\n");
        if (state.getDiscoveredLabels().isEmpty()) {
            sb.append("  (none)\n");
        } else {
            state.getDiscoveredLabels().forEach(l -> sb.append("  - ").append(l).append("\n"));
        }

        sb.append("\n--- ANOMALOUS METRIC RESULTS ---\n");
        if (state.getAnomalousMetricResults().isEmpty()) {
            sb.append("  (none)\n");
        } else {
            state.getAnomalousMetricResults().forEach(r -> sb.append("  ").append(r).append("\n"));
        }

        sb.append("\n--- EXEMPLAR TRACE IDs ---\n");
        if (state.getExemplarTraceIds().isEmpty()) {
            sb.append("  (none)\n");
        } else {
            state.getExemplarTraceIds().forEach(id -> sb.append("  - ").append(id).append("\n"));
        }

        sb.append("\n--- TRACE DETAILS ---\n");
        if (state.getTraceDetails().isEmpty()) {
            sb.append("  (none)\n");
        } else {
            state.getTraceDetails().forEach(t -> sb.append("  ").append(t).append("\n"));
        }

        sb.append("\n--- LOG DETAILS ---\n");
        if (state.getLogDetails().isEmpty()) {
            sb.append("  (none)\n");
        } else {
            state.getLogDetails().forEach(l -> sb.append("  ").append(l).append("\n"));
        }

        return sb.toString();
    }

    private static String safeText(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : null;
    }
}