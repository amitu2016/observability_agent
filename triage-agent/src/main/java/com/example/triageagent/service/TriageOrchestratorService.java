package com.example.triageagent.service;

import com.example.triageagent.investigation.InvestigationState;
import com.example.triageagent.investigation.InvestigationStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Orchestrates the sequential execution of investigation steps.
 *
 * Steps are sorted by a fixed execution order (scope → metrics → traces → logs → hypothesis)
 * to ensure the investigation pipeline runs in the correct sequence.  Spring autowires
 * List&lt;InvestigationStep&gt; in non-deterministic order, so explicit sorting is required.
 *
 * Each step receives the shared InvestigationState.  Errors are caught per-step, recorded
 * in the audit trail, and do not abort the investigation.
 */
@Service
public class TriageOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(TriageOrchestratorService.class);

    /** Canonical execution order for investigation steps. */
    private static final List<String> STEP_ORDER = List.of(
            "ScopeStep",      // 1. scope
            "MetricsStep",    // 2. metrics
            "TraceStep",      // 3. traces
            "LogStep",        // 4. logs
            "HypothesisStep"  // 5. hypothesis
    );

    private final List<InvestigationStep> sortedSteps;

    public TriageOrchestratorService(List<InvestigationStep> steps) {
        // Sort steps into canonical execution order.  Unknown/unexpected step classes
        // are appended after the known steps in their natural order.
        this.sortedSteps = steps.stream()
                .sorted(Comparator.comparingInt(s ->
                        STEP_ORDER.indexOf(s.getName()) >= 0
                                ? STEP_ORDER.indexOf(s.getName())
                                : STEP_ORDER.size()))
                .toList();

        log.info("[TriageOrchestratorService] Initialized with {} steps in order: {}",
                sortedSteps.size(),
                sortedSteps.stream().map(InvestigationStep::getName).toList());
    }

    /**
     * Runs the full investigation pipeline for the given question.
     *
     * @param question the user's symptom / investigation question
     * @return InvestigationState populated by all steps
     */
    public InvestigationState investigate(String question) {
        InvestigationState state = new InvestigationState();
        state.setQuestion(question);
        log.info("[TriageOrchestratorService] Starting investigation for question: {}", question);

        for (InvestigationStep step : sortedSteps) {
            long startMs = Instant.now().toEpochMilli();

            try {
                log.info("[TriageOrchestratorService] Executing step: {}", step.getName());
                step.execute(state);
            } catch (Exception e) {
                log.error("[TriageOrchestratorService] Step {} threw an exception; adding error audit entry and continuing",
                        step.getName(), e);

                InvestigationState.StepAudit errorAudit = new InvestigationState.StepAudit();
                errorAudit.setStepName(step.getName() + " [ERROR]");
                errorAudit.setModelResponse("Exception during step execution: " + e.getMessage());
                state.getAuditTrail().add(errorAudit);
            }

            long endMs = Instant.now().toEpochMilli();
            log.info("[TriageOrchestratorService] Step {} completed in {} ms", step.getName(), endMs - startMs);

            // Backfill timing on the last audit entry added by the step (if any).
            // Each step adds exactly one StepAudit entry on success.
            if (!state.getAuditTrail().isEmpty()) {
                InvestigationState.StepAudit lastAudit = state.getAuditTrail().get(state.getAuditTrail().size() - 1);
                // Only backfill if the audit belongs to this step and wasn't already timestamped
                // by the step itself (steps use Instant.now() in their constructor; we overwrite
                // with elapsed timing derived from wall-clock to keep it consistent here).
                if (lastAudit.getStepName().equals(step.getName()) || lastAudit.getStepName().equals(step.getName() + " [ERROR]")) {
                    lastAudit.setTimestamp(Instant.ofEpochMilli(startMs));
                }
            }
        }

        log.info("[TriageOrchestratorService] Investigation complete.  Audit entries: {}, Root cause: {}",
                state.getAuditTrail().size(), state.getRootCauseHypothesis());

        return state;
    }
}