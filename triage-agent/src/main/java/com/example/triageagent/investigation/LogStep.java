package com.example.triageagent.investigation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Component
public class LogStep implements InvestigationStep {

    private static final Logger log = LoggerFactory.getLogger(LogStep.class);

    private static final String SYSTEM_PROMPT = """
            You are a log analysis assistant. Use the `query_loki_logs` tool to fetch logs.

            IMPORTANT stream selector — all services log to this fixed pattern:
              {job="java-logs", filename="/var/log/app/{service-name}.log"}
            For example, for downstream-service use:
              {job="java-logs", filename="/var/log/app/downstream-service.log"}
            Never use {job="downstream-service"} or any other label — it will return nothing.

            IMPORTANT: The user prompt contains the exact Loki datasource UID and RFC3339 start/end
            timestamps to use. Always use those values when calling query_loki_logs — never guess,
            compute, or use placeholder values like "now-15m".

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
            JsonNode root = objectMapper.readTree(InvestigationStep.extractJson(response));

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
        long windowMinutes = parseWindowMinutes(state.getTimeWindow());
        Instant end = Instant.now();
        Instant start = end.minus(windowMinutes, ChronoUnit.MINUTES);

        StringBuilder sb = new StringBuilder();
        sb.append("Fetch and analyze logs for the following context:\n\n");
        sb.append("- Loki datasource UID: ").append(state.getLokiUid() != null ? state.getLokiUid() : "unknown — call list_datasources first").append("\n");
        sb.append("- Service: ").append(state.getService() != null ? state.getService() : "unknown").append("\n");
        sb.append("- Start time (RFC3339): ").append(start).append("\n");
        sb.append("- End time (RFC3339): ").append(end).append("\n");
        sb.append("- Trace IDs from metric exemplars:\n");
        for (String id : state.getExemplarTraceIds()) {
            sb.append("  - ").append(id).append("\n");
        }
        if (state.getExemplarTraceIds().isEmpty()) {
            sb.append("  (none — filter by service name only)\n");
        }
        return sb.toString();
    }

    private long parseWindowMinutes(String timeWindow) {
        if (timeWindow == null) return 15;
        try {
            if (timeWindow.endsWith("m")) return Long.parseLong(timeWindow.replace("m", ""));
            if (timeWindow.endsWith("h")) return Long.parseLong(timeWindow.replace("h", "")) * 60;
        } catch (NumberFormatException ignored) {}
        return 15;
    }
}