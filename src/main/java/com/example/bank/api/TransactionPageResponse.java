package com.example.bank.api;

import com.example.bank.core.Transaction;

import java.util.List;

public record TransactionPageResponse(List<TransactionResponse> items, int page, int limit, long total) {
    public TransactionPageResponse {
        items = List.copyOf(items);
    }

    public static TransactionPageResponse from(List<Transaction> transactions, int page, int limit, long total) {
        return new TransactionPageResponse(
                transactions.stream().map(TransactionResponse::from).toList(), page, limit, total);
    }
}
