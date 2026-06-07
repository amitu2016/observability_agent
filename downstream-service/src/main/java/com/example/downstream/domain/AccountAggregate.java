package com.example.downstream.domain;

import com.example.downstream.domain.command.CreateAccountCommand;
import com.example.downstream.domain.command.TransferFundsCommand;
import com.example.downstream.domain.event.AccountCreatedEvent;
import com.example.downstream.domain.event.FundsTransferredEvent;
import com.example.downstream.domain.event.TransferCompletedEvent;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.spring.stereotype.Aggregate;

@Aggregate
public class AccountAggregate {

    @AggregateIdentifier
    private String accountId;
    private double balance;

    protected AccountAggregate() {
        // Required no-arg constructor for Axon
    }

    @CommandHandler
    public AccountAggregate(CreateAccountCommand command) {
        AggregateLifecycle.apply(new AccountCreatedEvent(command.accountId(), command.initialBalance()));
    }

    @CommandHandler
    public void handle(TransferFundsCommand command) {
        if (balance < command.amount()) {
            throw new IllegalStateException("Insufficient funds in account " + accountId);
        }
        AggregateLifecycle.apply(new FundsTransferredEvent(
                command.accountId(), command.toAccount(), command.amount(), command.transferId()));
        AggregateLifecycle.apply(new TransferCompletedEvent(
                command.transferId(), command.accountId(), command.toAccount(), command.amount()));
    }

    @EventSourcingHandler
    public void on(AccountCreatedEvent event) {
        this.accountId = event.accountId();
        this.balance = event.initialBalance();
    }

    @EventSourcingHandler
    public void on(FundsTransferredEvent event) {
        this.balance -= event.amount();
    }
}
