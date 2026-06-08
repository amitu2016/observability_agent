package com.example.triageagent.dto;

public class TriageRequest {

    private String question;

    public TriageRequest() {
    }

    public TriageRequest(String question) {
        this.question = question;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }
}