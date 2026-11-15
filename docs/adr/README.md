# Architecture decision records

Short records of significant decisions: the context, what was decided, and what it costs.
A decision is never edited after the fact; if it changes, a new record supersedes it.

| # | Decision | Status |
|---|---|---|
| [0001](0001-local-sqlite-database.md) | Keep a local SQLite file as the only data store | Accepted |
| [0002](0002-versioned-migrations.md) | Versioned schema migrations in `PRAGMA user_version` | Accepted |
| [0003](0003-budgets-warn-not-block.md) | Budget alerts warn but never block saving | Accepted |
| [0004](0004-optional-llm-with-fallback.md) | AI categorisation is optional, with an offline fallback | Accepted |
| [0005](0005-no-json-library.md) | No JSON library for the LLM client | Accepted |
| [0006](0006-filter-in-memory.md) | Filter the table in memory, not in SQL | Accepted |
| [0007](0007-money-as-double.md) | Keep money as `double` for now | Accepted, to revisit |
