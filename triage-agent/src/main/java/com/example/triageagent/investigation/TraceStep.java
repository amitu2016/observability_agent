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
public class TraceStep implements InvestigationStep {

    private static final Logger log = LoggerFactory.getLogger(TraceStep.class);

    private static final String SYSTEM_PROMPT = """
            You are a trace analysis assistant. Use the `get_trace_by_id` tool to fetch individual
            traces or `search_traces` to discover traces for the given service.

            Identify:
            - Failing or error-marked spans
            - Slow dependencies (high duration spans)
            - Downstream services involved in the failure chain
            - Any correlation IDs that link to the metric exemplars

            Return ONLY a JSON object with this exact shape — no extra text:
            {
              "traceDetails": ["raw trace summary 1", "raw trace summary 2"],
              "failingSpanIds": ["span_id_1", "span_id_2"]
            }
            """;

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TraceStep(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public String getName() {
        return "TraceStep";
    }

    @Override
    public void execute(InvestigationState state) {
        String userPrompt = buildUserPrompt(state);

        String response = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userPrompt)
                .call()
                .content();

        log.info("[TraceStep] Raw response: {}", response);

        InvestigationState.StepAudit audit = new InvestigationState.StepAudit();
        audit.setStepName(getName());
        audit.setSystemPrompt(SYSTEM_PROMPT);
        audit.setUserPrompt(userPrompt);
        audit.setModelResponse(response);

        try {
            JsonNode root = objectMapper.readTree(response);

            List<String> traceDetails = new ArrayList<>();
            if (root.has("traceDetails") && root.get("traceDetails").isArray()) {
                root.get("traceDetails").forEach(n -> traceDetails.add(n.asText()));
            }

            // failingSpanIds are parsed but not yet stored as a dedicated field;
            // they are preserved in traceDetails for the HypothesisStep to consume.
            if (root.has("failingSpanIds") && root.get("failingSpanIds").isArray()) {
                List<String> failingSpanIds = new ArrayList<>();
                root.get("failingSpanIds").forEach(n -> failingSpanIds.add(n.asText()));
                log.info("[TraceStep] Found failing spans: {}", failingSpanIds);
            }

            state.setTraceDetails(traceDetails);
            log.info("[TraceStep] Parsed — {} trace details", traceDetails.size());

        } catch (Exception e) {
            log.warn("[TraceStep] JSON parse failed: {}", e.getMessage());
            List<String> fallback = new ArrayList<>();
            fallback.add(response);
            state.setTraceDetails(fallback);
        }

        state.getAuditTrail().add(audit);
    }

    private String buildUserPrompt(InvestigationState state) {
        StringBuilder sb = new StringBuilder();
        sb.append("Fetch and analyze traces for the following context:\n\n");
        sb.append("- Service: ").append(state.getService() != null ? state.getService() : "unknown").append("\n");
        sb.append("- Trace IDs from metric exemplars:\n");
        for (String id : state.getExemplarTraceIds()) {
            sb.append("  - ").append(id).append("\n");
        }
        if (state.getExemplarTraceIds().isEmpty()) {
            sb.append("  (none found — use search_traces to discover traces)\n");
        }
        return sb.toString();
    }
}