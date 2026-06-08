package com.example.triageagent.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO representing an investigation step's summary and captured tool results.
 */
public class InvestigationStepDTO {

    private String stepName;
    private String status; // "success" or "error"
    private String summary; // brief summary of what this step did
    private List<ToolCallDTO> toolCalls = new ArrayList<>();
    private long durationMs;

    public InvestigationStepDTO() {}

    public InvestigationStepDTO(String stepName, String status, String summary, List<ToolCallDTO> toolCalls, long durationMs) {
        this.stepName = stepName;
        this.status = status;
        this.summary = summary;
        this.toolCalls = toolCalls != null ? toolCalls : new ArrayList<>();
        this.durationMs = durationMs;
    }

    public String getStepName() {
        return stepName;
    }

    public void setStepName(String stepName) {
        this.stepName = stepName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<ToolCallDTO> getToolCalls() {
        return toolCalls;
    }

    public void setToolCalls(List<ToolCallDTO> toolCalls) {
        this.toolCalls = toolCalls != null ? toolCalls : new ArrayList<>();
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }
}