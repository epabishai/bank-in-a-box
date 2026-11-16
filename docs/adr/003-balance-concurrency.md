# ADR-003: Concurrency control for balance updates

- Status: Accepted
- Date: 2026-11-16

## Context and Problem Statement

Concurrent withdrawals must never reduce an account below zero, and each
successful balance change must be committed atomically with its transaction
record. The implementation must provide a clear, testable guard against
overdrafts under concurrent requests, as required by
[`specs/SPEC.md`](../../specs/SPEC.md).

## Considered Options

- Conditional `UPDATE` whose predicate includes the balance constraint
- `SELECT ... FOR UPDATE`, then validate and update the locked row

## Decision

Prefer a conditional update for single-account balance changes. A withdrawal
uses one statement equivalent to:

```sql
UPDATE accounts
SET balance_cents = balance_cents - :amount
WHERE id = :account_id
  AND balance_cents >= :amount
```

Treat exactly one affected row as success; zero affected rows means the debit
did not apply. If the API needs to distinguish an unknown account from
insufficient funds, check account existence without treating that check as the
overdraft guard. Deposits use an atomic increment update. In every case, update
the balance and insert its corresponding transaction row in the same database
transaction, rolling back both if either operation fails.

The predicate is evaluated as part of the database write, so concurrent
withdrawals serialize on the row and a later update cannot debit a balance that
no longer satisfies the predicate. Retain `SELECT ... FOR UPDATE` for a future
operation whose decision depends on reading and validating multiple values
before writing.

## Consequences

- The database enforces the overdraft guard atomically without a separate
  application-level read-then-write window.
- A single conditional update is straightforward to test with parallel
  withdrawal requests; the integration test must assert that the final balance
  is nonnegative and matches the committed transactions.
- The update count alone does not distinguish a missing account from a rejected
  debit, so the service must map those outcomes to the API's error contract.
- Multi-row operations may need explicit row locking and consistent lock
  ordering to avoid deadlocks; this decision does not remove that requirement.
