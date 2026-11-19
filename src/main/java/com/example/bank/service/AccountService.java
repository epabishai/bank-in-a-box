package com.example.bank.service;

import com.example.bank.api.CreateAccountRequest;
import com.example.bank.core.Account;
import com.example.bank.core.Money;
import com.example.bank.core.Transaction;
import com.example.bank.core.TransactionType;
import com.example.bank.db.AccountDao;
import com.example.bank.db.TransactionDao;
import com.example.bank.errors.AccountNotFoundException;
import com.example.bank.errors.EmailAlreadyExistsException;
import com.example.bank.errors.InsufficientFundsException;
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

    public Transaction deposit(UUID accountId, BigDecimal amount, String note) {
        return transact(accountId, amount, note, TransactionType.DEPOSIT);
    }

    public Transaction withdraw(UUID accountId, BigDecimal amount, String note) {
        return transact(accountId, amount, note, TransactionType.WITHDRAWAL);
    }

    private Transaction transact(UUID accountId, BigDecimal amount, String note, TransactionType type) {
        long amountCents = transactionAmountCents(amount);
        UUID transactionId = UUID.randomUUID();
        return jdbi.inTransaction(handle -> {
            AccountDao accounts = handle.attach(AccountDao.class);
            int updated = type == TransactionType.DEPOSIT
                    ? accounts.credit(accountId, amountCents)
                    : accounts.debit(accountId, amountCents);
            if (updated == 0) {
                if (accounts.findById(accountId).isEmpty()) {
                    throw new AccountNotFoundException();
                }
                throw new InsufficientFundsException();
            }
            TransactionDao transactions = handle.attach(TransactionDao.class);
            transactions.insert(transactionId, accountId, type, amountCents, note);
            return transactions.findById(transactionId).orElseThrow();
        });
    }

    private static long transactionAmountCents(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount is required");
        }
        long cents = Money.fromBigDecimal(amount).cents();
        if (cents <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        return cents;
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
