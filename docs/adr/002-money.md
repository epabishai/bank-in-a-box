# ADR-002: Money representation

- Status: Accepted
- Date: 2026-11-16

## Context and Problem Statement

Account balances and transaction amounts must be exact. The API accepts
decimal amounts with no more than two fractional digits, while calculations and
database updates must avoid binary floating-point rounding. This decision
formalizes the money rules in [`specs/SPEC.md`](../../specs/SPEC.md).

## Considered Options

- Store integer minor units (`long` cents)
- Use `BigDecimal` throughout the domain and persistence layers
- Use `double` or `float`

## Decision

Represent money in Java and SQL as signed 64-bit integer cents (`long` in Java,
`BIGINT` in PostgreSQL). At the API boundary, parse JSON decimal amounts as
`BigDecimal`, reject values with a decimal scale greater than two (including
trailing zero decimal places), and convert to cents exactly before calling
domain or persistence code. Reject values that cannot be represented as a
`long`; never round or truncate. Serialize API amounts as decimal currency
values with at most two fractional digits.

## Consequences

- Domain arithmetic and database comparisons are exact and use the smallest
  currency unit.
- The API retains a natural decimal representation without allowing
  floating-point precision loss into business logic.
- API conversion must validate scale, positivity where required, and integer
  range before any write.
- The `long` range bounds the maximum representable amount; overflow must be
  rejected rather than wrapped.
- `BigDecimal` is confined to parsing and formatting at the API edge; it is
  not the domain money type.
