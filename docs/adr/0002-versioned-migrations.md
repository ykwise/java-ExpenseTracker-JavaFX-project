# 2. Versioned schema migrations in `PRAGMA user_version`

**Status:** Accepted

## Context
The original app created its tables with `CREATE TABLE IF NOT EXISTS` on every start-up.
That cannot change an existing schema: adding the Budgets table, indexes, a Settings table
or a new constraint would silently not happen for existing users, or would need ad-hoc checks.

## Decision
`SchemaMigrator` holds an ordered list of migrations. SQLite's built-in `PRAGMA user_version`
records how many have run. On start-up, only the missing ones run, each **inside a transaction
together with its version bump**, so a failure rolls back completely. Shipped migrations are
never edited or reordered; changes are always new migrations.

A library such as Flyway or Liquibase was considered. It is the right choice for a server
application with many migrations and environments, but here it would add a dependency and
configuration for five small migrations.

## Consequences
- Databases from the original app (version 0) upgrade in place; tests cover this, and it was
  run against a real database with existing data.
- v1 uses `IF NOT EXISTS` because version-0 databases already have those tables.
- Down-migrations are not supported. A backup of the database file is the rollback plan.
