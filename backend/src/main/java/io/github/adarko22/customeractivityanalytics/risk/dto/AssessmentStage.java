package io.github.adarko22.customeractivityanalytics.risk.dto;

public enum AssessmentStage {
  PROMPT_BUILDING,
  RULE_RETRIEVAL,
  HISTORY_RETRIEVAL,
  GUARDRAIL_CHECK,
  MODEL_CALL,
  COMPLETE,
  FAILED
}
