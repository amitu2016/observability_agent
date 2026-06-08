package com.example.triageagent.kafka;

import com.example.triageagent.mapper.InvestigationMapper;
import com.example.triageagent.model.InvestigationJob;
import com.example.triageagent.repository.InvestigationJobRepository;
import com.example.triageagent.service.TriageOrchestratorService;
import com.example.triageagent.dto.TriageReport;
import com.example.triageagent.investigation.InvestigationState;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Component
public class InvestigationKafkaConsumer {
    private static final Logger log = LoggerFactory.getLogger(InvestigationKafkaConsumer.class);

    private final InvestigationJobRepository jobRepository;
    private final TriageOrchestratorService orchestratorService;
    private final InvestigationMapper investigationMapper;
    private final ObjectMapper objectMapper;

    public InvestigationKafkaConsumer(
            InvestigationJobRepository jobRepository,
            TriageOrchestratorService orchestratorService,
            InvestigationMapper investigationMapper,
            ObjectMapper objectMapper) {
        this.jobRepository = jobRepository;
        this.orchestratorService = orchestratorService;
        this.investigationMapper = investigationMapper;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "triage.requests", groupId = "triage-agent")
    @Transactional
    public void consume(String message) {
        try {
            // 1. Parse JSON into InvestigationRequestEvent (reuse producer's record class)
            InvestigationKafkaProducer.InvestigationRequestEvent event = objectMapper.readValue(
                    message, InvestigationKafkaProducer.InvestigationRequestEvent.class);
            UUID investigationId = UUID.fromString(event.investigationId());

            // 2. Load job from DB
            InvestigationJob job = jobRepository.findById(investigationId)
                    .orElseThrow(() -> new RuntimeException("Job not found: " + investigationId));

            // 3. Update status to RUNNING
            job.setStatus(InvestigationJob.Status.RUNNING);
            jobRepository.save(job);

            // 4. Run investigation
            log.info("Running investigation for job {}", investigationId);
            InvestigationState state = orchestratorService.investigate(job.getQuestion());

            // 5. Serialize report to JSON
            TriageReport report = investigationMapper.toReport(state);
            String reportJson = objectMapper.writeValueAsString(report);

            // 6. Save result
            job.setReportJson(reportJson);
            job.setStatus(InvestigationJob.Status.COMPLETED);
            job.setCompletedAt(Instant.now());
            jobRepository.save(job);

            log.info("Investigation {} completed successfully", investigationId);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse message: {}", message, e);
            // Cannot extract job ID from malformed message, log and skip
        } catch (Exception e) {
            log.error("Investigation failed for message: {}", message, e);
            // Try to parse investigationId and mark job as FAILED if possible
            try {
                InvestigationKafkaProducer.InvestigationRequestEvent event = objectMapper.readValue(
                        message, InvestigationKafkaProducer.InvestigationRequestEvent.class);
                UUID investigationId = UUID.fromString(event.investigationId());
                jobRepository.findById(investigationId).ifPresent(job -> {
                    job.setStatus(InvestigationJob.Status.FAILED);
                    job.setReportJson("{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}");
                    job.setCompletedAt(Instant.now());
                    jobRepository.save(job);
                });
            } catch (Exception ex) {
                log.error("Could not mark job as FAILED", ex);
            }
        }
    }
}