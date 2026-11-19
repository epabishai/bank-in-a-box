package com.example.bank.api;

import com.example.bank.core.Transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(UUID id, UUID accountId, String type, BigDecimal amount,
                                  String note, Instant createdAt) {
    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(transaction.id(), transaction.accountId(), transaction.type().name(),
                transaction.amount().toBigDecimal(), transaction.note(), transaction.createdAt());
    }
}
