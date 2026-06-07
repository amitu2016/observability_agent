package com.example.downstream.domain.event;

import java.util.UUID;

public record AccountCreatedEvent(String accountId, double initialBalance) {
}
