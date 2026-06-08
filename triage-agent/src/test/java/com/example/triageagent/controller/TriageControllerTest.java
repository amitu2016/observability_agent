package com.example.triageagent.controller;

import com.example.triageagent.dto.InvestigationJobResponse;
import com.example.triageagent.kafka.InvestigationKafkaProducer;
import com.example.triageagent.model.InvestigationJob;
import com.example.triageagent.repository.InvestigationJobRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TriageController.class)
@ActiveProfiles("test")
class TriageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private InvestigationJobRepository jobRepository;

    @MockBean
    private InvestigationKafkaProducer producer;

    @Test
    void controllerBeansAreLoaded() {
        assertNotNull(mockMvc, "MockMvc should be loaded");
        assertNotNull(jobRepository, "JobRepository mock should be loaded");
        assertNotNull(producer, "KafkaProducer mock should be loaded");
    }

    @Test
    void investigate_returns202WithJobMetadata() throws Exception {
        // Arrange: mock repository save and Kafka producer
        UUID jobId = UUID.randomUUID();
        Instant createdAt = Instant.now();

        when(jobRepository.save(any(InvestigationJob.class))).thenAnswer(invocation -> {
            InvestigationJob job = invocation.getArgument(0);
            // Simulate JPA @GeneratedValue UUID generation
            java.lang.reflect.Field idField = InvestigationJob.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(job, jobId);
            return job;
        });
        when(producer.sendInvestigationRequest(any(InvestigationJob.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        String requestBody = """
                { "question": "Why is the service slow?" }
                """;

        // Act & Assert
        mockMvc.perform(post("/api/triage/investigate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.investigationId").value(jobId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.createdAt").exists());

        verify(jobRepository, times(1)).save(any(InvestigationJob.class));
        verify(producer, times(1)).sendInvestigationRequest(any(InvestigationJob.class));
    }

    @Test
    void investigate_savesJobWithCorrectFields() throws Exception {
        // Arrange: always return same job with ID set
        when(jobRepository.save(any(InvestigationJob.class))).thenAnswer(invocation -> {
            InvestigationJob job = invocation.getArgument(0);
            java.lang.reflect.Field idField = InvestigationJob.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(job, UUID.randomUUID());
            return job;
        });
        when(producer.sendInvestigationRequest(any())).thenReturn(CompletableFuture.completedFuture(null));

        String requestBody = """
                { "question": "What caused the outage?" }
                """;

        // Act
        mockMvc.perform(post("/api/triage/investigate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isAccepted());

        // Assert: verify the saved job has correct question and status
        verify(jobRepository, times(1)).save(argThat(job ->
                "What caused the outage?".equals(job.getQuestion()) &&
                job.getStatus() == InvestigationJob.Status.PENDING
        ));
    }

    @Test
    void getInvestigation_returnsJobDetails() throws Exception {
        // Arrange
        UUID jobId = UUID.randomUUID();
        Instant createdAt = Instant.now();
        Instant completedAt = Instant.now();
        String reportJson = "{\"question\":\"Why is the service slow?\",\"service\":\"payment-api\"}";

        InvestigationJob job = new InvestigationJob();
        job.setId(jobId);
        job.setQuestion("Why is the service slow?");
        job.setStatus(InvestigationJob.Status.COMPLETED);
        job.setCreatedAt(createdAt);
        job.setCompletedAt(completedAt);
        job.setReportJson(reportJson);

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

        // Act & Assert
        mockMvc.perform(get("/api/triage/investigation/{id}", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.investigationId").value(jobId.toString()))
                .andExpect(jsonPath("$.question").value("Why is the service slow?"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.completedAt").exists())
                .andExpect(jsonPath("$.report.question").value("Why is the service slow?"))
                .andExpect(jsonPath("$.report.service").value("payment-api"));
    }

    @Test
    void getInvestigation_returnsNullReportWhenNotCompleted() throws Exception {
        // Arrange: job is still PENDING, no report JSON
        UUID jobId = UUID.randomUUID();
        Instant createdAt = Instant.now();

        InvestigationJob job = new InvestigationJob();
        job.setId(jobId);
        job.setQuestion("Why is the service slow?");
        job.setStatus(InvestigationJob.Status.PENDING);
        job.setCreatedAt(createdAt);
        // No reportJson set

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

        // Act & Assert
        mockMvc.perform(get("/api/triage/investigation/{id}", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.investigationId").value(jobId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.report").doesNotExist())
                .andExpect(jsonPath("$.completedAt").doesNotExist());
    }

    @Test
    void getInvestigation_returns404WhenNotFound() throws Exception {
        // Arrange
        UUID jobId = UUID.randomUUID();
        when(jobRepository.findById(jobId)).thenReturn(Optional.empty());

        // Act & Assert
        mockMvc.perform(get("/api/triage/investigation/{id}", jobId))
                .andExpect(status().isNotFound());
    }

    @Test
    void getInvestigation_returns400ForInvalidUUID() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/triage/investigation/{id}", "not-a-valid-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getInvestigation_handlesMalformedReportJson() throws Exception {
        // Arrange
        UUID jobId = UUID.randomUUID();
        Instant createdAt = Instant.now();
        Instant completedAt = Instant.now();

        InvestigationJob job = new InvestigationJob();
        job.setId(jobId);
        job.setQuestion("Why is the service slow?");
        job.setStatus(InvestigationJob.Status.COMPLETED);
        job.setCreatedAt(createdAt);
        job.setCompletedAt(completedAt);
        job.setReportJson("this is not valid json {{{"); // Malformed JSON

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

        // Act & Assert: should return report with error field
        mockMvc.perform(get("/api/triage/investigation/{id}", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.report.error").value("Failed to parse report"))
                .andExpect(jsonPath("$.report.raw").value("this is not valid json {{{"));
    }
}