package com.example.triageagent.investigation;

import com.example.triageagent.service.TriageOrchestratorService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TriageOrchestratorService.
 * Verifies step ordering, error handling, and audit trail population.
 */
class TriageOrchestratorTest {

    /**
     * Verifies that steps are executed in correct order (scope → metrics → traces → logs → hypothesis)
     * even when provided in reverse order. Also verifies audit trail is populated.
     */
    @Test
    void runsStepsInOrder_andPopulatesAuditTrail() {
        // Create mock steps
        InvestigationStep scope = mock(InvestigationStep.class, "scope");
        InvestigationStep metrics = mock(InvestigationStep.class, "metrics");
        InvestigationStep traces = mock(InvestigationStep.class, "traces");
        InvestigationStep logs = mock(InvestigationStep.class, "logs");
        InvestigationStep hypothesis = mock(InvestigationStep.class, "hypothesis");

        when(scope.getName()).thenReturn("ScopeStep");
        when(metrics.getName()).thenReturn("MetricsStep");
        when(traces.getName()).thenReturn("TraceStep");
        when(logs.getName()).thenReturn("LogStep");
        when(hypothesis.getName()).thenReturn("HypothesisStep");

        // Each mock adds an audit entry when execute() is called
        doAnswer(invocation -> {
            InvestigationState s = invocation.getArgument(0);
            s.getAuditTrail().add(new InvestigationState.StepAudit("ScopeStep", "sys", "user", "scope response", null));
            return null;
        }).when(scope).execute(any());

        doAnswer(invocation -> {
            InvestigationState s = invocation.getArgument(0);
            s.getAuditTrail().add(new InvestigationState.StepAudit("MetricsStep", "sys", "user", "metrics response", null));
            return null;
        }).when(metrics).execute(any());

        doAnswer(invocation -> {
            InvestigationState s = invocation.getArgument(0);
            s.getAuditTrail().add(new InvestigationState.StepAudit("TraceStep", "sys", "user", "traces response", null));
            return null;
        }).when(traces).execute(any());

        doAnswer(invocation -> {
            InvestigationState s = invocation.getArgument(0);
            s.getAuditTrail().add(new InvestigationState.StepAudit("LogStep", "sys", "user", "logs response", null));
            return null;
        }).when(logs).execute(any());

        doAnswer(invocation -> {
            InvestigationState s = invocation.getArgument(0);
            s.getAuditTrail().add(new InvestigationState.StepAudit("HypothesisStep", "sys", "user", "hypothesis response", null));
            return null;
        }).when(hypothesis).execute(any());

        // Create orchestrator with steps in REVERSE order to prove sorting works
        TriageOrchestratorService orchestrator = new TriageOrchestratorService(
                List.of(hypothesis, logs, traces, metrics, scope)
        );

        InvestigationState state = orchestrator.investigate("test question");

        // Verify InOrder execution
        var inOrder = inOrder(scope, metrics, traces, logs, hypothesis);
        inOrder.verify(scope).execute(state);
        inOrder.verify(metrics).execute(state);
        inOrder.verify(traces).execute(state);
        inOrder.verify(logs).execute(state);
        inOrder.verify(hypothesis).execute(state);

        // Assert state is populated correctly
        assertEquals("test question", state.getQuestion());
        assertEquals(5, state.getAuditTrail().size(), "Should have one audit entry per step");

        // Verify audit entry order matches step order
        assertEquals("ScopeStep", state.getAuditTrail().get(0).getStepName());
        assertEquals("MetricsStep", state.getAuditTrail().get(1).getStepName());
        assertEquals("TraceStep", state.getAuditTrail().get(2).getStepName());
        assertEquals("LogStep", state.getAuditTrail().get(3).getStepName());
        assertEquals("HypothesisStep", state.getAuditTrail().get(4).getStepName());
    }

    /**
     * Verifies that when a step throws an exception, the orchestrator:
     * - Continues to execute subsequent steps
     * - Records an error audit entry for the failed step
     */
    @Test
    void continuesOnStepError_andCapturesErrorAudit() {
        InvestigationStep scope = mock(InvestigationStep.class, "scope");
        InvestigationStep metrics = mock(InvestigationStep.class, "metrics");

        when(scope.getName()).thenReturn("ScopeStep");
        when(metrics.getName()).thenReturn("MetricsStep");

        // Scope throws an exception
        doThrow(new RuntimeException("Scope failed"))
                .when(scope).execute(any());

        // Metrics succeeds - adds audit entry
        doAnswer(invocation -> {
            InvestigationState s = invocation.getArgument(0);
            s.getAuditTrail().add(new InvestigationState.StepAudit("MetricsStep", "sys", "user", "metrics response", null));
            return null;
        }).when(metrics).execute(any());

        TriageOrchestratorService orchestrator = new TriageOrchestratorService(List.of(scope, metrics));

        InvestigationState state = orchestrator.investigate("test question");

        // Verify both steps were attempted
        verify(scope).execute(state);
        verify(metrics).execute(state);

        // Assert audit trail has both entries (error + success)
        assertEquals(2, state.getAuditTrail().size(), "Should have error audit and success audit");

        // Verify error audit entry
        InvestigationState.StepAudit firstAudit = state.getAuditTrail().get(0);
        assertTrue(firstAudit.getStepName().contains("[ERROR]"), "Error audit should have [ERROR] suffix");
        assertTrue(firstAudit.getModelResponse().contains("Scope failed"), "Error audit should contain exception message");

        // Verify second audit is from metrics
        assertEquals("MetricsStep", state.getAuditTrail().get(1).getStepName());
    }

    /**
     * Verifies that an empty step list is handled gracefully without exceptions.
     */
    @Test
    void testInvestigateWithEmptySteps() {
        TriageOrchestratorService orchestrator = new TriageOrchestratorService(List.of());

        InvestigationState state = orchestrator.investigate("test question");

        assertEquals("test question", state.getQuestion());
        assertTrue(state.getAuditTrail().isEmpty(), "Audit trail should be empty with no steps");
    }

    /**
     * Verifies that steps with unknown names are appended after known steps.
     */
    @Test
    void handlesUnknownStepNames() {
        InvestigationStep known = mock(InvestigationStep.class, "known");
        InvestigationStep unknown = mock(InvestigationStep.class, "unknown");

        when(known.getName()).thenReturn("MetricsStep");
        when(unknown.getName()).thenReturn("SomeUnknownStep");

        doAnswer(invocation -> {
            InvestigationState s = invocation.getArgument(0);
            s.getAuditTrail().add(new InvestigationState.StepAudit("MetricsStep", "sys", "user", "metrics", null));
            return null;
        }).when(known).execute(any());

        doAnswer(invocation -> {
            InvestigationState s = invocation.getArgument(0);
            s.getAuditTrail().add(new InvestigationState.StepAudit("SomeUnknownStep", "sys", "user", "unknown", null));
            return null;
        }).when(unknown).execute(any());

        TriageOrchestratorService orchestrator = new TriageOrchestratorService(List.of(unknown, known));

        InvestigationState state = orchestrator.investigate("test");

        assertEquals(2, state.getAuditTrail().size());
        // Known step (MetricsStep) should come first
        assertEquals("MetricsStep", state.getAuditTrail().get(0).getStepName());
        // Unknown step should be appended
        assertEquals("SomeUnknownStep", state.getAuditTrail().get(1).getStepName());
    }
}