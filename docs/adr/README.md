# Architecture Decision Records

This directory records the architectural decisions for Bank in a Box using the
[MADR format](https://adr.github.io/madr/). The product and technical baseline
is defined in [`specs/SPEC.md`](../../specs/SPEC.md).

| ADR | Decision |
|---|---|
| [ADR-001](001-stack.md) | Dropwizard, JDBI 3, PostgreSQL, and Flyway |
| [ADR-002](002-money.md) | Money as `long` cents, with `BigDecimal` at the API edge |
| [ADR-003](003-balance-concurrency.md) | Conditional updates for concurrent balance changes |
| [ADR-004](004-spa-serving.md) | Serve the SPA from Dropwizard with an `index.html` fallback |
| [ADR-005](005-agent-team.md) | Issue-to-agent-to-PR team workflow |
