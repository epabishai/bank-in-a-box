# Threat model and money-path review

Review date: 2026-11-24

## Scope and assumptions

This review covers the API, PostgreSQL access and schema, Java money path, error handling, CORS, logging, and the repository's deployment artifacts. It is a source review, not a penetration test. The repository currently has no `Dockerfile` or Compose file, so container controls are assessed as requirements rather than verified implementation.

**This demo has no authentication or authorization.** Anyone who can reach the API can create accounts, read account details and transaction history, and submit deposits or withdrawals. UUIDs are identifiers, not access controls. Do not expose this demo to untrusted networks or use it for real funds or customer data. A production system needs authenticated users or service identities, account-level authorization on every operation, TLS, managed secrets, rate limiting, and auditable actor identity.

## STRIDE by surface

| Surface | STRIDE | Threat and current posture |
|---|---|---|
| API | Spoofing | No authentication is implemented; callers cannot be distinguished. Account IDs do not prove ownership. |
| API | Tampering | JSON amounts are parsed as `BigDecimal`, and DAO SQL uses named bound parameters. The API has no authorization boundary to prevent callers changing any account they know. |
| API | Repudiation | Ledger entries record transaction type, amount, and time, but there is no authenticated actor or request audit trail. |
| API | Information disclosure | Account email and transaction data are readable without authentication. Error responses suppress exception details, while unhandled exceptions are logged server-side. |
| API | Denial of service | There is no authentication or rate limiting. Request text fields have no declared length bounds; a reachable service can be burdened with large values. |
| Database | Spoofing | The application connects with configured DB credentials. The committed defaults in `src/main/resources/config.yml` are development-only, but production configuration does not require overriding them. Network binding and production DB identity controls cannot be verified without deployment configuration. |
| Database | Tampering | Static SQL statements with bound parameters prevent user input from becoming SQL syntax. The schema enforces nonnegative balances, positive transaction amounts, foreign keys, and case-insensitive email uniqueness. A database credential compromise still permits direct data changes. |
| Database | Repudiation | Transaction rows provide a basic ledger, but do not capture authenticated actors or administrative changes made directly in the database. |
| Database | Information disclosure | Accounts contain names and email addresses, and transactions can contain notes. Encryption at rest, backup handling, and database network restrictions are deployment responsibilities not established here. |
| Database | Denial of service | Unbounded text and transaction history can consume database resources. No quotas or retention limits are defined. |
| Container | Spoofing, Tampering, Repudiation, Information disclosure, Denial of service, Elevation of privilege | No Dockerfile or Compose file is present in this checkout, so image provenance, non-root execution, port exposure, secret injection, filesystem/network restrictions, resource limits, and container logging cannot be verified. For deployment, use a minimal pinned image, non-root user, least-privilege network access, external secrets, resource limits, and a controlled image build/provenance process. |

## Focused review

### SQL construction

DAO statements are fixed SQL with named JDBI bindings (`src/main/java/com/example/bank/db/AccountDao.java:14-34`, `TransactionDao.java`). No dynamic SQL construction from request data was found. The email uniqueness constraint is enforced by a lower-case index (`src/main/resources/db/migration/V1__init.sql:9`).

### Input validation

The API validates required fields, email format, positive transaction amounts, decimal scale, and long-cent range; it also validates UUIDs and positive pagination parameters (`CreateAccountRequest.java:9-12`, `TransactionRequest.java:7`, `AccountResource.java`, `Money.java`). However, account name, email, and transaction note do not declare maximum lengths, and there is no application-level request-size limit in the checked-in configuration.

**Finding F-02 — Low severity: unbounded text input.**

**Location:** `src/main/java/com/example/bank/api/TransactionRequest.java:7`, `CreateAccountRequest.java:9-12`; persisted as PostgreSQL `text` in `src/main/resources/db/migration/V1__init.sql:3-4,16`.

**Reproduction:** Submit an otherwise valid account-creation request with a 64 KiB nonblank `name` and a unique valid email, or a valid deposit request with a 64 KiB `note`. The DTOs have no field-size constraint and the schema stores these values as `text`; repeat requests to grow database use and the data returned by account/history reads.

**Suggested fix:** Set explicit maximum lengths for name, email, and note; configure a maximum HTTP request body size; reject over-limit inputs with the standard validation response. Add boundary tests.

**Finding F-03 — Medium severity: production can silently use the development database credentials.**

**Location:** `src/main/resources/config.yml:10-16`, `src/main/java/com/example/bank/BankConfiguration.java:10-16`.

**Reproduction:** Start the service with `ENV=production` and a production `DB_URL`, but omit `DB_USER` and `DB_PASSWORD`. The environment substitution still selects the committed development defaults; the `environment` setting does not validate or reject them.

**Suggested fix:** Fail startup in production unless explicit database credentials are supplied. Inject them from a secret manager, use a least-privilege database role, and keep PostgreSQL on a private network.

### Error leakage and logging of PII

The error mappers return generic client responses; unhandled failures use `INTERNAL_ERROR` rather than exposing stack traces or exception messages (`src/main/java/com/example/bank/errors/UnhandledExceptionMapper.java:11-14`, `ErrorResponses.java`). No request-body or PII logging was found. Unhandled exceptions are logged with their full throwable on the server (`UnhandledExceptionMapper.java:13`); restrict log access and retention, and review database-driver diagnostics for sensitive values before production.

### CORS

No CORS filter or CORS configuration is registered in the current application. This does not grant cross-origin access, but differs from the development-only CORS behavior described in `specs/SPEC.md:114,126`. The Vite development server proxies `/api`, so this does not block the documented proxy workflow. If direct browser cross-origin access is introduced, allow only the required development origin and disable it in production.

### Money overflow

`Money.fromBigDecimal` checks decimal scale and long-cent range; `Money.add` and `subtract` use exact arithmetic (`src/main/java/com/example/bank/core/Money.java:8-48`). Withdrawals use a conditional update and the balance update plus ledger insert occur in one database transaction (`AccountDao.java:30-34`, `AccountService.java:74-91`). Deposits use an atomic increment, but that SQL expression has no upper-bound guard.

**Finding F-01 — Medium severity: deposit can overflow the database balance.**

**Location:** `src/main/java/com/example/bank/db/AccountDao.java:24-28`; surfaced through `AccountResource.java:61-73` and `UnhandledExceptionMapper.java:12-14`.

**Reproduction:** Create an account with the valid maximum long-cent balance, `92233720368547758.07`, then deposit `0.01`. Parsing succeeds, but `balance_cents + :amount` exceeds PostgreSQL `BIGINT`; the operation fails and is returned as `500 INTERNAL_ERROR` instead of a client validation error. The transaction rolls back, so this is not a partial ledger write.

**Suggested fix:** Reject deposits whose amount exceeds `Long.MAX_VALUE - currentBalance`, preferably with an atomic SQL upper-bound predicate to avoid a read/write race, and map that condition to `VALIDATION_ERROR`. Add a boundary test proving both the response and unchanged balance/ledger.

### Race conditions

Withdrawals use `UPDATE ... WHERE balance_cents >= :amount`, and the service inserts the corresponding transaction in the same JDBI transaction. The database update is the overdraft guard; a separate account-existence check is only used to classify a failed update. The integration test `src/test/java/com/example/bank/db/DaoTest.java` exercises concurrent debits and verifies the ledger matches the final balance. Deposits also use an atomic update and transaction boundary; the upper-bound overflow case above still needs explicit handling.

## Follow-up and disclosure

Findings are recorded here with severity, location, reproduction, and suggested fix. No follow-up issue IDs are listed because this environment has no issue-creation operation. Per `SECURITY.md`, confirmed vulnerabilities should be submitted through the repository's private vulnerability-reporting channel rather than disclosed in a public issue or pull request.
