package com.example.bank.db;

import com.example.bank.api.CreateAccountRequest;
import com.example.bank.core.Account;
import com.example.bank.core.Money;
import com.example.bank.core.Transaction;
import com.example.bank.core.TransactionType;
import com.example.bank.errors.EmailAlreadyExistsException;
import com.example.bank.service.AccountService;
import org.flywaydb.core.Flyway;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.UnableToExecuteStatementException;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DaoTest {
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.16-alpine");
    private static Jdbi jdbi;

    @BeforeAll
    static void startDatabase() {
        POSTGRES.start();
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load().migrate();
        jdbi = Jdbi.create(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .installPlugin(new SqlObjectPlugin());
    }

    @AfterAll
    static void stopDatabase() {
        POSTGRES.stop();
    }

    @BeforeEach
    void clearDatabase() {
        jdbi.useHandle(handle -> handle.execute("TRUNCATE transactions, accounts"));
    }

    @Test
    void accountMappingAndUpdatesRespectConstraints() {
        UUID id = UUID.randomUUID();
        AccountDao accounts = jdbi.onDemand(AccountDao.class);
        assertEquals(1, accounts.insert(id, "O'Brien", "person@example.com", 0));
        Account account = accounts.findById(id).orElseThrow();
        assertEquals(id, account.id());
        assertEquals("O'Brien", account.name());
        assertEquals("person@example.com", account.email());
        assertEquals(new Money(0), account.balance());
        assertNotNull(account.createdAt());
        assertTrue(accounts.findById(UUID.randomUUID()).isEmpty());
        assertEquals(1, accounts.credit(id, 500));
        assertEquals(0, accounts.debit(id, 501));
        assertEquals(0, accounts.debit(id, -1));
        assertEquals(0, accounts.credit(id, 0));
        assertEquals(0, accounts.credit(UUID.randomUUID(), 1));
        assertEquals(1, accounts.debit(id, 125));
        assertEquals(new Money(375), accounts.findById(id).orElseThrow().balance());
        assertThrows(UnableToExecuteStatementException.class,
                () -> accounts.insert(UUID.randomUUID(), "Duplicate", "PERSON@example.com", 0));
        assertThrows(UnableToExecuteStatementException.class, () -> accounts.credit(id, Long.MAX_VALUE));
        assertEquals(new Money(375), accounts.findById(id).orElseThrow().balance());
    }

    @Test
    void transactionsMapAllTypesAndPaginateNewestFirst() {
        UUID accountId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        AccountDao accounts = jdbi.onDemand(AccountDao.class);
        accounts.insert(accountId, "A", "a@example.com", 0);
        accounts.insert(otherId, "B", "b@example.com", 0);
        TransactionDao transactions = jdbi.onDemand(TransactionDao.class);
        for (TransactionType type : TransactionType.values()) {
            UUID id = UUID.randomUUID();
            String note = type == TransactionType.DEPOSIT ? null : "note ' for " + type;
            assertEquals(1, transactions.insert(id, accountId, type, 123, note));
            Transaction saved = transactions.findById(id).orElseThrow();
            assertEquals(id, saved.id());
            assertEquals(accountId, saved.accountId());
            assertEquals(type, saved.type());
            assertEquals(new Money(123), saved.amount());
            assertEquals(note, saved.note());
            assertNotNull(saved.createdAt());
        }
        transactions.insert(UUID.randomUUID(), otherId, TransactionType.DEPOSIT, 1, null);
        assertTrue(transactions.findById(UUID.randomUUID()).isEmpty());
        assertEquals(4, transactions.countByAccount(accountId));
        List<Transaction> all = transactions.listByAccount(accountId, 10, 0);
        assertEquals(4, all.size());
        for (int i = 1; i < all.size(); i++) {
            assertFalse(all.get(i - 1).createdAt().isBefore(all.get(i).createdAt()));
        }
        assertEquals(all.subList(1, 3), transactions.listByAccount(accountId, 2, 1));
        assertTrue(transactions.listByAccount(accountId, 2, 4).isEmpty());
        assertThrows(UnableToExecuteStatementException.class,
                () -> transactions.insert(UUID.randomUUID(), accountId, TransactionType.DEPOSIT, 0, null));
        assertThrows(UnableToExecuteStatementException.class,
                () -> transactions.insert(UUID.randomUUID(), UUID.randomUUID(), TransactionType.DEPOSIT, 1, null));
    }

    @Test
    void transactionPageQueryUsesV1AccountHistoryIndex() {
        UUID accountId = UUID.randomUUID();
        jdbi.onDemand(AccountDao.class).insert(accountId, "A", "index@example.com", 0);
        jdbi.useHandle(handle -> handle.createUpdate("""
                INSERT INTO transactions (id, account_id, type, amount_cents, created_at)
                SELECT md5('history-' || sequence_number)::uuid, :accountId, 'DEPOSIT', 1,
                       now() + sequence_number * interval '1 second'
                FROM generate_series(1, 5000) AS history(sequence_number)
                """).bind("accountId", accountId).execute());
        jdbi.useHandle(handle -> handle.execute("ANALYZE transactions"));

        List<String> plan = jdbi.withHandle(handle -> handle.createQuery("""
                EXPLAIN (ANALYZE, BUFFERS)
                SELECT id, account_id, type, amount_cents, note, created_at
                FROM transactions WHERE account_id = :accountId
                ORDER BY created_at DESC, id DESC LIMIT 20 OFFSET 0
                """).bind("accountId", accountId).mapTo(String.class).list());

        assertTrue(String.join("\n", plan).contains("idx_transactions_account_created_at"),
                () -> "Expected transaction page query to use the V1 account history index: " + plan);
        assertEquals(20, jdbi.onDemand(TransactionDao.class).listByAccount(accountId, 20, 0).size());
    }

    @Test
    void failedLedgerInsertRollsBackBalanceUpdate() {
        UUID id = UUID.randomUUID();
        jdbi.onDemand(AccountDao.class).insert(id, "A", "a@example.com", 0);
        assertThrows(UnableToExecuteStatementException.class, () -> jdbi.useTransaction(handle -> {
            handle.attach(AccountDao.class).credit(id, 100);
            handle.attach(TransactionDao.class).insert(UUID.randomUUID(), id, TransactionType.DEPOSIT, 0, null);
        }));
        assertEquals(new Money(0), jdbi.onDemand(AccountDao.class).findById(id).orElseThrow().balance());
        assertEquals(0, jdbi.onDemand(TransactionDao.class).countByAccount(id));
    }

    @Test
    void accountServiceCreatesInitialDepositAndLedgerAtomically() {
        AccountService service = new AccountService(jdbi);
        Account account = service.create(
                new CreateAccountRequest("Ada Lovelace", "ada@example.com", new BigDecimal("125.50")));
        List<Transaction> history = jdbi.onDemand(TransactionDao.class).listByAccount(account.id(), 10, 0);

        assertEquals(new Money(12_550), account.balance());
        assertEquals(1, history.size());
        assertEquals(TransactionType.DEPOSIT, history.getFirst().type());
        assertEquals(new Money(12_550), history.getFirst().amount());

        Account zeroDeposit = service.create(
                new CreateAccountRequest("Grace Hopper", "grace@example.com", BigDecimal.ZERO));
        assertEquals(new Money(0), zeroDeposit.balance());
        assertEquals(0, jdbi.onDemand(TransactionDao.class).countByAccount(zeroDeposit.id()));
    }

    @Test
    void concurrentAccountCreatesMapCaseInsensitiveEmailConflict() throws Exception {
        AccountService service = new AccountService(jdbi);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicInteger nextEmail = new AtomicInteger();
        List<String> emails = List.of("RACE@example.com", "race@example.com");
        try {
            java.util.concurrent.Callable<Boolean> create = () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Start timed out");
                }
                try {
                    service.create(new CreateAccountRequest("Race", emails.get(nextEmail.getAndIncrement()), null));
                    return true;
                } catch (EmailAlreadyExistsException exception) {
                    return false;
                }
            };
            Future<Boolean> first = executor.submit(create);
            Future<Boolean> second = executor.submit(create);
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            assertEquals(1, (first.get(20, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(20, TimeUnit.SECONDS) ? 1 : 0));
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void concurrentDebitsCannotOverdrawAndLedgerMatchesBalance() throws Exception {
        UUID id = UUID.randomUUID();
        jdbi.useTransaction(handle -> {
            handle.attach(AccountDao.class).insert(id, "A", "a@example.com", 100);
            handle.attach(TransactionDao.class).insert(UUID.randomUUID(), id, TransactionType.DEPOSIT, 100, null);
        });
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Integer> debit = () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Start timed out");
                }
                return jdbi.inTransaction(handle -> {
                    int updated = handle.attach(AccountDao.class).debit(id, 80);
                    if (updated == 1) {
                        handle.attach(TransactionDao.class)
                                .insert(UUID.randomUUID(), id, TransactionType.WITHDRAWAL, 80, null);
                    }
                    return updated;
                });
            };
            Future<Integer> first = executor.submit(debit);
            Future<Integer> second = executor.submit(debit);
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            assertEquals(1, first.get(20, TimeUnit.SECONDS) + second.get(20, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
        Account account = jdbi.onDemand(AccountDao.class).findById(id).orElseThrow();
        List<Transaction> history = jdbi.onDemand(TransactionDao.class).listByAccount(id, 10, 0);
        assertEquals(2, history.size());
        long ledger = history.stream().mapToLong(transaction -> switch (transaction.type()) {
            case DEPOSIT, TRANSFER_IN -> transaction.amount().cents();
            case WITHDRAWAL, TRANSFER_OUT -> -transaction.amount().cents();
        }).sum();
        assertEquals(20, account.balance().cents());
        assertEquals(ledger, account.balance().cents());
    }
}
