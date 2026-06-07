package com.example.downstream.domain.event;

import java.util.UUID;

public record FundsTransferredEvent(String accountId, String toAccount, double amount, UUID transferId) {
}
