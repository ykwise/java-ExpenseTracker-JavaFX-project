# Architecture

## Before (original project)

Every controller called static methods on one `DatabaseManager`, which also returned a
charting-library type. Nothing could be tested without the real database file and UI.

```mermaid
classDiagram
    direction LR
    class MainApp {
        +start(Stage)
    }
    class MainViewController {
        -SwingNode chartNode
        -loadChartData()
        -handleAddExpense()
        -handleEditExpense()
        -handleDeleteExpense()
        -handleExportToCSV()
    }
    class AddExpenseController {
        -handleSave()
    }
    class DatabaseManager {
        +initializeDatabase()$
        +getAllExpenses()$ List~Expense~
        +addExpense(...)$
        +updateExpense(...)$
        +deleteExpense(int)$
        +getMonthlyCategorySummary()$ DefaultPieDataset
    }
    class Expense
    class JFreeChart["JFreeChart (Swing)"]

    MainApp ..> DatabaseManager : static call
    MainViewController ..> DatabaseManager : static calls
    AddExpenseController ..> DatabaseManager : static calls
    DatabaseManager ..> JFreeChart : returns its type
    MainViewController ..> JFreeChart : embeds via SwingNode
    DatabaseManager ..> Expense
```

## After

`MainApp` is the single place that creates objects and wires them together (the
composition root). Controllers receive what they need through their constructors, so
every non-UI class can be tested on its own.

```mermaid
classDiagram
    direction TB
    class MainApp {
        +start(Stage)
        +stop()
    }
    class MainViewController
    class AddExpenseController

    class Database {
        +initialize()
        ~connect() Connection
        ~bindCurrentMonth(...)
        ~categoryId(...) int
    }
    class SchemaMigrator {
        +migrate(Connection)$
    }
    class ExpenseRepository {
        +getAllExpenses() List~Expense~
        +addExpense(...)
        +updateExpense(...)
        +deleteExpense(int)
        +getExpenses(YearMonth) List~Expense~
        +getMonthlyCategorySummary(YearMonth) Map
    }
    class CategoryRepository {
        +getAllNames() List~String~
        +add(String)
        +rename(String, String)
        +delete(String)
    }
    class SettingsRepository {
        +getCurrency() String
        +setCurrency(String)
        +moneyFormat() MoneyFormat
    }
    class BudgetRepository {
        +setBudget(String, double)
        +getBudget(String) OptionalDouble
        +getMonthlySpent(String, YearMonth) double
        +getBudgetSummaries(YearMonth) List~BudgetSummary~
    }
    class BudgetService {
        +evaluate(double, OptionalDouble)$ BudgetStatus
    }
    class BudgetSummary {
        <<record>>
    }
    class CsvExporter {
        +write(List, Writer)$
    }

    class CategorySuggester {
        <<interface>>
        +suggest(String, List) Optional~String~
    }
    class AsyncSuggester {
        +request(...)
        +cancelPending()
        +close()
    }
    class CachingCategorySuggester
    class FallbackCategorySuggester
    class LlmCategorySuggester
    class KeywordCategorySuggester
    class RetryPolicy
    class CircuitBreaker
    class HttpTransport {
        <<interface>>
    }

    MainApp --> Database : creates
    MainApp --> MainViewController : creates (controller factory)
    MainApp --> AddExpenseController : creates (controller factory)
    MainApp --> AsyncSuggester : creates, closes on exit

    MainViewController --> ExpenseRepository
    MainViewController --> BudgetRepository
    MainViewController ..> CsvExporter
    AddExpenseController --> ExpenseRepository
    AddExpenseController --> BudgetRepository
    AddExpenseController --> AsyncSuggester
    AddExpenseController ..> BudgetService

    ExpenseRepository --> Database
    BudgetRepository --> Database
    CategoryRepository --> Database
    SettingsRepository --> Database
    MainViewController --> CategoryRepository
    MainViewController --> SettingsRepository
    Database ..> SchemaMigrator
    BudgetRepository ..> BudgetSummary
    BudgetSummary ..> BudgetService

    AsyncSuggester --> CategorySuggester
    CategorySuggester <|.. CachingCategorySuggester
    CategorySuggester <|.. FallbackCategorySuggester
    CategorySuggester <|.. LlmCategorySuggester
    CategorySuggester <|.. KeywordCategorySuggester
    CachingCategorySuggester --> CategorySuggester : decorates
    FallbackCategorySuggester --> CategorySuggester : primary + fallback
    LlmCategorySuggester --> RetryPolicy
    LlmCategorySuggester --> CircuitBreaker
    LlmCategorySuggester --> HttpTransport
```

## Main window components (1.2)

`MainViewController` tracks the selected month, loads that month's data and hands it to
components that each own one part of the window.

```mermaid
flowchart TB
    MV["MainViewController<br/>selected month, refresh, open dialogs"]
    MV --> ET["ExpenseTable<br/>columns, filter pipeline, row actions"]
    ET --> EF["ExpenseFilter<br/>(pure predicate)"]
    MV --> SS["SummaryStrip"] --> MS["MonthSummary<br/>(pure arithmetic)"]
    MV --> BP["BudgetPanel"] --> BS["BudgetService<br/>(pure rules)"]
    MV --> D["BudgetDialog · SettingsDialog ·<br/>CategoryManagerDialog · AddExpense dialog"]
    MV --> CH["PieChart"]
```

Each JavaFX component is display-only; the logic it shows (`ExpenseFilter`, `MonthSummary`,
`BudgetService`) is plain Java and unit-tested without a UI.

## Saving an expense with a budget check

```mermaid
sequenceDiagram
    actor User
    participant Dialog as AddExpenseController
    participant Expenses as ExpenseRepository
    participant Budgets as BudgetRepository
    participant Rule as BudgetService

    User->>Dialog: Save (Food, 850)
    Dialog->>Dialog: validate (required fields, amount > 0)
    Dialog->>Expenses: addExpense(...)
    alt expense is dated this month
        Dialog->>Budgets: getBudget("Food")
        Dialog->>Budgets: getMonthlySpent("Food")
        Note right of Budgets: total read AFTER saving, so Add and Edit<br/>are both correct without special cases
        Dialog->>Rule: evaluate(spent, budget)
        Rule-->>Dialog: WARNING / EXCEEDED / OK / NO_BUDGET
        opt WARNING or EXCEEDED
            Dialog-->>User: Budget alert (informs, never blocks)
        end
    end
    Dialog-->>User: close dialog, refresh table, chart, budgets
```

## Category suggestion while typing

```mermaid
sequenceDiagram
    actor User
    participant UI as AddExpenseController (FX thread)
    participant Async as AsyncSuggester (worker thread)
    participant Cache as CachingCategorySuggester
    participant LLM as LlmCategorySuggester
    participant KW as KeywordCategorySuggester
    participant API as Claude API

    User->>UI: types "uber to town"
    UI->>UI: debounce 600 ms
    UI->>Async: request(#7, description)
    Async->>Cache: suggest(...)
    alt cached
        Cache-->>Async: Transport
    else not cached
        Cache->>LLM: suggest(...)
        alt circuit open
            LLM-->>Cache: empty (no network call)
        else circuit closed
            loop up to 3 attempts, backoff + jitter
                LLM->>API: POST /v1/messages (10 s timeout)
                API-->>LLM: 200 "Transport" / 429 / 5xx
            end
            LLM->>LLM: accept only an exact known category
        end
        opt LLM had no answer
            Cache->>KW: suggest(...)
        end
    end
    Async->>UI: Platform.runLater(result #7)
    UI->>UI: apply only if #7 is still the latest request<br/>and the user hasn't picked a category
```
