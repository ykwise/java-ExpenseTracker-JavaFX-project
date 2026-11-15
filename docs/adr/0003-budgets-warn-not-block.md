# 3. Budget alerts warn but never block saving

**Status:** Accepted

## Context
When an expense pushes a category over its budget, the app could refuse to save it, ask
for confirmation, or save it and inform the user.

## Decision
Save, then warn (at 80% and when exceeded). An expense records money that has already been
spent; refusing it would make the data wrong, and an extra confirmation step would slow down
the most frequent action in the app.

## Consequences
- The check runs *after* saving and reads the month's total from the database. This makes Add
  and Edit both correct without special cases (no "subtract the old amount" logic).
- Alerts only fire for the current month. Past months are history; warning about them adds noise.
- The threshold rules live in `BudgetService`, a pure function, so they are unit-tested
  separately from the UI and reused by the budget panel and summary strip.
