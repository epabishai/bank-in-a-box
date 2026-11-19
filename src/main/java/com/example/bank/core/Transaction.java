package com.example.bank.core;

import java.time.Instant;
import java.util.UUID;

public record Transaction(UUID id, UUID accountId, TransactionType type, Money amount,
                          String note, Instant createdAt) {
}
