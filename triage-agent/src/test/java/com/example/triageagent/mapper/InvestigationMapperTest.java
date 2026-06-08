package com.example.triageagent.mapper;

import com.example.triageagent.dto.InvestigationStepDTO;
import com.example.triageagent.dto.TriageReport;
import com.example.triageagent.dto.ToolCallDTO;
import com.example.triageagent.investigation.InvestigationState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for InvestigationMapper.
 * Verifies conversion of InvestigationState to TriageReport.
 */
class InvestigationMapperTest {

    private final InvestigationMapper mapper = new InvestigationMapper();

    /**
     * Verifies that a fully populated InvestigationState maps correctly to a TriageReport.
     */
    @Test
    void mapsCompleteStateToReport() {
        // Build InvestigationState with all fields populated
        InvestigationState state = new InvestigationState();
        state.setQuestion("API latency spike");
        state.setService("payment-service");
        state.setTimeWindow("15m");
        state.setRootCauseHypothesis("Database connection pool exhausted");
        state.setConfidence(0.85);
        state.setDeepLinks(List.of("http://grafana/dashboard", "http://jaeger/trace"));
        state.setNotChecked(List.of("Network metrics", "Redis cache"));

        // Add audit trail entries
        InvestigationState.StepAudit scopeAudit = new InvestigationState.StepAudit(
                "ScopeStep",
                "system prompt for scope",
                "user prompt for scope",
                "Identified payment-service",
                List.of(new InvestigationState.ToolCallAudit("listServices", "{}", "payment-service"))
        );
        state.getAuditTrail().add(scopeAudit);

        InvestigationState.StepAudit metricsAudit = new InvestigationState.StepAudit(
                "MetricsStep",
                "system prompt for metrics",
                "user prompt for metrics",
                "Found anomalous connection_pool_active metric",
                List.of(
                        new InvestigationState.ToolCallAudit("queryMetrics", "{}", "results"),
                        new InvestigationState.ToolCallAudit("getExemplarTraces", "{}", "trace-123")
                )
        );
        state.getAuditTrail().add(metricsAudit);

        InvestigationState.StepAudit errorAudit = new InvestigationState.StepAudit();
        errorAudit.setStepName("LogStep [ERROR]");
        errorAudit.setModelResponse("Exception during step execution: Connection refused");
        state.getAuditTrail().add(errorAudit);

        // Map to report
        TriageReport report = mapper.toReport(state);

        // Verify top-level fields
        assertEquals("API latency spike", report.getQuestion());
        assertEquals("payment-service", report.getService());
        assertEquals("15m", report.getTimeWindow());
        assertEquals("Database connection pool exhausted", report.getRootCause());
        assertEquals(0.85, report.getConfidence(), 0.001);
        assertEquals(List.of("http://grafana/dashboard", "http://jaeger/trace"), report.getDeepLinks());
        assertEquals(List.of("Network metrics", "Redis cache"), report.getNotChecked());

        // Verify steps mapping
        assertEquals(3, report.getSteps().size(), "Should have 3 step DTOs");

        // First step: ScopeStep
        InvestigationStepDTO scopeDTO = report.getSteps().get(0);
        assertEquals("ScopeStep", scopeDTO.getStepName());
        assertEquals("success", scopeDTO.getStatus());
        assertEquals("Identified payment-service", scopeDTO.getSummary());
        assertEquals(1, scopeDTO.getToolCalls().size());
        assertEquals("listServices", scopeDTO.getToolCalls().get(0).getToolName());

        // Second step: MetricsStep
        InvestigationStepDTO metricsDTO = report.getSteps().get(1);
        assertEquals("MetricsStep", metricsDTO.getStepName());
        assertEquals("success", metricsDTO.getStatus());
        assertEquals(2, metricsDTO.getToolCalls().size());

        // Third step: LogStep [ERROR] - should have error status and sanitized name
        InvestigationStepDTO errorDTO = report.getSteps().get(2);
        assertEquals("LogStep", errorDTO.getStepName(), "Error suffix should be stripped");
        assertEquals("error", errorDTO.getStatus());
        assertTrue(errorDTO.getSummary().contains("Connection refused"));
    }

    /**
     * Verifies that an empty or minimally populated InvestigationState maps gracefully.
     */
    @Test
    void handlesEmptyState() {
        // Build InvestigationState with only question set
        InvestigationState state = new InvestigationState();
        state.setQuestion("Test question");
        // All other fields remain at defaults

        TriageReport report = mapper.toReport(state);

        assertEquals("Test question", report.getQuestion());
        assertNull(report.getService());
        assertNull(report.getTimeWindow());
        assertNull(report.getRootCause());
        assertEquals(0.0, report.getConfidence(), 0.001);
        assertNotNull(report.getDeepLinks());
        assertTrue(report.getDeepLinks().isEmpty());
        assertNotNull(report.getNotChecked());
        assertTrue(report.getNotChecked().isEmpty());
        assertNotNull(report.getSteps());
        assertTrue(report.getSteps().isEmpty());
    }

    /**
     * Verifies JSON serialization of TriageReport produces valid JSON.
     */
    @Test
    void testReportJsonSerialization() {
        InvestigationState state = new InvestigationState();
        state.setQuestion("Memory leak investigation");
        state.setService("user-service");
        state.setTimeWindow("1h");
        state.setRootCauseHypothesis("Unclosed file descriptors");
        state.setConfidence(0.72);
        state.setDeepLinks(List.of("http://grafana/panel/1"));
        state.setNotChecked(List.of("GC logs"));

        InvestigationState.StepAudit audit = new InvestigationState.StepAudit(
                "ScopeStep",
                "sys",
                "user",
                "Discovered service",
                List.of(new InvestigationState.ToolCallAudit("listServices", "{}", "user-service"))
        );
        state.getAuditTrail().add(audit);

        TriageReport report = mapper.toReport(state);

        // Verify Jackson can serialize without exceptions
        com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        objectMapper.findAndRegisterModules(); // Register JavaTimeModule for Instant support

        assertDoesNotThrow(() -> {
            String json = objectMapper.writeValueAsString(report);
            assertNotNull(json);
            assertTrue(json.contains("\"question\":\"Memory leak investigation\""));
            assertTrue(json.contains("\"service\":\"user-service\""));
            assertTrue(json.contains("\"rootCause\":\"Unclosed file descriptors\""));
            assertTrue(json.contains("\"confidence\":0.72"));
            assertTrue(json.contains("\"stepName\":\"ScopeStep\""));
            assertTrue(json.contains("\"status\":\"success\""));
            assertTrue(json.contains("\"deepLinks\":[\"http://grafana/panel/1\"]"));
            assertTrue(json.contains("\"notChecked\":[\"GC logs\"]"));
        });
    }

    /**
     * Verifies that null tool calls in audit are handled gracefully.
     */
    @Test
    void handlesNullToolCalls() {
        InvestigationState state = new InvestigationState();
        state.setQuestion("Test");

        InvestigationState.StepAudit audit = new InvestigationState.StepAudit(
                "TestStep", null, null, "Response text", null
        );
        state.getAuditTrail().add(audit);

        TriageReport report = mapper.toReport(state);

        assertEquals(1, report.getSteps().size());
        assertNotNull(report.getSteps().get(0).getToolCalls());
        assertTrue(report.getSteps().get(0).getToolCalls().isEmpty());
    }

    /**
     * Verifies that long model responses are truncated in summary.
     */
    @Test
    void truncatesLongResponses() {
        InvestigationState state = new InvestigationState();
        state.setQuestion("Test");

        String longResponse = "A".repeat(300);
        InvestigationState.StepAudit audit = new InvestigationState.StepAudit(
                "TestStep", null, null, longResponse, null
        );
        state.getAuditTrail().add(audit);

        TriageReport report = mapper.toReport(state);

        InvestigationStepDTO dto = report.getSteps().get(0);
        assertEquals(200, dto.getSummary().length());
        assertTrue(dto.getSummary().endsWith("..."), "Summary should end with ellipsis");
    }
}