# Product vision

This is a software **product**, not a project: there is no external customer handing over a
specification. The developer decides what the product does, who it is for and when it changes
(Sommerville, 2020). The vision below is what those decisions are measured against.

## Vision statement

> **For** people managing their own monthly spending, often in Kenyan shillings,
> **who** want to know where their money went and whether they are within the limits they set,
> **Expense Tracker** is a desktop app
> **that** records expenses in seconds, shows each month at a glance, and warns before a budget runs out.
> **Unlike** spreadsheets, which need building and maintaining, or cloud apps, which want an account
> and your data on their servers,
> **it** works offline on your own computer, keeps everything in one local file, and only uses AI
> if you choose to turn it on.

## The three questions

**What is the product?**
A desktop expense tracker with monthly budgets per category, a month-by-month view with a summary
and chart, search and filtering, and optional AI that suggests a category from what you type.

**Who are the target users?**
Individuals tracking personal spending on their own computer, who want structure (categories,
budgets) without the effort of a spreadsheet. The defaults, such as KES and keywords like "matatu",
"KPLC" and "boda", reflect a Nairobi user, but currency and categories are configurable.

**Why should they choose it?**
- **Fast entry:** today's date is filled in, the category is suggested from the description, and
  ⌘N opens the form from anywhere.
- **Early warning:** budgets turn amber at 80% and red when exceeded, and a message appears the
  moment an expense crosses the line.
- **Private by default:** no account and no server. Descriptions leave the machine only if the user
  sets an API key.
- **Trustworthy data:** the database enforces its own rules (constraints, foreign keys) and
  upgrades itself safely between versions.

## Out of scope (for now)

These would change who the product is for or how it is built, so they are deliberately excluded.
See [BACKLOG.md](BACKLOG.md) for the full "Won't have" list.

- Syncing between devices or sharing with a household (would need a server and accounts).
- Connecting to bank or M-Pesa statements.
- Business accounting: invoices, tax, multi-currency conversion.

## Reference

Sommerville, I. (2020) *Engineering Software Products: An Introduction to Modern Software
Engineering*. Harlow: Pearson.
