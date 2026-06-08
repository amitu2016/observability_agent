package com.example.triageagent.kafka;

import com.example.triageagent.model.InvestigationJob;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for InvestigationKafkaProducer.
 * Verifies that sendInvestigationRequest() correctly serializes and sends messages to Kafka.
 */
@ExtendWith(MockitoExtension.class)
class InvestigationKafkaProducerTest {

    private static final String TOPIC = "triage.requests";

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private ObjectMapper objectMapper;
    private InvestigationKafkaProducer producer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules(); // Register Java 8 time modules
        producer = new InvestigationKafkaProducer(kafkaTemplate, objectMapper);
    }

    @Test
    void sendInvestigationRequest_sendsToCorrectTopic() {
        // Arrange
        InvestigationJob job = createTestJob();
        when(kafkaTemplate.send(eq(TOPIC), any(), any())).thenReturn(CompletableFuture.completedFuture(null));

        // Act
        producer.sendInvestigationRequest(job);

        // Assert
        verify(kafkaTemplate).send(eq(TOPIC), any(), any());
    }

    @Test
    void sendInvestigationRequest_usesJobIdAsKey() {
        // Arrange
        UUID jobId = UUID.randomUUID();
        InvestigationJob job = createTestJob(jobId);
        when(kafkaTemplate.send(eq(TOPIC), any(), any())).thenReturn(CompletableFuture.completedFuture(null));

        // Act
        producer.sendInvestigationRequest(job);

        // Assert
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(TOPIC), keyCaptor.capture(), any());
        assertThat(keyCaptor.getValue()).isEqualTo(jobId.toString());
    }

    @Test
    void sendInvestigationRequest_serializesMessageCorrectly() throws Exception {
        // Arrange
        UUID jobId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2024-01-15T10:30:00Z");
        InvestigationJob job = createTestJob(jobId);
        job.setCreatedAt(createdAt);
        when(kafkaTemplate.send(eq(TOPIC), any(), any())).thenReturn(CompletableFuture.completedFuture(null));

        // Act
        producer.sendInvestigationRequest(job);

        // Assert
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(TOPIC), eq(jobId.toString()), payloadCaptor.capture());

        String payload = payloadCaptor.getValue();
        assertThat(payload).contains("\"investigationId\":\"" + jobId + "\"");
        assertThat(payload).contains("\"question\":\"Why is the service slow?\"");
        assertThat(payload).contains("\"createdAt\":\"2024-01-15T10:30:00Z\"");
    }

    @Test
    void sendInvestigationRequest_returnsCompletableFuture() {
        // Arrange
        InvestigationJob job = createTestJob();
        CompletableFuture<SendResult<String, String>> expectedFuture = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(eq(TOPIC), any(), any())).thenReturn(expectedFuture);

        // Act
        CompletableFuture<SendResult<String, String>> result = producer.sendInvestigationRequest(job);

        // Assert
        assertThat(result).isSameAs(expectedFuture);
    }

    @Test
    void sendInvestigationRequest_throwsWhenSerializationFails() throws Exception {
        // Arrange: ObjectMapper that fails to serialize
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        doThrow(new com.fasterxml.jackson.core.JsonProcessingException("Serialization failed") {}).when(failingMapper).writeValueAsString(any());
        InvestigationKafkaProducer failingProducer = new InvestigationKafkaProducer(kafkaTemplate, failingMapper);

        InvestigationJob job = createTestJob();

        // Act & Assert
        assertThatThrownBy(() -> failingProducer.sendInvestigationRequest(job))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to serialize investigation request");
    }

    @Test
    void sendInvestigationRequest_neverSendsToOtherTopics() {
        // Arrange
        InvestigationJob job = createTestJob();
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(null));

        // Act
        producer.sendInvestigationRequest(job);

        // Assert: Verify no sends to other topics
        verify(kafkaTemplate).send(eq(TOPIC), any(), any());
        verify(kafkaTemplate, never()).send(eq("other.topic"), any(), any());
        verify(kafkaTemplate, never()).send(eq("some-topic"), any(), any());
    }

    private InvestigationJob createTestJob() {
        return createTestJob(UUID.randomUUID());
    }

    private InvestigationJob createTestJob(UUID id) {
        InvestigationJob job = new InvestigationJob();
        job.setId(id);
        job.setQuestion("Why is the service slow?");
        job.setStatus(InvestigationJob.Status.PENDING);
        job.setCreatedAt(Instant.now());
        return job;
    }
}