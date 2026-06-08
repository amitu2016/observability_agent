package com.example.triageagent.dto;

import java.util.Map;

/**
 * Response DTO for investigation job endpoints.
 * Used for both the initial POST (202) and the polling GET.
 */
public class InvestigationJobResponse {

    private String investigationId;
    private String question;
    private String status;
    private String createdAt;
    private String completedAt;
    private Map<String, Object> report;

    /**
     * Constructor for POST /investigate response (202 Accepted).
     */
    public InvestigationJobResponse(String investigationId, String status, String createdAt) {
        this.investigationId = investigationId;
        this.status = status;
        this.createdAt = createdAt;
    }

    /**
     * Full constructor for GET /investigation/{id} response.
     */
    public InvestigationJobResponse(String investigationId, String question, String status,
                                     String createdAt, String completedAt, Map<String, Object> report) {
        this.investigationId = investigationId;
        this.question = question;
        this.status = status;
        this.createdAt = createdAt;
        this.completedAt = completedAt;
        this.report = report;
    }

    public String getInvestigationId() {
        return investigationId;
    }

    public void setInvestigationId(String investigationId) {
        this.investigationId = investigationId;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(String completedAt) {
        this.completedAt = completedAt;
    }

    public Map<String, Object> getReport() {
        return report;
    }

    public void setReport(Map<String, Object> report) {
        this.report = report;
    }
}