package com.example.caller.transfer;

import io.opentelemetry.api.trace.Span;
import java.net.URI;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

@RestController
@RequestMapping("/transfers")
public class TransferController {

    private static final Logger log = LoggerFactory.getLogger(TransferController.class);

    private final TransferRepository transferRepository;
    private final RestClient downstreamClient;

    public TransferController(TransferRepository transferRepository, RestClient.Builder restClientBuilder) {
        this.transferRepository = transferRepository;
        this.downstreamClient = restClientBuilder.baseUrl("http://downstream-service:8080").build();
    }

    @PostMapping
    public ResponseEntity<TransferResponse> initiateTransfer(@RequestBody TransferRequest request) {
        log.info("Initiating transfer from {} to {} amount {}", request.fromAccount(), request.toAccount(), request.amount());

        TransferEntity transfer = new TransferEntity(request.fromAccount(), request.toAccount(), request.amount());
        transfer.setStatus(TransferEntity.TransferStatus.PENDING);
        transfer = transferRepository.save(transfer);

        try {
            downstreamClient.post()
                    .uri("/accounts/{id}/transfer", request.fromAccount())
                    .body(new DownstreamTransferRequest(request.fromAccount(), request.toAccount(), request.amount(), transfer.getId()))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.error("Failed to call downstream-service for transfer {}", transfer.getId(), e);
            transfer.setStatus(TransferEntity.TransferStatus.FAILED);
            transferRepository.save(transfer);
            return ResponseEntity.status(502).body(new TransferResponse(transfer.getId(), transfer.getStatus().name(), "Downstream call failed"));
        }

        String traceId = Span.current().getSpanContext().getTraceId();
        return ResponseEntity.accepted()
                .header("X-Trace-Id", traceId)
                .body(new TransferResponse(transfer.getId(), transfer.getStatus().name(), "Transfer initiated"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransferResponse> getTransfer(@PathVariable UUID id) {
        return transferRepository.findById(id)
                .map(t -> ResponseEntity.ok(new TransferResponse(t.getId(), t.getStatus().name(), null)))
                .orElse(ResponseEntity.notFound().build());
    }

    public record TransferRequest(String fromAccount, String toAccount, double amount) {}

    public record TransferResponse(UUID transferId, String status, String message) {}

    public record DownstreamTransferRequest(String fromAccount, String toAccount, double amount, UUID transferId) {}
}
