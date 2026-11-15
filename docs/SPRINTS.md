# Sprints

The work was run as short Scrum-style iterations, each delivered as one pull request.
This is a solo project, so one person played every role: **Product Owner** (choosing and
prioritising stories in [BACKLOG.md](BACKLOG.md)), **Scrum Master** (keeping each sprint to its goal)
and **Development Team**. Every sprint was far shorter than the two-week limit, because the
scope was deliberately small.

Story IDs refer to the backlog. The reviews and retrospectives are written as they happened,
including what went wrong.

---

## Sprint 0: Make it run

**Goal:** get the original project building and running on an Apple Silicon Mac.

**Done**
- Cloned the project and confirmed it compiles with Maven and JDK 17.
- Fixed a crash on launch (`NSException`, exit code 134): JavaFX 17.0.6 doesn't support
  current macOS on Apple Silicon. Upgraded to JavaFX 21.0.6, the current long-term-support release.

**Review:** the app opened with its sample data. The pie chart was empty, which turned out to be
correct: it shows the current month, and the sample data was from 2025.

**Retrospective**
- *Went well:* reading the native error code instead of the Java stack trace pointed straight at
  a platform incompatibility, not a code bug.
- *To improve:* the first build failed on a dropped network connection during a large download.
  Retrying with Maven's retry options fixed it; worth knowing before blaming the code.

---

## Sprint 1: Budgets and a trustworthy data layer

**Goal:** users can set monthly budgets and get warned before overspending, on a data layer that
can be tested and upgraded safely. **Pull request:** `feature/category-budgets`.

**Stories done:** US-05, US-06, US-07, US-08, US-16, plus fixes to US-01, US-09 and US-17.

**Also delivered**
- Static `DatabaseManager` replaced by injectable repositories; versioned migrations in transactions;
  foreign keys enforced; index-backed month queries.
- Native JavaFX chart replacing the Swing chart; `.gitignore`; CI on Ubuntu and macOS.
- 82 automated tests; CKJM metrics before and after; architecture diagrams.

**Review**
- All five stories met their acceptance criteria in automated tests.
- Running the migration against a copy of the real database upgraded it from version 0 to 3 with
  every expense and budget intact.
- Not demonstrated: the AI suggestions against the real Claude API (only against a fake server).

**Retrospective**
- *Went well:* CKJM changed the design instead of just describing it. The first combined repository
  scored LCOM 149, which led to splitting it into three classes.
- *Went well:* writing the budget rule (`BudgetService`) as a pure function first made it trivial to test.
- *To improve:* a commit was made before the change it depended on, so one point in the history
  doesn't compile on its own. Commit in dependency order.
- *To improve:* shell commands with `#` comments failed in zsh. Keep copy-paste commands comment-free.

---

## Sprint 2: Month view, search, categories and a redesigned UI

**Goal:** make the app pleasant for everyday use: look back at past months, find expenses quickly,
and shape categories to real spending. **Pull request:** `feature/ux-improvements`.

**Stories done:** US-04, US-10, US-11, US-12, US-13, US-14, US-15.

**Also delivered**
- New stylesheet and layout, summary strip, keyboard shortcuts, clearer empty states and errors.
- Migrations v4 (settings) and v5 (case-insensitive unique category names).
- Decision records, changelog, 112 automated tests.

**Review**
- All stories met their acceptance criteria in automated tests, including month boundaries across
  the year end and the all-or-nothing category delete.
- Not demonstrated yet: the redesigned UI on screen. It compiles and its stylesheet parses without
  errors, but it hasn't been looked at by a person.

**Retrospective**
- *Went well:* moving the month into repository parameters made both the feature and the tests
  simpler; the injected clock was no longer needed.
- *Didn't go well:* the first version of the redesign made `MainViewController` much worse
  (CBO 84, LCOM 576). Measuring after the change caught it, and extracting `ExpenseTable` and two
  dialog classes brought it down to 58 and 181, but it is still above Sprint 1.
- *To improve:* look at the UI on screen during the sprint, not after it. Work done without seeing
  the result needs a review step before it counts as done.
- *To improve:* a review of the finished sprint found four bugs (BUG-01 to BUG-04). They go to the
  top of the next sprint.

---

## Next sprint (proposed)

**Goal:** fix the open bugs and make the app installable.

| Item | MoSCoW |
|---|---|
| BUG-01: CSV amounts in scientific notation | Must |
| BUG-02: CSV formula injection | Must |
| BUG-03: database location depends on start folder | Must |
| BUG-04: silent start-up failure | Should |
| Check the redesigned UI on screen and adjust | Must |
| Test AI suggestions against the real API | Should |
| US-18: installable `.dmg` / `.msi` | Should |
