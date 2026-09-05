package io.github.adarko22.customeractivityanalytics.risk.ai;

/**
 * Port to the configured AI provider. {@link OpenAiRiskAssessmentAiClient} and {@link
 * AnthropicRiskAssessmentAiClient} are the two implementations, each active only when {@code
 * app.ai.provider} selects it (see docs/DECISIONS.md D18, D19) — this interface is the seam that
 * lets either be swapped in without changing any caller.
 */
public interface RiskAssessmentAiClient {

  /** Bumped whenever a prompt template's meaning changes — attached to logs for traceability. */
  String PROMPT_VERSION = "v1";

  ModelAssessmentResult assess(AssembledPrompt prompt);

  /** The active model name, for observability — sourced from this provider's own configuration. */
  String modelName();
}
