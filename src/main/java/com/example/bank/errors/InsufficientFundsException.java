package com.example.bank.errors;

public final class InsufficientFundsException extends DomainException {
    @Override int status() { return 422; }
    @Override ApiError error() { return new ApiError("Insufficient funds.", "INSUFFICIENT_FUNDS"); }
}
