package com.example.triageagent.investigation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class InvestigationState {

    // Input from user
    private String question;

    // Identified by ScopeStep
    private String service;
    private String timeWindow;

    // Discovered by ScopeStep
    private List<String> discoveredMetrics = new ArrayList<>();
    private List<String> discoveredLabels = new ArrayList<>();

    // Produced by MetricsStep
    private List<String> anomalousMetricResults = new ArrayList<>();
    private List<String> exemplarTraceIds = new ArrayList<>();

    // Produced by TraceStep
    private List<String> traceDetails = new ArrayList<>();

    // Produced by LogStep
    private List<String> logDetails = new ArrayList<>();

    // Produced by HypothesisStep
    private String rootCauseHypothesis;
    private double confidence;
    private List<String> deepLinks = new ArrayList<>();
    private List<String> notChecked = new ArrayList<>();

    // Full audit trail across all steps
    private List<StepAudit> auditTrail = new ArrayList<>();

    // --- Getters & Setters ---

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }

    public String getService() { return service; }
    public void setService(String service) { this.service = service; }

    public String getTimeWindow() { return timeWindow; }
    public void setTimeWindow(String timeWindow) { this.timeWindow = timeWindow; }

    public List<String> getDiscoveredMetrics() { return discoveredMetrics; }
    public void setDiscoveredMetrics(List<String> discoveredMetrics) { this.discoveredMetrics = discoveredMetrics != null ? discoveredMetrics : new ArrayList<>(); }

    public List<String> getDiscoveredLabels() { return discoveredLabels; }
    public void setDiscoveredLabels(List<String> discoveredLabels) { this.discoveredLabels = discoveredLabels != null ? discoveredLabels : new ArrayList<>(); }

    public List<String> getAnomalousMetricResults() { return anomalousMetricResults; }
    public void setAnomalousMetricResults(List<String> anomalousMetricResults) { this.anomalousMetricResults = anomalousMetricResults != null ? anomalousMetricResults : new ArrayList<>(); }

    public List<String> getExemplarTraceIds() { return exemplarTraceIds; }
    public void setExemplarTraceIds(List<String> exemplarTraceIds) { this.exemplarTraceIds = exemplarTraceIds != null ? exemplarTraceIds : new ArrayList<>(); }

    public List<String> getTraceDetails() { return traceDetails; }
    public void setTraceDetails(List<String> traceDetails) { this.traceDetails = traceDetails != null ? traceDetails : new ArrayList<>(); }

    public List<String> getLogDetails() { return logDetails; }
    public void setLogDetails(List<String> logDetails) { this.logDetails = logDetails != null ? logDetails : new ArrayList<>(); }

    public String getRootCauseHypothesis() { return rootCauseHypothesis; }
    public void setRootCauseHypothesis(String rootCauseHypothesis) { this.rootCauseHypothesis = rootCauseHypothesis; }

    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }

    public List<String> getDeepLinks() { return deepLinks; }
    public void setDeepLinks(List<String> deepLinks) { this.deepLinks = deepLinks != null ? deepLinks : new ArrayList<>(); }

    public List<String> getNotChecked() { return notChecked; }
    public void setNotChecked(List<String> notChecked) { this.notChecked = notChecked != null ? notChecked : new ArrayList<>(); }

    public List<StepAudit> getAuditTrail() { return auditTrail; }
    public void setAuditTrail(List<StepAudit> auditTrail) { this.auditTrail = auditTrail != null ? auditTrail : new ArrayList<>(); }

    // --- Audit record classes ---

    public static class StepAudit {
        private String stepName;
        private String systemPrompt;
        private String userPrompt;
        private String modelResponse;
        private List<ToolCallAudit> toolCalls = new ArrayList<>();
        private Instant timestamp = Instant.now();

        public StepAudit() {}

        public StepAudit(String stepName, String systemPrompt, String userPrompt,
                         String modelResponse, List<ToolCallAudit> toolCalls) {
            this.stepName = stepName;
            this.systemPrompt = systemPrompt;
            this.userPrompt = userPrompt;
            this.modelResponse = modelResponse;
            this.toolCalls = toolCalls != null ? toolCalls : new ArrayList<>();
            this.timestamp = Instant.now();
        }

        public String getStepName() { return stepName; }
        public void setStepName(String stepName) { this.stepName = stepName; }

        public String getSystemPrompt() { return systemPrompt; }
        public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }

        public String getUserPrompt() { return userPrompt; }
        public void setUserPrompt(String userPrompt) { this.userPrompt = userPrompt; }

        public String getModelResponse() { return modelResponse; }
        public void setModelResponse(String modelResponse) { this.modelResponse = modelResponse; }

        public List<ToolCallAudit> getToolCalls() { return toolCalls; }
        public void setToolCalls(List<ToolCallAudit> toolCalls) { this.toolCalls = toolCalls != null ? toolCalls : new ArrayList<>(); }

        public Instant getTimestamp() { return timestamp; }
        public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    }

    public static class ToolCallAudit {
        private String toolName;
        private String arguments;
        private String result;

        public ToolCallAudit() {}

        public ToolCallAudit(String toolName, String arguments, String result) {
            this.toolName = toolName;
            this.arguments = arguments;
            this.result = result;
        }

        public String getToolName() { return toolName; }
        public void setToolName(String toolName) { this.toolName = toolName; }

        public String getArguments() { return arguments; }
        public void setArguments(String arguments) { this.arguments = arguments; }

        public String getResult() { return result; }
        public void setResult(String result) { this.result = result; }
    }
}