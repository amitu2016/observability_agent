package com.example.caller.event;

import java.util.UUID;

public record TransferCompletedEvent(UUID transferId, String fromAccount, String toAccount, double amount) {
}
