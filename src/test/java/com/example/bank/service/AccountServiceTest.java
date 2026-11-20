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
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.HandleCallback;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountServiceTest {
    private final Jdbi jdbi = mock(Jdbi.class);
    private final Handle handle = mock(Handle.class);
    private final AccountDao accounts = mock(AccountDao.class);
    private final TransactionDao transactions = mock(TransactionDao.class);
    private AccountService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new AccountService(jdbi);
        when(handle.attach(AccountDao.class)).thenReturn(accounts);
        when(handle.attach(TransactionDao.class)).thenReturn(transactions);
        doAnswer(invocation -> executeCallback(invocation, handle))
                .when(jdbi).inTransaction(any(HandleCallback.class));
        doAnswer(invocation -> executeCallback(invocation, handle))
                .when(jdbi).withHandle(any(HandleCallback.class));
    }

    @Test
    void createWritesPositiveInitialDepositAndLedgerEntryInTransaction() {
        when(accounts.findById(any(UUID.class))).thenAnswer(invocation ->
                Optional.of(account(invocation.getArgument(0), 1_250)));

        Account created = service.create(
                new CreateAccountRequest("Ada", "ada@example.com", new BigDecimal("12.50")));

        assertEquals(new Money(1_250), created.balance());
        verify(jdbi).inTransaction(any(HandleCallback.class));
        verify(accounts).insert(any(UUID.class), eq("Ada"), eq("ada@example.com"), eq(1_250L));
        verify(transactions).insert(any(UUID.class), eq(created.id()), eq(TransactionType.DEPOSIT),
                eq(1_250L), isNull());
    }

    @Test
    void createWithNoInitialDepositDoesNotWriteLedgerEntry() {
        when(accounts.findById(any(UUID.class))).thenAnswer(invocation ->
                Optional.of(account(invocation.getArgument(0), 0)));

        assertEquals(new Money(0),
                service.create(new CreateAccountRequest("Grace", "grace@example.com", null)).balance());

        verify(transactions, never()).insert(any(UUID.class), any(UUID.class),
                any(TransactionType.class), anyLong(), any());
    }

    @Test
    void createRejectsNegativeOrInexactInitialDeposit() {
        assertThrows(IllegalArgumentException.class, () -> service.create(
                new CreateAccountRequest("Ada", "ada@example.com", new BigDecimal("-0.01"))));
        assertThrows(IllegalArgumentException.class, () -> service.create(
                new CreateAccountRequest("Ada", "ada@example.com", new BigDecimal("0.001"))));
    }

    @Test
    void createMapsOnlyTheCaseInsensitiveEmailUniqueConstraint() {
        PSQLException duplicate = new PSQLException(new ServerErrorMessage(
                "SERROR\u0000C23505\u0000nidx_accounts_email_lower\u0000"));
        when(accounts.insert(any(UUID.class), any(String.class), any(String.class), anyLong()))
                .thenThrow(new IllegalStateException("insert failed", duplicate));

        assertThrows(EmailAlreadyExistsException.class, () -> service.create(
                new CreateAccountRequest("Ada", "ADA@example.com", null)));
    }

    @Test
    void createPreservesUnrelatedDatabaseFailures() {
        IllegalStateException failure = new IllegalStateException("database unavailable");
        when(accounts.insert(any(UUID.class), any(String.class), any(String.class), anyLong()))
                .thenThrow(failure);

        assertSame(failure, assertThrows(IllegalStateException.class, () -> service.create(
                new CreateAccountRequest("Ada", "ada@example.com", null))));
    }

    @Test
    void getReturnsAccountAndRejectsMissingAccount() {
        UUID accountId = UUID.randomUUID();
        Account account = account(accountId, 5);
        when(accounts.findById(accountId)).thenReturn(Optional.of(account));
        assertEquals(account, service.get(accountId));

        when(accounts.findById(accountId)).thenReturn(Optional.empty());
        assertThrows(AccountNotFoundException.class, () -> service.get(accountId));
    }

    @Test
    void listTransactionsChecksAccountAndReturnsPageMetadata() {
        UUID accountId = UUID.randomUUID();
        when(accounts.findById(accountId)).thenReturn(Optional.of(account(accountId, 10)));
        Transaction transaction = transaction(UUID.randomUUID(), accountId, TransactionType.DEPOSIT, 10, null);
        when(transactions.countByAccount(accountId)).thenReturn(21L);
        when(transactions.listByAccount(accountId, 10, 10)).thenReturn(List.of(transaction));

        var page = service.listTransactions(accountId, 2, 10);

        assertEquals(2, page.page());
        assertEquals(10, page.limit());
        assertEquals(21, page.total());
        assertEquals(1, page.items().size());
        verify(transactions).listByAccount(accountId, 10, 10);

        when(accounts.findById(accountId)).thenReturn(Optional.empty());
        assertThrows(AccountNotFoundException.class, () -> service.listTransactions(accountId, 1, 10));
    }

    @Test
    void depositAndWithdrawUpdateBalanceAndInsertMatchingTransactions() {
        UUID accountId = UUID.randomUUID();
        when(accounts.credit(accountId, 250)).thenReturn(1);
        when(accounts.debit(accountId, 125)).thenReturn(1);
        when(transactions.findById(any(UUID.class))).thenAnswer(invocation -> Optional.of(transaction(
                invocation.getArgument(0), accountId, TransactionType.DEPOSIT, 250, "payday")));

        Transaction deposit = service.deposit(accountId, new BigDecimal("2.50"), "payday");
        assertEquals(TransactionType.DEPOSIT, deposit.type());
        assertEquals(new Money(250), deposit.amount());
        assertEquals("payday", deposit.note());
        verify(accounts).credit(accountId, 250);
        verify(transactions).insert(any(UUID.class), eq(accountId), eq(TransactionType.DEPOSIT),
                eq(250L), eq("payday"));

        when(transactions.findById(any(UUID.class))).thenAnswer(invocation -> Optional.of(transaction(
                invocation.getArgument(0), accountId, TransactionType.WITHDRAWAL, 125, null)));
        Transaction withdrawal = service.withdraw(accountId, new BigDecimal("1.25"), null);
        assertEquals(TransactionType.WITHDRAWAL, withdrawal.type());
        assertEquals(new Money(125), withdrawal.amount());
        verify(accounts).debit(accountId, 125);
        verify(transactions).insert(any(UUID.class), eq(accountId), eq(TransactionType.WITHDRAWAL),
                eq(125L), isNull());
        verify(jdbi, times(2)).inTransaction(any(HandleCallback.class));
    }

    @Test
    void transactionAmountsMustBePositiveAndHaveAtMostTwoDecimalPlaces() {
        for (BigDecimal invalid : new BigDecimal[]{null, BigDecimal.ZERO, new BigDecimal("-1.00"),
                new BigDecimal("1.001")}) {
            assertThrows(IllegalArgumentException.class, () -> service.deposit(UUID.randomUUID(), invalid, null));
        }
        assertThrows(IllegalArgumentException.class,
                () -> service.withdraw(UUID.randomUUID(), BigDecimal.ZERO, null));

        verify(jdbi, never()).inTransaction(any(HandleCallback.class));
    }

    @Test
    void failedDebitDistinguishesInsufficientFundsFromMissingAccount() {
        UUID accountId = UUID.randomUUID();
        when(accounts.debit(accountId, 100)).thenReturn(0);
        when(accounts.findById(accountId)).thenReturn(Optional.of(account(accountId, 50)));
        assertThrows(InsufficientFundsException.class,
                () -> service.withdraw(accountId, new BigDecimal("1.00"), null));
        verify(transactions, never()).insert(any(UUID.class), any(UUID.class),
                any(TransactionType.class), anyLong(), any());

        when(accounts.findById(accountId)).thenReturn(Optional.empty());
        assertThrows(AccountNotFoundException.class,
                () -> service.withdraw(accountId, new BigDecimal("1.00"), null));
    }

    @Test
    void failedDepositForMissingAccountDoesNotInsertTransaction() {
        UUID accountId = UUID.randomUUID();
        when(accounts.credit(accountId, 100)).thenReturn(0);
        when(accounts.findById(accountId)).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class,
                () -> service.deposit(accountId, new BigDecimal("1.00"), null));

        verify(transactions, never()).insert(any(UUID.class), any(UUID.class),
                any(TransactionType.class), anyLong(), any());
    }

    private static Account account(UUID id, long cents) {
        return new Account(id, "Test account", "test@example.com", new Money(cents), Instant.EPOCH);
    }

    private static Transaction transaction(UUID id, UUID accountId, TransactionType type, long cents, String note) {
        return new Transaction(id, accountId, type, new Money(cents), note, Instant.EPOCH);
    }

    @SuppressWarnings("unchecked")
    private static Object executeCallback(InvocationOnMock invocation, Handle handle) throws Exception {
        HandleCallback<Object, Exception> callback = invocation.getArgument(0);
        return callback.withHandle(handle);
    }
}
