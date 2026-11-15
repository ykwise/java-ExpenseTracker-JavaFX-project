# 7. Keep money as `double` for now

**Status:** Accepted, to revisit

## Context
Amounts are stored as `DECIMAL` (which SQLite treats as a floating-point number) and handled as
`double` in Java. Binary floating point cannot represent most decimal fractions exactly, so
sums can carry tiny errors such as `0.30000000000000004`.

## Decision
Keep `double` for now. Display always rounds to two decimals, budget comparisons use ratios
where these errors are far below any meaningful threshold, and the original data is already
stored this way.

## Consequences
- Fine for a personal tracker; not acceptable for accounting or anything that must reconcile.
- The proper fix, when needed: store integer cents (`INTEGER`), use `BigDecimal` in Java, and
  convert existing rows in a migration (`ROUND(amount * 100)`), covered by migration tests.
