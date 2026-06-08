package com.example.triageagent.investigation;

public interface InvestigationStep {
    String getName();
    void execute(InvestigationState state);
}