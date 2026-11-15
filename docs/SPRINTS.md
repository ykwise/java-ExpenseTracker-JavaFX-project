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
- The redesigned UI was reviewed on screen after the sprint's code was finished. Structure, colours,
  summary strip and budget panel rendered as intended; four layout problems were found and fixed:
  bordered and drifting month arrows, table text not vertically centred, column headers not lined up
  with cell text, and lines and a hover band on empty table rows.

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

## Sprint 3: Fix what the review found

**Goal:** fix the four bugs found when reviewing Sprint 2, and start tracking work on a real board.
**Pull request:** `feature/sprint-3-fixes`. **Board:** GitHub Project "Expense Tracker"
(Backlog, Sprint, Doing, Review, Done), with one issue per backlog item and a milestone per sprint.

**Done:** BUG-01, BUG-02, BUG-03, BUG-04.

- BUG-01, BUG-02: CSV amounts are plain decimals (`12500000.00`, not `1.25E7`), and text that would
  run as a spreadsheet formula is neutralised (OWASP's CSV injection guidance).
- BUG-03: the database lives in the OS's per-user application data folder. A file left in the
  project folder by earlier versions is copied there once, the original kept as a backup.
- BUG-04: a start-up failure shows what went wrong and that no data was changed, then exits cleanly.

**Review**
- Each bug has tests that fail on the old behaviour; 125 tests pass.
- Still to check on screen: the one-time "Your data has moved" message, and the Add expense dialog.

**Retrospective**
- *Went well:* a deliberate review of finished work found real bugs, including one (BUG-03) that
  would have looked like data loss to a user.
- *Went well:* making `DataLocation` take its inputs (OS name, environment, home folder) as
  parameters meant every platform could be tested on one machine.
- *To improve:* the board was created after Sprints 1 and 2 had finished, so their cards were added
  as already done. From Sprint 4 on, cards move through the columns as the work happens.

---

## Next sprint (proposed)

**Goal:** make the app installable and close the remaining verification gaps.

| Item | MoSCoW |
|---|---|
| Check the Add expense dialog and the data-moved message on screen | Must |
| Test AI suggestions against the real API | Should |
| US-18: installable `.dmg` / `.msi` built by CI | Should |
| US-19: back up and restore | Should |
| Add a licence (R9) | Should |
