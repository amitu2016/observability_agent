package com.example.downstream.query;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "accounts")
public class AccountEntity {

    @Id
    private String accountId;
    private double balance;

    public AccountEntity() {}

    public AccountEntity(String accountId, double balance) {
        this.accountId = accountId;
        this.balance = balance;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }
}
