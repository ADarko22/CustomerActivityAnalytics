package io.github.adarko22.customeractivityanalytics.risk.ai;

import io.github.adarko22.customeractivityanalytics.risk.RiskFinalAssessment;
import io.github.adarko22.customeractivityanalytics.risk.RiskRule;
import java.util.List;

/**
 * Port to the configured AI provider. One concrete implementation exists today ({@link
 * OpenAiRiskAssessmentAiClient}, see docs/DECISIONS.md D18) — this interface is the seam a second
 * provider would implement, without changing any caller.
 */
public interface RiskAssessmentAiClient {

  /** Bumped whenever a prompt template's meaning changes — attached to logs for traceability. */
  String PROMPT_VERSION = "v1";

  ModelAssessmentResult assess(
      String transactionContext, List<RiskRule> candidateRules, List<RiskFinalAssessment> history);
}
