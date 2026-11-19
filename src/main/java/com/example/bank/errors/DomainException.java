package com.example.bank.errors;

/** Expected business failures, with messages safe to return to the client. */
public abstract class DomainException extends RuntimeException {
    protected DomainException() {
        super();
    }

    abstract int status();
    abstract ApiError error();
}
