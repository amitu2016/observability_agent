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
public class LogStep implements InvestigationStep {

    private static final Logger log = LoggerFactory.getLogger(LogStep.class);

    private static final String SYSTEM_PROMPT = """
            You are a log analysis assistant. Use the `query_loki_logs` tool to fetch logs
            filtered by trace_id and service name.

            Focus on:
            - ERROR and WARN level lines within the time window
            - Stack traces or exception messages
            - Log lines correlated to the trace spans from TraceStep

            Return ONLY a JSON object with this exact shape — no extra text:
            {
              "logLines": ["raw log line 1", "raw log line 2"],
              "errorPatterns": ["ErrorPattern: null pointer in UserService.getUser()", "..."]
            }
            """;

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LogStep(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public String getName() {
        return "LogStep";
    }

    @Override
    public void execute(InvestigationState state) {
        String userPrompt = buildUserPrompt(state);

        String response = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userPrompt)
                .call()
                .content();

        log.info("[LogStep] Raw response: {}", response);

        InvestigationState.StepAudit audit = new InvestigationState.StepAudit();
        audit.setStepName(getName());
        audit.setSystemPrompt(SYSTEM_PROMPT);
        audit.setUserPrompt(userPrompt);
        audit.setModelResponse(response);

        try {
            JsonNode root = objectMapper.readTree(response);

            List<String> logLines = new ArrayList<>();
            if (root.has("logLines") && root.get("logLines").isArray()) {
                root.get("logLines").forEach(n -> logLines.add(n.asText()));
            }

            // errorPatterns are parsed but not yet stored as a dedicated field;
            // they are preserved in logDetails for the HypothesisStep.
            if (root.has("errorPatterns") && root.get("errorPatterns").isArray()) {
                List<String> errorPatterns = new ArrayList<>();
                root.get("errorPatterns").forEach(n -> errorPatterns.add(n.asText()));
                log.info("[LogStep] Found {} error patterns", errorPatterns.size());
            }

            state.setLogDetails(logLines);
            log.info("[LogStep] Parsed — {} log lines", logLines.size());

        } catch (Exception e) {
            log.warn("[LogStep] JSON parse failed: {}", e.getMessage());
            List<String> fallback = new ArrayList<>();
            fallback.add(response);
            state.setLogDetails(fallback);
        }

        state.getAuditTrail().add(audit);
    }

    private String buildUserPrompt(InvestigationState state) {
        StringBuilder sb = new StringBuilder();
        sb.append("Fetch and analyze logs for the following context:\n\n");
        sb.append("- Service: ").append(state.getService() != null ? state.getService() : "unknown").append("\n");
        sb.append("- Time window: ").append(state.getTimeWindow() != null ? state.getTimeWindow() : "15m").append("\n");
        sb.append("- Trace IDs from metric exemplars:\n");
        for (String id : state.getExemplarTraceIds()) {
            sb.append("  - ").append(id).append("\n");
        }
        if (state.getExemplarTraceIds().isEmpty()) {
            sb.append("  (none — filter by service name only)\n");
        }
        return sb.toString();
    }
}