package com.example.triageagent.kafka;

import com.example.triageagent.model.InvestigationJob;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class InvestigationKafkaProducer {
    private static final Logger log = LoggerFactory.getLogger(InvestigationKafkaProducer.class);
    private static final String TOPIC = "triage.requests";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public InvestigationKafkaProducer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public CompletableFuture<SendResult<String, String>> sendInvestigationRequest(InvestigationJob job) {
        try {
            InvestigationRequestEvent event = new InvestigationRequestEvent(
                job.getId().toString(),
                job.getQuestion(),
                job.getCreatedAt().toString()
            );
            String payload = objectMapper.writeValueAsString(event);
            
            log.info("Sending investigation request to Kafka topic '{}': investigationId={}", 
                TOPIC, job.getId());
            
            return kafkaTemplate.send(TOPIC, job.getId().toString(), payload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize investigation request", e);
        }
    }

    public record InvestigationRequestEvent(String investigationId, String question, String createdAt) {}
}