# WireMock — offline LLM stub (Phase 4 / Phase 4 EXT)

`docker-compose up wiremock` serves canned, provider-shaped chat responses so the AI risk assessment
demo runs fully offline ([DECISIONS.md](../../docs/DECISIONS.md) D4), for either provider `app.ai.provider` selects
([DECISIONS.md](../../docs/DECISIONS.md) D19):

- OpenAI — [openai-chat-completions.json](mappings/openai-chat-completions.json) /
  [openai-chat-completions-response.json](__files/openai-chat-completions-response.json)
  (`POST /v1/chat/completions`). The backend's `local`
  profile ([application-local.yml](../../backend/src/main/resources/application-local.yml)) points
  `spring.ai.openai.base-url` at this container (with the `/v1` suffix — the openai-java SDK's default
  base-url already includes the path, so a full override must too).
-

Anthropic — [anthropic-messages.json](mappings/anthropic-messages.json) / [anthropic-messages-response.json](__files/anthropic-messages-response.json)
(`POST /v1/messages`). [application-local.yml](../../backend/src/main/resources/application-local.yml) points
`spring.ai.anthropic.base-url` at the same container (no `/v1` suffix — the anthropic-java SDK appends `/v1/messages`
itself).

Both mappings are always present, so switching `app.ai.provider` between `openai` and `anthropic`
never requires touching WireMock config — only the mapping matching the active provider's request
path is ever hit. Each stubbed response carries a `fixedDelayMilliseconds` of 2500ms so the SSE
`MODEL_CALL` stage is visibly in progress in the UI rather than resolving instantly, well inside the
configured `app.risk.assessment-timeout` (45s) / `sse-timeout` (50s).

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
5. Stop the container and restart it without `WIREMOCK_RECORD_MODE`/`WIREMOCK_PROXY_TARGET` to return
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
