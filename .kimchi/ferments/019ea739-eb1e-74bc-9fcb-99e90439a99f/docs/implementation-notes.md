# TriageOrchestratorService Implementation Notes

## File created
`triage-agent/src/main/java/com/example/triageagent/service/TriageOrchestratorService.java`

## Design decisions

### ChatClient — not injected into orchestrator
All five step classes (ScopeStep, MetricsStep, TraceStep, LogStep, HypothesisStep) already
receive their own `ChatClient` via constructor injection. Spring autowires each step with the
shared `ChatClient.Builder`-backed bean. The orchestrator does NOT build or pass a `ChatClient`,
which avoids the complexity of updating the `InvestigationStep` interface signature.

### Step ordering — explicit sort required
Spring injects `List<InvestigationStep>` in non-deterministic order. The orchestrator sorts
steps by a fixed `STEP_ORDER` list: ScopeStep → MetricsStep → TraceStep → LogStep → HypothesisStep.
Unknown step classes (not in the canonical order) are appended after known steps.

Comparator uses `STEP_ORDER.indexOf(name)` with fallback to `STEP_ORDER.size()` (appended).

### Error resilience
Each step is wrapped in a try/catch. On exception:
- An `InvestigationState.StepAudit` entry is added with `stepName + " [ERROR]"` and the exception message
- The exception is logged but execution continues
- The investigation state is returned with whatever data was accumulated

### Timing
- Wall-clock start/end timestamps captured per step using `Instant.now().toEpochMilli()`
- Duration logged: `stepName + " completed in X ms"`
- The orchestrator backfills the `timestamp` field on the last audit entry (which was
  set by the step to `Instant.now()` at construction time) with `Instant.ofEpochMilli(startMs)`
  for consistency with the orchestrator's own clock.

### InvestigationState construction
`InvestigationState` has only a no-arg constructor. The orchestrator uses:
```java
InvestigationState state = new InvestigationState();
state.setQuestion(question);
```

## No changes required to existing step classes
Since each step class already has `ChatClient` injected via its own constructor, no changes
were needed to:
- `InvestigationStep` interface
- `ScopeStep`, `MetricsStep`, `TraceStep`, `LogStep`, `HypothesisStep`
- `InvestigationState`

## Verification
```bash
test -f triage-agent/src/main/java/com/example/triageagent/service/TriageOrchestratorService.java  # PASS
./gradlew :triage-agent:compileJava  # BUILD SUCCESSFUL
```