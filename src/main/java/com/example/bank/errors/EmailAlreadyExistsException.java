package com.example.bank.errors;

public final class EmailAlreadyExistsException extends DomainException {
    @Override int status() { return 409; }
    @Override ApiError error() { return new ApiError("An account with this email already exists.", "EMAIL_ALREADY_EXISTS"); }
}
