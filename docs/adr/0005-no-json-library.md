# 5. No JSON library for the LLM client

**Status:** Accepted

## Context
The Claude API speaks JSON. The client needs to build one small request body and read one
field (`content[0].text`) from the response.

## Decision
Use a tiny, tested `Json` helper (string escaping and reading the first `"text"` field) and the
JDK's built-in `HttpClient`, rather than adding Jackson or the Anthropic SDK.

## Consequences
- No extra dependencies for a single call.
- The helper is deliberately narrow; its tests include escaping, unicode, and a `"type":"text"`
  value appearing before the `"text"` key.
- If the app ever needs more of the API (streaming, tool use, structured output), replace the
  helper with Jackson or the official SDK instead of extending it.
