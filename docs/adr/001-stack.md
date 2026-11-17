# ADR-001: Backend stack

- Status: Accepted
- Date: 2026-11-16

## Context and Problem Statement

The application needs a Java backend with REST support, plain SQL persistence,
PostgreSQL storage, and repeatable schema migrations. The stack should follow
the product baseline in [`specs/SPEC.md`](../../specs/SPEC.md) and have exact
versions so builds and environments use the same compatible releases.

## Considered Options

- Dropwizard with JDBI 3, PostgreSQL, and Flyway
- A different Java web framework and ORM
- A non-Java backend stack

## Decision

Use Java 21 and the following pinned stack:

| Component | Exact version | Coordinates / release |
|---|---:|---|
| Dropwizard | 5.0.2 | `io.dropwizard:dropwizard-core`; `io.dropwizard:dropwizard-jdbi3` |
| JDBI 3 | 3.53.0 | `org.jdbi:jdbi3-core` and the required JDBI modules |
| PostgreSQL server | 16.16 | PostgreSQL 16 minor release |
| PostgreSQL JDBC driver | 42.7.13 | `org.postgresql:postgresql` |
| Flyway | 13.12.2 | `org.flywaydb:flyway-core` and `org.flywaydb:flyway-database-postgresql` |

Use Dropwizard's JDBI integration with JDBI's plain-SQL APIs. Dropwizard
5.0.2's dependency management pins JDBI to 3.53.0; retain that compatible
version rather than overriding it without validating the integration. Run
Flyway migrations at application startup, before serving requests. Pin both
Flyway artifacts to the same version; Flyway's PostgreSQL support is provided
by its database module. Keep PostgreSQL on major version 16 as specified,
applying compatible 16.x minor updates as maintenance releases become
available and updating the exact pin in this ADR when the deployment baseline
changes.

The pinned releases were verified as stable and available on 2026-11-16:
[Dropwizard 5.0.2](https://github.com/dropwizard/dropwizard/releases/tag/v5.0.2),
[Dropwizard JDBI integration on Maven Central](https://central.sonatype.com/artifact/io.dropwizard/dropwizard-jdbi3/5.0.2),
[Dropwizard's dependency management on Maven Central](https://central.sonatype.com/artifact/io.dropwizard/dropwizard-dependencies/5.0.2),
[JDBI 3.53.0](https://jdbi.org/releases/3.53.0/),
[JDBI 3.53.0 on Maven Central](https://central.sonatype.com/artifact/org.jdbi/jdbi3-core/3.53.0),
[PostgreSQL 16.16 release notes](https://www.postgresql.org/docs/16/release-16-16.html),
[pgJDBC 42.7.13 on Maven Central](https://central.sonatype.com/artifact/org.postgresql/postgresql/42.7.13),
[Flyway 13.12.2](https://github.com/flyway/flyway/releases/tag/flyway-13.12.2),
[Flyway core 13.12.2 on Maven Central](https://central.sonatype.com/artifact/org.flywaydb/flyway-core/13.12.2), and
[Flyway PostgreSQL module 13.12.2 on Maven Central](https://central.sonatype.com/artifact/org.flywaydb/flyway-database-postgresql/13.12.2).

## Consequences

- The backend uses the framework, SQL access pattern, database, and migration
  lifecycle already called for by the product spec.
- Exact version pins make the initial build reproducible and make future
  upgrades explicit.
- Upgrades, including PostgreSQL minor-version updates, require dependency and
  integration validation; Flyway core and its PostgreSQL module must stay in
  lockstep.
- The stack remains a single Java application and database, without adding an
  ORM or a second persistence abstraction.
