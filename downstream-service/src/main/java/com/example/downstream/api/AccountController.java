package com.example.downstream.api;

import com.example.downstream.domain.command.CreateAccountCommand;
import com.example.downstream.domain.command.TransferFundsCommand;
import com.example.downstream.query.AccountEntity;
import com.example.downstream.query.AccountRepository;
import java.util.List;
import java.util.UUID;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private static final Logger log = org.slf4j.LoggerFactory.getLogger(AccountController.class);

    private final CommandGateway commandGateway;
    private final AccountRepository accountRepository;

    public AccountController(CommandGateway commandGateway, AccountRepository accountRepository) {
        this.commandGateway = commandGateway;
        this.accountRepository = accountRepository;
    }

    @GetMapping
    public List<AccountEntity> getAllAccounts() {
        return accountRepository.findAll();
    }

    @PostMapping
    public ResponseEntity<CreateAccountResponse> createAccount(@RequestBody CreateAccountRequest request) {
        log.info("Creating account {}", request.accountId());
        commandGateway.sendAndWait(new CreateAccountCommand(request.accountId(), request.initialBalance()));
        return ResponseEntity.accepted().body(new CreateAccountResponse(request.accountId(), "Account created"));
    }

    @PostMapping("/{id}/transfer")
    public ResponseEntity<TransferResponse> transfer(
            @PathVariable String id,
            @RequestBody TransferRequest request) {
        log.info("Transferring {} from {} to {}", request.amount(), id, request.toAccount());
        commandGateway.sendAndWait(new TransferFundsCommand(id, request.toAccount(), request.amount(), request.transferId()));
        return ResponseEntity.accepted().body(new TransferResponse(request.transferId(), "Transfer initiated"));
    }

    public record CreateAccountRequest(String accountId, double initialBalance) {}
    public record CreateAccountResponse(String accountId, String message) {}
    public record TransferRequest(String toAccount, double amount, UUID transferId) {}
    public record TransferResponse(UUID transferId, String message) {}
}
