package com.example.triageagent.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Root DTO for the structured triage investigation report.
 * Contains the identified service, root cause hypothesis, confidence,
 * full audit trail of steps, deep links, and items not checked.
 */
public class TriageReport {

    private String question;       // original symptom
    private String service;        // identified service
    private String timeWindow;     // e.g. "15m"
    private String rootCause;      // hypothesis text
    private double confidence;     // 0.0-1.0
    private List<InvestigationStepDTO> steps;
    private List<String> deepLinks;
    private List<String> notChecked;

    public TriageReport() {
        this.steps = new ArrayList<>();
        this.deepLinks = new ArrayList<>();
        this.notChecked = new ArrayList<>();
    }

    public TriageReport(String question, String service, String timeWindow, String rootCause,
                        double confidence, List<InvestigationStepDTO> steps,
                        List<String> deepLinks, List<String> notChecked) {
        this.question = question;
        this.service = service;
        this.timeWindow = timeWindow;
        this.rootCause = rootCause;
        this.confidence = confidence;
        this.steps = steps != null ? steps : new ArrayList<>();
        this.deepLinks = deepLinks != null ? deepLinks : new ArrayList<>();
        this.notChecked = notChecked != null ? notChecked : new ArrayList<>();
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public String getTimeWindow() {
        return timeWindow;
    }

    public void setTimeWindow(String timeWindow) {
        this.timeWindow = timeWindow;
    }

    public String getRootCause() {
        return rootCause;
    }

    public void setRootCause(String rootCause) {
        this.rootCause = rootCause;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public List<InvestigationStepDTO> getSteps() {
        return steps;
    }

    public void setSteps(List<InvestigationStepDTO> steps) {
        this.steps = steps != null ? steps : new ArrayList<>();
    }

    public List<String> getDeepLinks() {
        return deepLinks;
    }

    public void setDeepLinks(List<String> deepLinks) {
        this.deepLinks = deepLinks != null ? deepLinks : new ArrayList<>();
    }

    public List<String> getNotChecked() {
        return notChecked;
    }

    public void setNotChecked(List<String> notChecked) {
        this.notChecked = notChecked != null ? notChecked : new ArrayList<>();
    }
}