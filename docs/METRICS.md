# Quality metrics: before and after

Measured with [CKJM](https://github.com/dspinellis/ckjm) (Chidamber & Kemerer metrics for Java)
on the compiled `src/main` classes. **Before** is the original project at commit `d7080cb`;
**after** is this branch. Raw output: [`metrics/ckjm-before.txt`](metrics/ckjm-before.txt),
[`metrics/ckjm-after.txt`](metrics/ckjm-after.txt). Nested/anonymous classes are omitted from the tables.

| Metric | Meaning | Better when |
|---|---|---|
| **WMC** | Weighted methods per class (here: number of methods) | lower: less to understand per class |
| **DIT** | Depth of inheritance tree | lower |
| **NOC** | Number of direct subclasses | context-dependent |
| **CBO** | Coupling between objects: how many other classes this one uses or is used by | lower: changes ripple less |
| **RFC** | Response for a class: methods that can run in response to a call | lower |
| **LCOM** | Lack of cohesion in methods: pairs of methods sharing no fields, minus pairs that do | lower: the class does one job |
| **Ca** | Afferent coupling: how many classes depend on this one | high is fine for stable abstractions |
| **NPM** | Number of public methods | |

## Summary

| | Before | After |
|---|--:|--:|
| Production classes (top-level) | 5 | 24 |
| Lines of production code | 605 | 1,827 |
| Automated tests | 0 | 82 |
| Average WMC | 6.6 | **5.6** |
| Average CBO | 10.8 | **5.9** |
| Average RFC | 35.2 | **26.9** |
| Average LCOM | 13.8 | **7.4** |
| Average LCOM, non-UI classes | 16.5 | **4.0** |

The codebase grew about 3x in features (budgets, AI suggestions, migrations, CSV quoting),
yet the average class got smaller, less coupled and more cohesive. Responsibilities were
split into small classes with one job each, instead of growing the existing ones.

## What the numbers drove

- **The metrics changed the design, not just described it.** The first version of the
  refactored data layer was one `ExpenseRepository` with **LCOM 149**: its budget queries
  and its expense CRUD shared nothing but the connection. It was split into `Database`
  (shared plumbing), `ExpenseRepository` and `BudgetRepository`; their LCOM is now **11, 0 and 0**.
- **The data layer no longer leaks a UI library.** `DatabaseManager` returned JFreeChart's
  `DefaultPieDataset`, so the database code depended on a charting library. It now returns
  a plain `Map<String, Double>`, and JFreeChart is gone from the project.
- **New logic is isolated in low-coupling classes.** `BudgetService` (CBO 1), `RetryPolicy` (0),
  `CircuitBreaker` (1) and `Json` (0) depend on almost nothing, which is why each is
  unit-tested in milliseconds without a database, network or UI.
- **`CategorySuggester` has the highest afferent coupling (Ca 7)** of the new classes. That is
  intended: it is a small, stable interface that the cache, fallback, LLM and keyword
  implementations all depend on, rather than on each other.

## Honest caveats

- **`MainViewController` got worse (CBO 31 to 37, LCOM 33 to 51).** It now also runs the
  budget panel and set-budget dialog. Most of its coupling is to JavaFX control classes,
  which is normal for a UI controller, but the low cohesion is a real signal. The next
  refactor would extract a `BudgetPanelController` (its own FXML + controller) and a
  `ChartPanel`, leaving the main controller to coordinate.
- **Averages are flattered by small classes.** Splitting one class into several always lowers
  averages. Look at the hotspots, not only the mean: after the change the highest LCOM
  outside the UI is `BudgetSummary` (16, a record whose accessors CKJM counts as methods).
- **`DatabaseManager`'s "before" CBO of 2 looks good but hid a problem.** It was all static
  methods, so every controller was hard-wired to it and nothing could be tested. CBO counts
  type references; it does not measure testability.


## Update: after the month view, search, categories and UI redesign (1.2)

Raw output: [`metrics/ckjm-after-1.2.txt`](metrics/ckjm-after-1.2.txt).

| | 1.1 | 1.2 |
|---|--:|--:|
| Production classes (top-level) | 24 | 36 |
| Automated tests | 82 | 112 |
| Average WMC | 5.6 | 1.5 |
| Average CBO | 5.9 | 32.2 |
| Average LCOM | 7.4 | 2.7 |
| `MainViewController` WMC / CBO / LCOM | 15 / 37 / 51 | 26 / 58 / 181 |

**The metrics stopped a bad design from shipping.** The first version of the redesign put month
navigation, filtering, keyboard shortcuts, row handling and four dialogs into
`MainViewController`, which jumped to **WMC 41, CBO 84, LCOM 576**. That was split into
`ExpenseTable` (the table, filter and row interactions), `SummaryStrip`, `BudgetPanel`,
`BudgetDialog` and `SettingsDialog`, bringing it back to **26 / 58 / 181**.

**Honest caveats:**
- `MainViewController` is still worse than in 1.1, because the window genuinely does more.
  Most of its coupling is to JavaFX control types, which every controller has.
- CKJM counts each lambda body as a method of the enclosing class. 6 of `MainViewController`'s
  25 methods, 12 of `ExpenseTable`'s 22 and 12 of `CategoryManagerDialog`'s 20 are lambdas
  (event handlers), which inflates their WMC and LCOM.
- LCOM in CKJM grows roughly with the square of the method count, so UI classes with many
  small handlers score badly even when they have one job.
- Average coupling rose from 5.9 to 32.2 mostly because the new classes are UI components.
  The data and domain classes added in 1.2 stay low: `CategoryRepository` CBO 3,
  `MonthSummary` 6, `ExpenseFilter` 5.

### 1.2 classes

| Class | WMC | DIT | NOC | CBO | RFC | LCOM | Ca | NPM |
|---|--:|--:|--:|--:|--:|--:|--:|--:|
| `AddExpenseController` | 15 | 1 | 0 | 29 | 77 | 29 | 2 | 4 |
| `Alerts` | 5 | 1 | 0 | 4 | 13 | 10 | 5 | 0 |
| `BudgetDialog` | 3 | 1 | 0 | 9 | 33 | 3 | 1 | 0 |
| `BudgetPanel` | 3 | 6 | 0 | 11 | 33 | 3 | 1 | 2 |
| `BudgetRepository` | 5 | 1 | 0 | 3 | 30 | 0 | 5 | 5 |
| `BudgetService` | 2 | 1 | 0 | 1 | 5 | 1 | 2 | 2 |
| `BudgetSummary` | 9 | 2 | 0 | 5 | 15 | 16 | 3 | 9 |
| `CategoryManagerDialog` | 20 | 1 | 0 | 33 | 90 | 130 | 1 | 0 |
| `CategoryRepository` | 9 | 1 | 0 | 3 | 44 | 6 | 6 | 6 |
| `CsvExporter` | 3 | 1 | 0 | 2 | 18 | 3 | 1 | 1 |
| `DataAccessException` | 1 | 4 | 0 | 0 | 2 | 0 | 6 | 1 |
| `Database` | 5 | 1 | 0 | 3 | 26 | 8 | 5 | 2 |
| `Expense` | 6 | 1 | 0 | 4 | 15 | 5 | 6 | 6 |
| `ExpenseFilter` | 14 | 2 | 0 | 5 | 35 | 73 | 1 | 9 |
| `ExpenseRepository` | 8 | 1 | 0 | 4 | 36 | 0 | 4 | 7 |
| `ExpenseTable` | 23 | 1 | 0 | 45 | 111 | 217 | 1 | 0 |
| `MainApp` | 7 | 2 | 0 | 21 | 44 | 17 | 1 | 4 |
| `MainViewController` | 26 | 1 | 0 | 58 | 146 | 181 | 1 | 1 |
| `MoneyFormat` | 4 | 1 | 0 | 1 | 9 | 0 | 7 | 4 |
| `MonthSummary` | 12 | 2 | 0 | 6 | 23 | 42 | 2 | 12 |
| `SchemaMigrator` | 5 | 1 | 0 | 1 | 28 | 4 | 1 | 0 |
| `SettingsDialog` | 4 | 1 | 0 | 6 | 25 | 6 | 1 | 0 |
| `SettingsRepository` | 5 | 1 | 0 | 3 | 23 | 4 | 5 | 4 |
| `SummaryStrip` | 5 | 6 | 0 | 11 | 38 | 6 | 1 | 2 |
| `Theme` | 4 | 1 | 0 | 3 | 12 | 0 | 6 | 0 |
| `suggest.AsyncSuggester` | 8 | 1 | 0 | 3 | 28 | 4 | 3 | 4 |
| `suggest.CachingCategorySuggester` | 4 | 1 | 0 | 4 | 19 | 0 | 2 | 2 |
| `suggest.CategorySuggester` | 1 | 1 | 0 | 0 | 1 | 0 | 7 | 1 |
| `suggest.CircuitBreaker` | 5 | 1 | 0 | 1 | 10 | 0 | 2 | 5 |
| `suggest.FallbackCategorySuggester` | 2 | 1 | 0 | 1 | 5 | 0 | 1 | 2 |
| `suggest.HttpTransport` | 4 | 1 | 0 | 4 | 31 | 6 | 2 | 2 |
| `suggest.Json` | 4 | 1 | 0 | 0 | 19 | 6 | 1 | 0 |
| `suggest.KeywordCategorySuggester` | 4 | 1 | 0 | 1 | 25 | 4 | 1 | 2 |
| `suggest.LlmCategorySuggester` | 8 | 1 | 0 | 13 | 56 | 14 | 1 | 1 |
| `suggest.RetryPolicy` | 5 | 1 | 0 | 0 | 12 | 0 | 2 | 4 |
| `suggest.Suggesters` | 4 | 1 | 0 | 10 | 23 | 6 | 1 | 2 |

## Before (original project)

| Class | WMC | DIT | NOC | CBO | RFC | LCOM | Ca | NPM |
|---|--:|--:|--:|--:|--:|--:|--:|--:|
| `AddExpenseController` | 6 | 1 | 0 | 10 | 34 | 0 | 1 | 3 |
| `DatabaseManager` | 8 | 1 | 0 | 2 | 38 | 28 | 3 | 8 |
| `Expense` | 6 | 1 | 0 | 4 | 15 | 5 | 3 | 6 |
| `MainApp` | 3 | 2 | 0 | 7 | 15 | 3 | 0 | 3 |
| `MainViewController` | 10 | 1 | 0 | 31 | 74 | 33 | 0 | 1 |

## After 1.1

| Class | WMC | DIT | NOC | CBO | RFC | LCOM | Ca | NPM |
|---|--:|--:|--:|--:|--:|--:|--:|--:|
| `AddExpenseController` | 14 | 1 | 0 | 26 | 72 | 19 | 2 | 3 |
| `Alerts` | 5 | 1 | 0 | 2 | 11 | 10 | 3 | 0 |
| `BudgetRepository` | 5 | 1 | 0 | 3 | 30 | 0 | 4 | 5 |
| `BudgetService` | 2 | 1 | 0 | 1 | 5 | 1 | 2 | 2 |
| `BudgetSummary` | 9 | 2 | 0 | 5 | 15 | 16 | 2 | 9 |
| `CsvExporter` | 3 | 1 | 0 | 2 | 18 | 3 | 1 | 1 |
| `DataAccessException` | 1 | 4 | 0 | 0 | 2 | 0 | 4 | 1 |
| `Database` | 6 | 1 | 0 | 3 | 29 | 11 | 3 | 3 |
| `Expense` | 6 | 1 | 0 | 4 | 15 | 5 | 4 | 6 |
| `ExpenseRepository` | 7 | 1 | 0 | 3 | 37 | 0 | 4 | 7 |
| `MainApp` | 7 | 2 | 0 | 18 | 39 | 17 | 1 | 4 |
| `MainViewController` | 15 | 1 | 0 | 37 | 117 | 51 | 1 | 1 |
| `SchemaMigrator` | 5 | 1 | 0 | 1 | 27 | 4 | 1 | 0 |
| `suggest.AsyncSuggester` | 8 | 1 | 0 | 3 | 28 | 4 | 3 | 4 |
| `suggest.CachingCategorySuggester` | 4 | 1 | 0 | 4 | 19 | 0 | 2 | 2 |
| `suggest.CategorySuggester` | 1 | 1 | 0 | 0 | 1 | 0 | 7 | 1 |
| `suggest.CircuitBreaker` | 5 | 1 | 0 | 1 | 10 | 0 | 2 | 5 |
| `suggest.FallbackCategorySuggester` | 2 | 1 | 0 | 1 | 5 | 0 | 1 | 2 |
| `suggest.HttpTransport` | 4 | 1 | 0 | 4 | 31 | 6 | 2 | 2 |
| `suggest.Json` | 4 | 1 | 0 | 0 | 19 | 6 | 1 | 0 |
| `suggest.KeywordCategorySuggester` | 4 | 1 | 0 | 1 | 25 | 4 | 1 | 2 |
| `suggest.LlmCategorySuggester` | 8 | 1 | 0 | 13 | 56 | 14 | 1 | 1 |
| `suggest.RetryPolicy` | 5 | 1 | 0 | 0 | 12 | 0 | 2 | 4 |
| `suggest.Suggesters` | 4 | 1 | 0 | 10 | 23 | 6 | 1 | 2 |

## Reproduce

CKJM is built from source (https://github.com/dspinellis/ckjm) and needs BCEL and
commons-lang3 (plus the project's dependency jars) on the classpath. Class paths are passed one per line on stdin:

```bash
mvn -q compile
cd target/classes
find . -name '*.class' | sed 's|^\./||' | \
  java -cp "ckjm/out:bcel-6.8.1.jar:commons-lang3-3.20.0.jar:." gr.spinellis.ckjm.MetricsFilter
```

Output columns: class, WMC, DIT, NOC, CBO, RFC, LCOM, Ca, NPM.
