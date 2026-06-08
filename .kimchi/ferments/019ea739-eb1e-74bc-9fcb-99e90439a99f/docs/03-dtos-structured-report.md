# Phase 3 & 4 Implementation: Structured DTOs and Report

**Date:** 2026-06-08  
**Phase:** Ferment Step 3 — Create structured DTOs and TriageReport

## Files Created

### DTOs (triage-agent/src/main/java/com/example/triageagent/dto/)

| File | Purpose |
|------|---------|
| `ToolCallDTO.java` | Captures tool name, arguments, result |
| `InvestigationStepDTO.java` | Step name, status, summary, tool calls, duration |
| `TriageReport.java` | Root report: question, service, timeWindow, rootCause, confidence, steps[], deepLinks[], notChecked[] |

### Mapper (triage-agent/src/main/java/com/example/triageagent/mapper/)

| File | Purpose |
|------|---------|
| `InvestigationMapper.java` | Converts InvestigationState → TriageReport, maps StepAudit → InvestigationStepDTO, ToolCallAudit → ToolCallDTO |

### Updated Files

| File | Change |
|------|--------|
| `TriageController.java` | Now returns `ResponseEntity<TriageReport>`, uses `TriageOrchestratorService` + `InvestigationMapper` |
| `TriageControllerTest.java` | Updated to mock `TriageOrchestratorService` + `InvestigationMapper`, asserts new report structure |
| `TriageAgentApplicationTest.java` | Added `@MockBean ChatClient chatClient` — required because `InvestigationStep` implementations (ScopeStep, etc.) inject ChatClient, and the integration test context needs this mock since MCP tools are disabled |

## Key Design Decisions

1. **No StepOutput class** — Tool results are captured directly in `InvestigationStepDTO.toolCalls` list, keeping the model flat.

2. **Mapper handles error-step sanitization** — Step names ending with ` [ERROR]` are stripped to `stepName` and mapped to `status = "error"`.

3. **Test compatibility** — The integration test now properly mocks `ChatClient` since all investigation steps depend on it but the test disables MCP tool callbacks.

## Verification

```bash
test -f triage-agent/src/main/java/com/example/triageagent/dto/TriageReport.java
test -f triage-agent/src/main/java/com/example/triageagent/dto/InvestigationStepDTO.java
./gradlew :triage-agent:compileJava  # BUILD SUCCESSFUL
./gradlew :triage-agent:test          # 9 tests pass
```