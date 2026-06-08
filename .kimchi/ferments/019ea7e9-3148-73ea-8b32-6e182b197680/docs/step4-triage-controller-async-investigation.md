# Step 4: TriageController Async Investigation Implementation

## Date: 2026-06-08

## Files Modified

| File | Action |
|------|--------|
| `triage-agent/src/main/java/com/example/triageagent/controller/TriageController.java` | Rewritten |
| `triage-agent/src/main/java/com/example/triageagent/dto/InvestigationJobResponse.java` | Created |
| `triage-agent/src/test/java/com/example/triageagent/controller/TriageControllerTest.java` | Rewritten |

## Changes Summary

### 1. TriageController.java
- **Removed**: `TriageOrchestratorService` and `InvestigationMapper` dependencies
- **Added**: `InvestigationJobRepository`, `InvestigationKafkaProducer`, `ObjectMapper` dependencies
- **POST /api/triage/investigate**: Now returns `202 Accepted` with job metadata (id, status, createdAt)
  - Creates `InvestigationJob`, saves to DB, sends to Kafka, returns immediately
- **GET /api/triage/investigation/{id}**: New endpoint for polling investigation status
  - Returns job details including report only when status == COMPLETED
  - Handles 404 (not found) and 400 (invalid UUID)

### 2. InvestigationJobResponse.java (NEW DTO)
- Two constructors:
  - Short form: `investigationId, status, createdAt` (for 202 response)
  - Full form: `investigationId, question, status, createdAt, completedAt, report`
- Supports nullable `report` field for pending jobs

### 3. TriageControllerTest.java
- Replaced mocks for `TriageOrchestratorService` and `InvestigationMapper` with `InvestigationJobRepository` and `InvestigationKafkaProducer`
- 8 test cases covering:
  - Controller beans loaded
  - POST returns 202 with job metadata
  - POST saves job with correct fields
  - GET returns job details with report (COMPLETED status)
  - GET returns null report when pending
  - GET returns 404 for missing job
  - GET returns 400 for invalid UUID
  - GET handles malformed report JSON gracefully

## Test Results
```
./gradlew :triage-agent:test --tests '*Controller*Test'
BUILD SUCCESSFUL
8 tests passed
```

## Verification
```bash
grep -q 'status(202)' TriageController.java  # PASS
grep -q '@GetMapping.*investigation' TriageController.java  # PASS
```

## Key Design Decisions
1. **Report deserialization**: `extractReport()` returns `Map<String, Object>` to avoid tight coupling to `TriageReport` structure
2. **Malformed JSON handling**: Returns `{"error": "...", "raw": "..."}` instead of failing the request
3. **JPA ID generation**: Tests use reflection to simulate UUID generation since JPA assigns ID after save

## Notes
- `TriageRequest.java` and `TriageReport.java` preserved (still needed by Kafka consumer)
- No changes to other services, entities, or repositories