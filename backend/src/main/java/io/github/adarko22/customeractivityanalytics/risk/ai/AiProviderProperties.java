package io.github.adarko22.customeractivityanalytics.risk.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Observability labels for the configured AI provider (docs/development/PHASE_4_PLAN.md
 * Clarification #4/#5) — {@code provider}/model are attached to every assessment's log line; {@code
 * recordMode} is logged once at boot so the WireMock record-mode toggle (a local-environment/docs
 * concern) is also visible from the running app.
 */
@ConfigurationProperties(prefix = "app.ai")
public record AiProviderProperties(String provider, boolean recordMode) {}
