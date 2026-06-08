package com.example.triageagent.kafka;

import com.example.triageagent.dto.TriageReport;
import com.example.triageagent.investigation.InvestigationState;
import com.example.triageagent.mapper.InvestigationMapper;
import com.example.triageagent.model.InvestigationJob;
import com.example.triageagent.repository.InvestigationJobRepository;
import com.example.triageagent.service.TriageOrchestratorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for InvestigationKafkaConsumer.
 * Verifies that the consumer correctly processes Kafka messages:
 * - Loads jobs by ID
 * - Calls the orchestrator service
 * - Saves jobs with COMPLETED or FAILED status
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InvestigationKafkaConsumerTest {

    @Mock
    private InvestigationJobRepository jobRepository;

    @Mock
    private TriageOrchestratorService orchestratorService;

    @Mock
    private InvestigationMapper investigationMapper;

    private ObjectMapper objectMapper;
    private InvestigationKafkaConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        consumer = new InvestigationKafkaConsumer(jobRepository, orchestratorService, investigationMapper, objectMapper);
    }

    @Test
    void consume_loadsJobByIdAndCallsOrchestrator() {
        // Arrange
        UUID jobId = UUID.randomUUID();
        String jobQuestion = "Why is the service slow?";
        InvestigationJob job = createTestJob(jobId, InvestigationJob.Status.PENDING);
        job.setQuestion(jobQuestion); // Consumer uses job.getQuestion()
        String message = createMessage(jobId, jobQuestion, Instant.now());

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(orchestratorService.investigate(any())).thenReturn(createTestState("CPU spike"));
        when(investigationMapper.toReport(any())).thenReturn(new TriageReport());

        // Act
        consumer.consume(message);

        // Assert
        verify(jobRepository).findById(jobId);
        verify(orchestratorService).investigate(jobQuestion); // Uses job.getQuestion()
    }

    @Test
    void consume_savesJobWithCompletedStatusAndReport() throws Exception {
        // Arrange
        UUID jobId = UUID.randomUUID();
        String question = "Why is the service slow?";
        InvestigationJob job = createTestJob(jobId, InvestigationJob.Status.PENDING);
        job.setQuestion(question);
        String message = createMessage(jobId, question, Instant.now());

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        
        InvestigationState state = createTestState("Database connections exhausted");
        when(orchestratorService.investigate(question)).thenReturn(state);
        
        TriageReport report = new TriageReport();
        report.setRootCause("Database connections exhausted");
        report.setService("payment-api");
        when(investigationMapper.toReport(state)).thenReturn(report);

        // Act
        consumer.consume(message);

        // Assert: capture final saved job
        ArgumentCaptor<InvestigationJob> jobCaptor = ArgumentCaptor.forClass(InvestigationJob.class);
        verify(jobRepository, times(2)).save(jobCaptor.capture());

        InvestigationJob finalJob = jobCaptor.getAllValues().get(1); // Second save is COMPLETED
        assertThat(finalJob.getStatus()).isEqualTo(InvestigationJob.Status.COMPLETED);
        assertThat(finalJob.getCompletedAt()).isNotNull();
        assertThat(finalJob.getReportJson()).contains("Database connections exhausted");
    }

    @Test
    void consume_savesJobWithCorrectQuestion() {
        // Arrange: verify the job's question is used (not from message)
        UUID jobId = UUID.randomUUID();
        InvestigationJob job = createTestJob(jobId, InvestigationJob.Status.PENDING);
        job.setQuestion("The actual question from DB");
        String message = createMessage(jobId, "Different text in message", Instant.now());

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(orchestratorService.investigate("The actual question from DB")).thenReturn(createTestState("result"));
        when(investigationMapper.toReport(any())).thenReturn(new TriageReport());

        // Act
        consumer.consume(message);

        // Assert: orchestrator was called with the job's question, not the message
        verify(orchestratorService).investigate("The actual question from DB");
    }

    @Test
    void consume_marksJobAsFailedWhenOrchestratorThrowsException() {
        // Arrange
        UUID jobId = UUID.randomUUID();
        InvestigationJob job = createTestJob(jobId, InvestigationJob.Status.PENDING);
        String message = createMessage(jobId, "Why is it slow?", Instant.now());

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(orchestratorService.investigate(any())).thenThrow(new RuntimeException("AI service unavailable"));

        // Act
        consumer.consume(message);

        // Assert: final save should have FAILED status
        ArgumentCaptor<InvestigationJob> jobCaptor = ArgumentCaptor.forClass(InvestigationJob.class);
        verify(jobRepository, times(2)).save(jobCaptor.capture());

        InvestigationJob finalJob = jobCaptor.getAllValues().get(1);
        assertThat(finalJob.getStatus()).isEqualTo(InvestigationJob.Status.FAILED);
        assertThat(finalJob.getCompletedAt()).isNotNull();
        assertThat(finalJob.getReportJson()).contains("AI service unavailable");
    }

    @Test
    void consume_savesErrorReportJsonWhenExceptionOccurs() {
        // Arrange
        UUID jobId = UUID.randomUUID();
        InvestigationJob job = createTestJob(jobId, InvestigationJob.Status.PENDING);
        String message = createMessage(jobId, "Why is it slow?", Instant.now());

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(orchestratorService.investigate(any())).thenThrow(new RuntimeException("Connection timeout"));

        // Act
        consumer.consume(message);

        // Assert
        ArgumentCaptor<InvestigationJob> jobCaptor = ArgumentCaptor.forClass(InvestigationJob.class);
        verify(jobRepository, times(2)).save(jobCaptor.capture());

        InvestigationJob finalJob = jobCaptor.getAllValues().get(1);
        assertThat(finalJob.getReportJson()).isNotNull();
        assertThat(finalJob.getReportJson()).contains("Connection timeout");
    }

    @Test
    void consume_doesNotThrowWhenJobNotFound() {
        // Arrange
        UUID jobId = UUID.randomUUID();
        String message = createMessage(jobId, "Test question", Instant.now());

        when(jobRepository.findById(jobId)).thenReturn(Optional.empty());

        // Act & Assert: should not throw
        consumer.consume(message);

        verify(orchestratorService, never()).investigate(any());
    }

    @Test
    void consume_doesNotCrashOnMalformedJson() {
        // Arrange
        String malformedMessage = "{ this is not valid json";

        // Act & Assert: should not throw
        consumer.consume(malformedMessage);

        verify(orchestratorService, never()).investigate(any());
    }

    @Test
    void consume_callsInvestigateWithJobQuestion() {
        // Arrange
        UUID jobId = UUID.randomUUID();
        String question = "What caused the high latency?";
        InvestigationJob job = createTestJob(jobId, InvestigationJob.Status.PENDING);
        job.setQuestion(question);
        String message = createMessage(jobId, question, Instant.now());

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(orchestratorService.investigate(question)).thenReturn(createTestState("answer"));
        when(investigationMapper.toReport(any())).thenReturn(new TriageReport());

        // Act
        consumer.consume(message);

        // Assert
        verify(orchestratorService).investigate(question);
    }

    @Test
    void consume_setsCompletedAtTimestampOnSuccess() {
        // Arrange
        UUID jobId = UUID.randomUUID();
        InvestigationJob job = createTestJob(jobId, InvestigationJob.Status.PENDING);
        String message = createMessage(jobId, "Test", Instant.now());

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(orchestratorService.investigate(any())).thenReturn(createTestState("result"));
        when(investigationMapper.toReport(any())).thenReturn(new TriageReport());

        // Act
        Instant beforeConsume = Instant.now();
        consumer.consume(message);
        Instant afterConsume = Instant.now();

        // Assert
        ArgumentCaptor<InvestigationJob> jobCaptor = ArgumentCaptor.forClass(InvestigationJob.class);
        verify(jobRepository, times(2)).save(jobCaptor.capture());

        InvestigationJob finalJob = jobCaptor.getAllValues().get(1);
        assertThat(finalJob.getCompletedAt()).isNotNull();
        assertThat(finalJob.getCompletedAt()).isBetween(beforeConsume, afterConsume.plusMillis(1));
    }

    // --- Helper Methods ---

    private String createMessage(UUID investigationId, String question, Instant createdAt) {
        return String.format(
                "{\"investigationId\":\"%s\",\"question\":\"%s\",\"createdAt\":\"%s\"}",
                investigationId, question, createdAt.toString()
        );
    }

    private InvestigationJob createTestJob(UUID id, InvestigationJob.Status status) {
        InvestigationJob job = new InvestigationJob();
        job.setId(id);
        job.setQuestion("Test question");
        job.setStatus(status);
        job.setCreatedAt(Instant.now());
        return job;
    }

    private InvestigationState createTestState(String rootCauseHypothesis) {
        InvestigationState state = new InvestigationState();
        state.setQuestion("Test question");
        state.setRootCauseHypothesis(rootCauseHypothesis);
        state.setConfidence(0.85);
        return state;
    }
}