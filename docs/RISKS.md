# Risk register

Risks to the product and its users, scored for **likelihood** and **impact** (Low / Medium / High).
Reviewed at the end of each sprint ([SPRINTS.md](SPRINTS.md)).

| ID | Risk | Likelihood | Impact | Mitigation | Status |
|---|---|---|---|---|---|
| R1 | **Data loss during an upgrade:** a schema change corrupts or empties an existing database. | Low | High | Migrations run in transactions with their version bump and roll back on failure; tested against the original schema and run against a real database. There is still no backup feature (US-19). | Mitigated, residual risk |
| R2 | **Data appears lost:** the app opens a new empty database when started from a different folder. | Medium | High | Store the database in a fixed per-user location and move the existing file once (BUG-03). | Open |
| R3 | **Platform breakage:** a JavaFX or macOS update stops the app launching (already happened once). | Medium | High | Stay on the JavaFX LTS line; CI runs on macOS as well as Linux; add Dependabot to surface updates. | Partly mitigated |
| R4 | **AI service problems:** outage, rate limits, cost, or a nonsensical answer. | Medium | Low | Retries with backoff and jitter, circuit breaker, timeouts, cache, offline keyword fallback; answers must match a real category. | Mitigated |
| R5 | **Privacy:** expense descriptions are sent to a third party without the user realising. | Low | Medium | AI is off unless the user sets an API key; the window title shows when it is on; documented in the README. | Mitigated |
| R6 | **Malicious content in exports:** a description opens as a formula in Excel (CSV injection). | Low | Medium | Prefix risky cells when exporting (BUG-02). | Open |
| R7 | **Rounding errors in totals** from storing money as `double`. | Medium | Low | Display rounds to two decimals; move to integer cents when it matters ([ADR 0007](adr/0007-money-as-double.md)). | Accepted |
| R8 | **UI not verified:** layout or styling problems that automated tests can't catch. | Medium | Medium | Review on screen before release; add UI tests (TestFX) for the main dialogs. | Open |
| R9 | **Licensing:** the original project has no licence, so reuse and redistribution rights are unclear. | High | Medium | Ask the original author to add a licence, or license only the changes and say so in the README. | Open |
| R10 | **Single maintainer:** knowledge lives with one person. | High | Medium | Decision records, architecture and metrics docs, and tests that document behaviour. | Mitigated |
