# WireMock — offline LLM stub (Phase 4 / Phase 4 EXT)

`docker-compose up wiremock` serves canned, provider-shaped chat responses so the AI risk assessment
demo runs fully offline ([DECISIONS.md](../../docs/DECISIONS.md) D4), for either provider `app.ai.provider` selects
([DECISIONS.md](../../docs/DECISIONS.md) D19):

- **OpenAI** — [openai-chat-completions.json](mappings/openai-chat-completions.json) /
  [openai-chat-completions-response.json](__files/openai-chat-completions-response.json).
    - The backend's `local` profile ([application-local.yml](../../backend/src/main/resources/application-local.yml))
      points `spring.ai.openai.base-url` at this container.
- **Anthropic** — [anthropic-messages.json](mappings/anthropic-messages.json) /
  [anthropic-messages-response.json](__files/anthropic-messages-response.json).
    - Also9 `anthropic-messages-<suffix>.json` / `anthropic-messages-<suffix>.json` scenario-specific stubs, one per
      seeded demo transaction, recorded from real Anthropic Haiku 4.5 responses.
      See [PHASE_7.md](../../docs/development/PHASE_7.md)
    - The backend's `local` profile [application-local.yml](../../backend/src/main/resources/application-local.yml)
      points `spring.ai.anthropic.base-url` at the same container.

OpenAI has exactly one mapping; Anthropic has 10 (9 transaction-specific scenarios + 1 generic fallback).
Switching `app.ai.provider` between `openai` and `anthropic` (default `anthropic` since
[DECISIONS.md](../../docs/DECISIONS.md) D26).

For Anthropic, a request for one of the 9 seeded demo transactions replays that transaction's own recorded findings
regardless of how much assessment history has accumulated since it was recorded; any other transaction falls through to
the lower-priority generic fallback, so no request ever 404s. Each stubbed response carries a `fixedDelayMilliseconds`
of 2500ms so the SSE `MODEL_CALL` stage is visibly in progress in the UI rather than resolving instantly, well inside
the configured `app.risk.assessment-timeout` (45s) / `sse-timeout` (50s).

## Recording a new stub from a real provider response (dev-only)

The record-mode workflow is the same shape for both providers — set `WIREMOCK_PROXY_TARGET` to the
provider whose response you want to capture; it defaults to `https://api.openai.com` if unset.

### OpenAI

1. Set a real key: `export OPENAI_API_KEY=sk-...`
2. Start WireMock in record mode: `WIREMOCK_RECORD_MODE=true docker compose up wiremock` (proxies
   unmatched requests to `https://api.openai.com` — the default `WIREMOCK_PROXY_TARGET` — and records
   the exchange).
3. Run the backend against this WireMock instance as
   usual ([application-local.yml](../../backend/src/main/resources/application-local.yml)'s `base-url` still
   points here, and `app.ai.provider` should be `openai` or unset) and trigger a live AI risk
   assessment through the UI.
4. WireMock writes the newly recorded mapping + response body under its own `mappings`/`__files` —
   copy the new files from the running container (or the mounted volumes) into this folder's
   `mappings/` and `__files/`, replacing or adding to the existing `openai-chat-completions*` stub.
5. Stop the container and restart it without `WIREMOCK_RECORD_MODE` (or set it back to unset/`false`)
   to return to normal offline replay.

### Anthropic

1. Set a real key: `export ANTHROPIC_API_KEY=sk-ant-...`
2. Start WireMock in record mode against the Anthropic API:
   `WIREMOCK_RECORD_MODE=true WIREMOCK_PROXY_TARGET=https://api.anthropic.com docker compose up wiremock`.
3. Set `AI_PROVIDER=anthropic` on the backend (e.g. `export AI_PROVIDER=anthropic`) and restart it —
   `application-local.yml`'s `spring.ai.anthropic.base-url` already points here — then trigger a live
   AI risk assessment through the UI.
4. Copy the newly recorded `mappings`/`__files` entries into this folder, replacing or adding to the
   existing `anthropic-messages*` stub.
5. **Scope the new mapping to its transaction, not its exact byte content.** WireMock's recorder always writes a
   full-body `equalToJson` matcher — replace it by hand with a regex keyed on the transaction's own
   `transactionId` (find the UUID inside the captured `request.bodyPatterns[0].equalToJson`'s `messages[0].content`
   string):
   ```json
   {
     "bodyPatterns": [ { "matches": "(?s).*transactionId: <uuid-from-the-captured-request>.*" } ]
   }
   ```
   Without this, the stub only ever matches the exact request that produced it — the "Prior assessments" section
   embedded in every prompt grows a new timestamped entry on each re-assessment, so even replaying the *same*
   transaction a second time would never match a full-body-equality stub. There's no CLI or Admin API record-mode
   flag that produces this automatically (WireMock's recorder and its `POST /__admin/recordings/start`
   `requestBodyPattern.matcher` options — `equal-to-json`/`equal-to`/`contains` — are all whole-body strategies;
   none can extract a single field), so this is a manual edit every time. This step is Anthropic-specific — the
   OpenAI stub is a single generic mapping with no per-transaction content to scope by.
6. Stop the container and restart it without `WIREMOCK_RECORD_MODE`/`WIREMOCK_PROXY_TARGET` to return
   to normal offline replay.

If your Docker Compose version doesn't expand the `${WIREMOCK_RECORD_MODE:+...}`/
`${WIREMOCK_PROXY_TARGET:-...}` defaults as expected, run WireMock directly instead — substitute the
target/mount paths for whichever provider you're recording:

```
docker run --rm -p 8089:8080 \
  -v $(pwd)/mappings:/home/wiremock/mappings \
  -v $(pwd)/__files:/home/wiremock/__files \
  wiremock/wiremock:3.9.1 --record-mappings --proxy-all=https://api.anthropic.com
```
