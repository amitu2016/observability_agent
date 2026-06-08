# Test Coverage Audit Notes

## Created Test Files

### 1. InvestigationKafkaProducerTest
**Path:** `triage-agent/src/test/java/com/example/triageagent/kafka/InvestigationKafkaProducerTest.java`

**Purpose:** Unit tests for `InvestigationKafkaProducer` using plain Mockito.

**Tests:**
- `sendInvestigationRequest_sendsToCorrectTopic` - Verifies `KafkaTemplate.send()` is called with topic 'triage.requests'
- `sendInvestigationRequest_usesJobIdAsKey` - Verifies job UUID is used as the message key
- `sendInvestigationRequest_serializesMessageCorrectly` - Verifies JSON payload contains investigationId, question, createdAt
- `sendInvestigationRequest_returnsCompletableFuture` - Verifies the method returns the future from KafkaTemplate
- `sendInvestigationRequest_throwsWhenSerializationFails` - Verifies RuntimeException wrapping JsonProcessingException
- `sendInvestigationRequest_neverSendsToOtherTopics` - Negative test to ensure no accidental topic leakage

**Design Decisions:**
- Uses `@ExtendWith(MockitoExtension.class)` for plain unit tests (no Spring context)
- Mocks `KafkaTemplate<String, String>` completely
- Uses `ObjectMapper` with `findAndRegisterModules()` for Java 8 time support
- Captures arguments to verify payload contents without brittle string matching

---

### 2. InvestigationKafkaConsumerTest
**Path:** `triage-agent/src/test/java/com/example/triageagent/kafka/InvestigationKafkaConsumerTest.java`

**Purpose:** Unit tests for `InvestigationKafkaConsumer` using plain Mockito.

**Tests:**
- `consume_loadsJobByIdAndUpdatesStatusToCompleted` - Verifies job is loaded by UUID and orchestrator is called
- `consume_savesJobWithCompletedStatusAndReport` - Verifies final job has COMPLETED status and populated reportJson
- `consume_updatesStatusToRunningBeforeProcessing` - Verifies job status transitions PENDING -> RUNNING -> COMPLETED
- `consume_marksJobAsFailedWhenOrchestratorThrowsException` - Tests FAILED status on orchestrator exceptions
- `consume_savesErrorReportJsonWhenExceptionOccurs` - Verifies error message is stored in reportJson
- `consume_doesNotThrowWhenJobNotFound` - Graceful handling of missing jobs (logged and skipped)
- `consume_doesNotCrashOnMalformedJson` - Graceful handling of malformed messages
- `consume_doesNotCrashOnEmptyMessage` - Edge case for empty input
- `consume_callsInvestigateWithCorrectQuestion` - Verifies correct question is passed to orchestrator
- `consume_setsCompletedAtTimestampOnSuccess` - Verifies completedAt is set on successful completion

**Design Decisions:**
- Uses `@ExtendWith(MockitoExtension.class)` for fast unit tests
- Mocks all dependencies: `InvestigationJobRepository`, `TriageOrchestratorService`, `InvestigationMapper`
- Creates valid JSON messages using helper method to simulate real Kafka payload
- Captures saved jobs to verify state transitions and final state
- Tests error handling paths (missing job, malformed JSON, orchestrator exceptions)

---

### 3. TriageControllerTest (Existing - No Changes Needed)
**Path:** `triage-agent/src/test/java/com/example/triageagent/controller/TriageControllerTest.java`

**Status:** Already comprehensive and meets all requirements.

**Existing Tests:**
- `investigate_returns202WithJobMetadata` - POST /api/triage/investigate returns 202 with investigationId, status=PENDING, createdAt
- `investigate_savesJobWithCorrectFields` - Verifies job is persisted with correct question and PENDING status
- `getInvestigation_returnsJobDetails` - GET returns COMPLETED status and report when job is done
- `getInvestigation_returnsNullReportWhenNotCompleted` - GET returns PENDING when job not completed (no report)
- `getInvestigation_returns404WhenNotFound` - 404 for unknown job ID
- `getInvestigation_returns400ForInvalidUUID` - 400 for invalid UUID format
- `getInvestigation_handlesMalformedReportJson` - Graceful error when report JSON is invalid

---

## Verification Results

### First Run (Kafka-specific tests only)
```
./gradlew :triage-agent:test --tests '*Controller*Test' --tests '*Kafka*Test'
```
**Status:** Exit code 0 - All tests passed

### Full Module Test Run
```
./gradlew :triage-agent:test
```
**Status:** Exit code 0 - All tests passed

---

## Key Design Decisions

1. **Plain Mockito over Spring context**: Producer and Consumer tests use `@ExtendWith(MockitoExtension.class)` for fast, isolated unit tests. No real Kafka needed.

2. **No KafkaEmbedded**: Avoided `EmbeddedKafkaBroker` for simplicity and speed. Mocks provide sufficient coverage for unit testing business logic.

3. **Existing controller test coverage**: The existing `TriageControllerTest` already covered all required scenarios using `@WebMvcTest` with mocked `KafkaTemplate` (via `InvestigationKafkaProducer` mock).

4. **Error handling verification**: Consumer tests specifically verify that exceptions during investigation are caught, logged, and result in FAILED status with error JSON in reportJson field.

5. **State transition verification**: Consumer tests capture saved jobs to verify the full state machine: PENDING -> RUNNING -> COMPLETED/FAILED