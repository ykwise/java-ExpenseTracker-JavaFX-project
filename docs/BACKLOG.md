# Product backlog

The requirements catalogue for Expense Tracker: user stories grouped into themes, non-functional
requirements, and what is deliberately out of scope. Priorities use **MoSCoW**
(Must, Should, Could, Won't have this time).

**Status:** ✅ Done · 🔄 In progress · ⬜ To do. "Sprint" refers to [SPRINTS.md](SPRINTS.md);
"Original" means it existed in the project this was built on.

## Functional requirements

### Theme A: Recording expenses

| ID | User story | MoSCoW | Sprint | Status |
|---|---|---|---|---|
| US-01 | As a user, I want to record an expense with a date, category, amount and description, so that I have a record of my spending. | Must | Original, hardened in 1 | ✅ |
| US-02 | As a user, I want to edit an expense, so that I can correct mistakes. | Must | Original | ✅ |
| US-03 | As a user, I want to delete an expense after confirming, so that I can remove wrong entries without deleting by accident. | Must | Original, improved in 2 | ✅ |
| US-04 | As a user, I want entry to be quick (today's date filled in, a keyboard shortcut, amounts like "1,250" accepted), so that recording an expense takes seconds. | Should | 2 | ✅ |

**Acceptance criteria, US-01**
- Given the form is open, when I save without a date, category or amount, then nothing is saved and I am told which fields are required.
- Given I enter an amount of 0, a negative number or text, when I save, then nothing is saved and I am told the amount must be a number greater than 0.
- Given I save an expense dated in another month, then the main window switches to that month so I can see it.

### Theme B: Budgets

| ID | User story | MoSCoW | Sprint | Status |
|---|---|---|---|---|
| US-05 | As a user, I want to set a monthly budget for a category, so that I can plan my spending. | Must | 1 | ✅ |
| US-06 | As a user, I want to be warned when an expense takes a category past its budget, so that I notice overspending immediately. | Must | 1 | ✅ |
| US-07 | As a user, I want an early warning at 80% of a budget, so that I can adjust before I overspend. | Should | 1 | ✅ |
| US-08 | As a user, I want to see spent vs budget for each category, so that I know where I stand. | Could | 1 | ✅ |

**Acceptance criteria, US-06 and US-07**
- Given Food has a budget of 1,000 and 700 is spent this month, when I add 100, then I see a "budget almost used up" warning showing 80%.
- Given the same budget, when spending passes 1,000, then I see a "budget exceeded" warning. At exactly 1,000 it is still a near-limit warning, not exceeded.
- The expense is always saved. The warning informs; it never blocks ([ADR 0003](adr/0003-budgets-warn-not-block.md)).
- Expenses dated in past months don't trigger warnings.

### Theme C: Understanding my spending

| ID | User story | MoSCoW | Sprint | Status |
|---|---|---|---|---|
| US-09 | As a user, I want to see a chart of the month's spending by category, so that I can see where most of it goes. | Must | Original, rebuilt in 1 | ✅ |
| US-10 | As a user, I want to move between months, so that I can look back at earlier spending. | Must | 2 | ✅ |
| US-11 | As a user, I want a summary of what I spent, how it compares with last month and how my budgets are doing, so that I get the picture without reading every row. | Should | 2 | ✅ |

**Acceptance criteria, US-10**
- When I go to a previous month, then the table, chart, summary and budgets all show that month.
- Month boundaries are exact: an expense on 31 October is in October, 1 November is in November, including across December and January.

### Theme D: Organising

| ID | User story | MoSCoW | Sprint | Status |
|---|---|---|---|---|
| US-12 | As a user, I want to search expenses by description, category or amount, so that I can find a specific one. | Should | 2 | ✅ |
| US-13 | As a user, I want to filter the list by category, so that I can review one kind of spending. | Should | 2 | ✅ |
| US-14 | As a user, I want to add, rename and delete categories, so that they fit how I actually spend. | Should | 2 | ✅ |
| US-15 | As a user, I want amounts shown in my currency, so that the numbers read naturally. | Could | 2 | ✅ |

**Acceptance criteria, US-14**
- Names are unique regardless of case: "food" is rejected if "Food" exists.
- Renaming a category updates every expense and budget that uses it.
- A category that has expenses can't be deleted, and I'm told how many expenses use it.
- Deleting an unused category also removes its budget, and either both happen or neither does.

### Theme E: Smart entry

| ID | User story | MoSCoW | Sprint | Status |
|---|---|---|---|---|
| US-16 | As a user, I want the category suggested from what I type in the description, so that I don't have to pick it every time. | Could | 1 | ✅ |

**Acceptance criteria, US-16**
- A suggestion never replaces a category I have chosen myself.
- With no API key, or when the AI service fails, the offline keyword rules still suggest a category.
- An AI answer that isn't one of my categories is ignored.
- Typing never freezes the window.

### Theme F: Getting data out

| ID | User story | MoSCoW | Sprint | Status |
|---|---|---|---|---|
| US-17 | As a user, I want to export all expenses to CSV, so that I can use them in a spreadsheet. | Must | Original, fixed in 1 | ✅, see BUG-01 and BUG-02 |

## Open items

| ID | Item | MoSCoW | Status |
|---|---|---|---|
| BUG-01 | CSV export writes large amounts in scientific notation (12,500,000 becomes `1.25E7`). | Must | ⬜ |
| BUG-02 | CSV export doesn't guard against formula injection (a description starting with `=` runs as a formula in Excel). | Must | ⬜ |
| BUG-03 | The database file is created in whatever folder the app is started from, so starting it elsewhere shows an empty app. | Must | ⬜ |
| BUG-04 | If the database can't be opened at start-up, the app exits without a message. | Should | ⬜ |
| US-18 | As a user, I want to install the app without Java or Maven (macOS `.dmg`, Windows `.msi`). | Should | ⬜ |
| US-19 | As a user, I want to back up and restore my data. | Should | ⬜ |
| US-20 | As a user, I want totals that never show rounding errors (store money as integer cents, [ADR 0007](adr/0007-money-as-double.md)). | Could | ⬜ |
| US-21 | As a user, I want different budgets in different months. | Could | ⬜ |
| US-22 | As a user, I want recurring expenses (rent, subscriptions) added automatically. | Could | ⬜ |
| US-23 | As a user, I want to import expenses from a CSV file. | Could | ⬜ |

## Won't have (this release)

| Item | Why not |
|---|---|
| Sync between devices, shared household accounts | Needs a server, accounts and conflict handling: a different product ([ADR 0001](adr/0001-local-sqlite-database.md)). |
| Bank or M-Pesa statement import | Statement formats vary and change; a CSV import (US-23) covers most of the need first. |
| Converting between currencies | Needs live exchange rates; the app records spending in one currency. |
| Scanning receipts | Large effort (OCR) for a small gain over typing four fields. |

## Non-functional requirements

Each one says how it is verified, so it is a requirement rather than a wish.

| ID | Requirement | How it is verified | Status |
|---|---|---|---|
| NFR-01 | **Data integrity:** the database rejects invalid data itself (unknown categories, duplicate names, non-positive budgets). | Integration tests insert bad rows with raw SQL and expect rejection. | ✅ |
| NFR-02 | **Safe upgrades:** existing users' data survives every schema change, and a failed upgrade changes nothing. | Migration tests: upgrade from the original app's schema, run twice, roll back on failure. Also run against a real database. | ✅ |
| NFR-03 | **Performance:** month queries use an index and the budget panel needs one query regardless of how many budgets exist. | A test reads `EXPLAIN QUERY PLAN` and fails on a full table scan. | ✅ |
| NFR-04 | **Responsiveness:** no network call runs on the UI thread. | `AsyncSuggester` tests; suggestions run on a worker thread. | ✅ |
| NFR-05 | **Resilience:** an AI outage, rate limit or bad answer never stops the user recording an expense. | Tests with a fake server for retries, backoff, circuit breaker, timeouts and fallback. | ✅ |
| NFR-06 | **Privacy:** no expense data leaves the computer unless the user sets an API key. | Factory test: without a key, only the offline suggester exists. | ✅ |
| NFR-07 | **Portability:** builds and passes tests on macOS and Linux. | CI runs on Ubuntu and macOS for every push. | ✅ |
| NFR-08 | **Maintainability:** classes stay small, loosely coupled and cohesive; changes are covered by tests. | CKJM measured before and after each release ([METRICS.md](METRICS.md)); 112 automated tests. | ✅ |
| NFR-09 | **Consistency:** money looks the same on every computer, whatever its language settings. | Test formats under a German locale. | ✅ |
| NFR-10 | **Usability:** common actions have shortcuts; empty screens and errors say what to do next. | Implemented; not yet checked with real users or UI tests. | 🔄 |
