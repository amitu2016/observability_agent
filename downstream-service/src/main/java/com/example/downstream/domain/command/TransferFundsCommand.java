package com.example.downstream.domain.command;

import java.util.UUID;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

public record TransferFundsCommand(@TargetAggregateIdentifier String accountId, String toAccount, double amount, UUID transferId) {
}
