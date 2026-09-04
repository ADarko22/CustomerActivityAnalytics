# WireMock — offline LLM stub (Phase 4)

`docker-compose up wiremock` serves the canned OpenAI-shaped chat-completions response in
`mappings/openai-chat-completions.json` / `__files/openai-chat-completions-response.json`, so the AI risk
assessment demo runs fully offline (`docs/DECISIONS.md` D4). The backend's `local` profile
(`application-local.yml`) already points `spring.ai.openai.base-url` at this container.

## Recording a new stub from a real provider response (dev-only)

1. Set a real key: `export OPENAI_API_KEY=sk-...`
2. Start WireMock in record mode: `WIREMOCK_RECORD_MODE=true docker compose up wiremock` (proxies unmatched
   requests to `https://api.openai.com` and records the exchange).
3. Run the backend against this WireMock instance as usual (`application-local.yml`'s `base-url` still
   points here) and trigger a live AI risk assessment through the UI.
4. WireMock writes the newly recorded mapping + response body under its own `mappings`/`__files` — copy
   the new files from the running container (or the mounted volumes) into this folder's `mappings/` and
   `__files/`, replacing or adding to the existing stub.
5. Stop the container and restart it without `WIREMOCK_RECORD_MODE` (or set it back to unset/`false`) to
   return to normal offline replay.

If your Docker Compose version doesn't expand `${WIREMOCK_RECORD_MODE:+...}` as expected, run WireMock
directly instead: `docker run --rm -p 8089:8080 -v $(pwd)/mappings:/home/wiremock/mappings -v $(pwd)/__files:/home/wiremock/__files wiremock/wiremock:3.9.1 --record-mappings --proxy-all=https://api.openai.com`.
