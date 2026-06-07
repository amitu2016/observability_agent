package com.example.downstream.domain.command;

public record CreateAccountCommand(String accountId, double initialBalance) {
}
