package com.example.caller.transfer;

import com.example.caller.event.TransferCompletedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class TransferEventListener {

    private static final Logger log = LoggerFactory.getLogger(TransferEventListener.class);

    private final TransferRepository transferRepository;
    private final ObjectMapper objectMapper;

    public TransferEventListener(TransferRepository transferRepository, ObjectMapper objectMapper) {
        this.transferRepository = transferRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "bank.events", groupId = "caller-service")
    public void onTransferCompleted(String eventJson) {
        try {
            TransferCompletedEvent event = objectMapper.readValue(eventJson, TransferCompletedEvent.class);
            log.info("Received TransferCompletedEvent for transferId={}", event.transferId());

            transferRepository.findById(event.transferId()).ifPresent(transfer -> {
                transfer.setStatus(TransferEntity.TransferStatus.COMPLETED);
                transferRepository.save(transfer);
                log.info("Updated transfer {} to COMPLETED", event.transferId());
            });
        } catch (Exception e) {
            log.error("Failed to process transfer event: {}", eventJson, e);
        }
    }
}
