# 1. Keep a local SQLite file as the only data store

**Status:** Accepted

## Context
The app is a single-user desktop tool. The original version already stored data in a local
SQLite file. Options considered: keep SQLite, move to an embedded H2 database, or use a
server database (PostgreSQL) to enable sync between devices.

## Decision
Keep SQLite. It needs no installation or running server, the whole dataset is one file the
user can back up, and it supports what the app needs: transactions, foreign keys, unique and
check constraints, and indexes.

## Consequences
- No multi-device sync. That would need a backend service, which is a separate project.
- Patterns aimed at multi-user servers (connection pooling, read replicas, row locking,
  isolation-level tuning) are deliberately not used; SQLite runs transactions serializably
  and there is one writer.
- Foreign keys must be switched on for every connection (`PRAGMA foreign_keys = ON`);
  SQLite ignores them otherwise. `Database.connect()` does this in one place.
