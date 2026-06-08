package com.example.triageagent.controller;

import com.example.triageagent.dto.InvestigationJobResponse;
import com.example.triageagent.dto.TriageRequest;
import com.example.triageagent.kafka.InvestigationKafkaProducer;
import com.example.triageagent.model.InvestigationJob;
import com.example.triageagent.repository.InvestigationJobRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/triage")
public class TriageController {

    private final InvestigationJobRepository jobRepository;
    private final InvestigationKafkaProducer producer;
    private final ObjectMapper objectMapper;

    public TriageController(InvestigationJobRepository jobRepository,
                            InvestigationKafkaProducer producer,
                            ObjectMapper objectMapper) {
        this.jobRepository = jobRepository;
        this.producer = producer;
        this.objectMapper = objectMapper;
    }

    /**
     * Initiates an async investigation by saving a job to the DB and sending to Kafka.
     * Returns immediately with 202 Accepted and job metadata for polling.
     */
    @PostMapping("/investigate")
    public ResponseEntity<InvestigationJobResponse> investigate(@RequestBody TriageRequest request) {
        // 1. Create and persist InvestigationJob
        InvestigationJob job = new InvestigationJob();
        job.setQuestion(request.getQuestion());
        job.setStatus(InvestigationJob.Status.PENDING);
        jobRepository.save(job);

        // 2. Send to Kafka for async processing
        producer.sendInvestigationRequest(job);

        // 3. Return 202 Accepted with job metadata
        InvestigationJobResponse response = new InvestigationJobResponse(
                job.getId().toString(),
                job.getStatus().name(),
                job.getCreatedAt().toString()
        );
        return ResponseEntity.status(202).body(response);
    }

    /**
     * Polls the status of an investigation job.
     * The 'report' field is only populated when status == COMPLETED.
     */
    @GetMapping("/investigation/{id}")
    public ResponseEntity<InvestigationJobResponse> getInvestigation(@PathVariable String id) {
        UUID uuid;
        try {
            uuid = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid investigation ID format");
        }

        InvestigationJob job = jobRepository.findById(uuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Investigation not found"));

        InvestigationJobResponse response = new InvestigationJobResponse(
                job.getId().toString(),
                job.getQuestion(),
                job.getStatus().name(),
                job.getCreatedAt().toString(),
                job.getCompletedAt() != null ? job.getCompletedAt().toString() : null,
                extractReport(job)
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Parses the report JSON stored in the job entity.
     * Returns null if no report is available yet.
     */
    private Map<String, Object> extractReport(InvestigationJob job) {
        if (job.getReportJson() == null || job.getReportJson().isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(job.getReportJson(), new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            return Map.of("error", "Failed to parse report", "raw", job.getReportJson());
        }
    }
}