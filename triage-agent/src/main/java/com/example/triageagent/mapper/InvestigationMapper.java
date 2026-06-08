package com.example.triageagent.mapper;

import com.example.triageagent.dto.InvestigationStepDTO;
import com.example.triageagent.dto.TriageReport;
import com.example.triageagent.dto.ToolCallDTO;
import com.example.triageagent.investigation.InvestigationState;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps internal InvestigationState to structured TriageReport DTOs.
 */
@Service
public class InvestigationMapper {

    /**
     * Converts an InvestigationState into a TriageReport.
     *
     * @param state the internal investigation state with full audit trail
     * @return TriageReport with all findings, steps, deep links, and not-checked items
     */
    public TriageReport toReport(InvestigationState state) {
        TriageReport report = new TriageReport();

        // Top-level fields
        report.setQuestion(state.getQuestion());
        report.setService(state.getService());
        report.setTimeWindow(state.getTimeWindow());
        report.setRootCause(state.getRootCauseHypothesis());
        report.setConfidence(state.getConfidence());
        report.setDeepLinks(new ArrayList<>(state.getDeepLinks()));
        report.setNotChecked(new ArrayList<>(state.getNotChecked()));

        // Map audit trail to step DTOs
        List<InvestigationStepDTO> steps = new ArrayList<>();
        for (InvestigationState.StepAudit audit : state.getAuditTrail()) {
            steps.add(toStepDTO(audit));
        }
        report.setSteps(steps);

        return report;
    }

    /**
     * Converts a StepAudit to InvestigationStepDTO.
     */
    private InvestigationStepDTO toStepDTO(InvestigationState.StepAudit audit) {
        InvestigationStepDTO dto = new InvestigationStepDTO();

        dto.setStepName(sanitizeStepName(audit.getStepName()));
        dto.setStatus(determineStatus(audit));
        dto.setSummary(buildSummary(audit));
        dto.setToolCalls(toToolCallDTOs(audit.getToolCalls()));
        dto.setDurationMs(calculateDuration(audit));

        return dto;
    }

    /**
     * Strips error suffix from step name for clean reporting.
     */
    private String sanitizeStepName(String stepName) {
        if (stepName != null && stepName.endsWith(" [ERROR]")) {
            return stepName.substring(0, stepName.length() - 8);
        }
        return stepName != null ? stepName : "Unknown";
    }

    /**
     * Determines status based on step name suffix.
     */
    private String determineStatus(InvestigationState.StepAudit audit) {
        if (audit.getStepName() != null && audit.getStepName().endsWith(" [ERROR]")) {
            return "error";
        }
        return "success";
    }

    /**
     * Builds a brief summary from the model response or error message.
     */
    private String buildSummary(InvestigationState.StepAudit audit) {
        String response = audit.getModelResponse();
        if (response == null || response.isBlank()) {
            return "No response recorded.";
        }
        // Truncate to ~200 chars for summary brevity
        if (response.length() > 200) {
            return response.substring(0, 197) + "...";
        }
        return response;
    }

    /**
     * Converts ToolCallAudit list to ToolCallDTO list.
     */
    private List<ToolCallDTO> toToolCallDTOs(List<InvestigationState.ToolCallAudit> toolCalls) {
        if (toolCalls == null) {
            return new ArrayList<>();
        }
        List<ToolCallDTO> dtos = new ArrayList<>();
        for (InvestigationState.ToolCallAudit audit : toolCalls) {
            ToolCallDTO dto = new ToolCallDTO();
            dto.setToolName(audit.getToolName());
            dto.setArguments(audit.getArguments());
            dto.setResult(audit.getResult());
            dtos.add(dto);
        }
        return dtos;
    }

    /**
     * Calculates duration in milliseconds from audit timestamp to now (or uses a sentinel).
     * Since StepAudit records only a start timestamp, we approximate using a heuristic:
     * if there's at least one tool call, assume minimal 10ms; actual timing is recorded
     * via elapsed wall-clock in the orchestrator, but not persisted per audit entry.
     * For accurate reporting, steps should record their own duration, but we fallback to 0.
     */
    private long calculateDuration(InvestigationState.StepAudit audit) {
        if (audit.getTimestamp() != null) {
            // Rough estimate: now minus timestamp. Real implementations would store endMs.
            // We return 0 as a sentinel indicating "duration not recorded".
            return 0;
        }
        return 0;
    }
}