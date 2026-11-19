package com.example.bank.api;

import com.example.bank.core.Account;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AccountResponse(UUID id, String name, String email, BigDecimal balance, Instant createdAt) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(account.id(), account.name(), account.email(),
                account.balance().toBigDecimal(), account.createdAt());
    }
}
