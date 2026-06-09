package com.example.downstream.query;

import com.example.downstream.domain.event.AccountCreatedEvent;
import com.example.downstream.domain.event.FundsTransferredEvent;
import org.axonframework.eventhandling.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AccountProjection {

    private static final Logger log = LoggerFactory.getLogger(AccountProjection.class);
    private final AccountRepository repository;

    public AccountProjection(AccountRepository repository) {
        this.repository = repository;
    }

    @EventHandler
    public void on(AccountCreatedEvent event) {
        log.info("Projecting AccountCreatedEvent for {}", event.accountId());
        repository.save(new AccountEntity(event.accountId(), event.initialBalance()));
    }

    @EventHandler
    public void on(FundsTransferredEvent event) {
        log.info("Projecting FundsTransferredEvent from {} to {} amount {}", event.accountId(), event.toAccount(), event.amount());
        
        repository.findById(event.accountId()).ifPresent(account -> {
            account.setBalance(account.getBalance() - event.amount());
            repository.save(account);
        });

        repository.findById(event.toAccount()).ifPresent(account -> {
            account.setBalance(account.getBalance() + event.amount());
            repository.save(account);
        });
    }
}
