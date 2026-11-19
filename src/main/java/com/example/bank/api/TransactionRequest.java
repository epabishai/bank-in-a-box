package com.example.bank.api;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record TransactionRequest(@NotNull BigDecimal amount, String note) {
}
