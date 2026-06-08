package com.example.triageagent.dto;

/**
 * DTO representing a single tool call captured during investigation.
 */
public class ToolCallDTO {

    private String toolName;
    private String arguments;
    private String result;

    public ToolCallDTO() {}

    public ToolCallDTO(String toolName, String arguments, String result) {
        this.toolName = toolName;
        this.arguments = arguments;
        this.result = result;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public String getArguments() {
        return arguments;
    }

    public void setArguments(String arguments) {
        this.arguments = arguments;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }
}