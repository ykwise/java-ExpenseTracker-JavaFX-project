# 6. Filter the table in memory, not in SQL

**Status:** Accepted

## Context
The search box and category filter could either re-query the database on every keystroke
(`WHERE description LIKE ?`) or filter the rows already loaded for the selected month.

## Decision
Filter in memory with JavaFX's `FilteredList`, using `ExpenseFilter` as the predicate. The
table only ever holds one month, which is at most a few hundred rows, so filtering is instant
and needs no database round trip or debounce.

## Consequences
- Search covers the selected month only, which matches what the table shows.
- If an all-time search is added later, it should move into SQL (with an index or SQLite FTS),
  because loading every row into memory would not scale.
- `ExpenseFilter` is a plain predicate with no JavaFX dependency, so it is unit-tested directly.
