package com.example.triageagent.dto;

public class TriageResponse {

    private String answer;

    public TriageResponse() {
    }

    public TriageResponse(String answer) {
        this.answer = answer;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }
}