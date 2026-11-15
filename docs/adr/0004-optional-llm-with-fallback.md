# 4. AI categorisation is optional, with an offline fallback

**Status:** Accepted

## Context
Suggesting a category from the description saves typing. An LLM handles free text well
("KPLC tokens", "bolt home"), but a desktop app cannot assume network access, an API key, or
that the user wants their descriptions sent to a third party.

## Decision
- With `ANTHROPIC_API_KEY` set, ask Claude; without it, use offline keyword rules. Both sit
  behind one `CategorySuggester` interface, composed as decorators:
  cache → LLM (retries + circuit breaker) → keyword fallback.
- The model's answer is accepted only if it exactly matches an existing category.
- Calls run on a background thread; only the latest request's result is applied.
- Suggestions never override a category the user picked.

## Consequences
- The feature degrades instead of breaking: offline, rate-limited or outage all fall back.
- Descriptions leave the machine only when the user has configured a key.
- More moving parts (retry policy, breaker, cache, async worker), each small and unit-tested
  with a fake HTTP transport.
