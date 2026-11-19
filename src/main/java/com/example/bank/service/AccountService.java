package com.example.bank.service;

import com.example.bank.api.CreateAccountRequest;
import com.example.bank.core.Account;
import com.example.bank.core.Money;
import com.example.bank.core.TransactionType;
import com.example.bank.db.AccountDao;
import com.example.bank.db.TransactionDao;
import com.example.bank.errors.AccountNotFoundException;
import com.example.bank.errors.EmailAlreadyExistsException;
import org.jdbi.v3.core.Jdbi;
import org.postgresql.util.PSQLException;

import java.math.BigDecimal;
import java.util.UUID;

public final class AccountService {
    private static final String EMAIL_UNIQUE_INDEX = "idx_accounts_email_lower";
    private final Jdbi jdbi;

    public AccountService(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    public Account create(CreateAccountRequest request) {
        long initialDeposit = initialDepositCents(request.initialDeposit());
        UUID id = UUID.randomUUID();
        try {
            return jdbi.inTransaction(handle -> {
                AccountDao accounts = handle.attach(AccountDao.class);
                accounts.insert(id, request.name(), request.email(), initialDeposit);
                if (initialDeposit > 0) {
                    handle.attach(TransactionDao.class)
                            .insert(UUID.randomUUID(), id, TransactionType.DEPOSIT, initialDeposit, null);
                }
                return accounts.findById(id).orElseThrow(AccountNotFoundException::new);
            });
        } catch (RuntimeException exception) {
            if (isDuplicateEmail(exception)) {
                throw new EmailAlreadyExistsException();
            }
            throw exception;
        }
    }

    public Account get(UUID id) {
        return jdbi.withHandle(handle -> handle.attach(AccountDao.class).findById(id)
                .orElseThrow(AccountNotFoundException::new));
    }

    private static long initialDepositCents(BigDecimal initialDeposit) {
        if (initialDeposit == null) {
            return 0;
        }
        long cents = Money.fromBigDecimal(initialDeposit).cents();
        if (cents < 0) {
            throw new IllegalArgumentException("Initial deposit cannot be negative");
        }
        return cents;
    }

    private static boolean isDuplicateEmail(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof PSQLException postgresException
                    && "23505".equals(postgresException.getSQLState())
                    && postgresException.getServerErrorMessage() != null
                    && EMAIL_UNIQUE_INDEX.equals(postgresException.getServerErrorMessage().getConstraint())) {
                return true;
            }
        }
        return false;
    }
}
