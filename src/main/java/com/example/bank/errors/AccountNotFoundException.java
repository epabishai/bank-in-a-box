package com.example.bank.errors;

public final class AccountNotFoundException extends DomainException {
    @Override int status() { return 404; }
    @Override ApiError error() { return new ApiError("Account not found.", "ACCOUNT_NOT_FOUND"); }
}
