# Changelog

Changes on top of the original project ([ykwise/java-ExpenseTracker-JavaFX-project](https://github.com/ykwise/java-ExpenseTracker-JavaFX-project)).

## 1.2 – Month view, search, categories and a redesigned UI

### Added
- Month navigation: previous / next month and "Back to this month". The table, chart, summary and budgets all follow the selected month.
- Summary strip: spent this month, change compared with last month, and budget health.
- Search (description, category or amount) and a category filter, with a running total of what is shown.
- Manage categories: add, rename and delete. Categories in use can't be deleted; deleting one with a budget removes both in one transaction.
- Currency setting (default KES), with consistent `KES 1,250.00` formatting regardless of the computer's locale.
- Keyboard shortcuts: ⌘N add, ⌘F search, ⌘← / ⌘→ change month, Enter edit, ⌫ delete. Double-click a row to edit; right-click for Edit / Delete.
- Saving an expense dated in another month switches the view to that month.
- Architecture decision records in `docs/adr/`.
- Product vision, product backlog (user stories, acceptance criteria, MoSCoW, non-functional requirements), sprint reviews and retrospectives, a risk register and a use case diagram in `docs/`.

### Changed
- New visual design: one stylesheet (`app.css`) with a calm ledger palette, colour used only for meaning (within budget, near the limit, over).
- Clearer empty states and error messages that say what to do next.
- `MainViewController` split into `ExpenseTable`, `SummaryStrip`, `BudgetPanel`, `BudgetDialog` and `SettingsDialog`.
- Repositories take the month as a parameter instead of reading the clock.

### Database
- Migration v4: `Settings` table.
- Migration v5: case-insensitive unique index on category names.

## 1.1 – Budgets, AI suggestions and a hardened data layer

### Added
- Monthly budgets per category, a budget panel with progress bars, and alerts at 80% and when exceeded.
- AI category suggestions (Claude) with retries, exponential backoff with jitter, a circuit breaker, a cache and an offline keyword fallback.
- Versioned schema migrations, enforced foreign keys, and indexes for month queries.
- Unit and integration tests, and CI on Ubuntu and macOS.

### Fixed
- Crash on launch on Apple Silicon Macs (JavaFX 17.0.6 → 21.0.6).
- CSV export no longer replaces commas in descriptions; fields are quoted properly.
- Expenses with an unknown category were saved and then disappeared from the list.
- Negative amounts were accepted; database errors were printed and ignored.

### Removed
- JFreeChart and the Swing chart embedded in JavaFX, replaced by the native `PieChart`.
