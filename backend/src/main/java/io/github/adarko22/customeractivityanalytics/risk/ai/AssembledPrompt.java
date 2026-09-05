package io.github.adarko22.customeractivityanalytics.risk.ai;

/**
 * The fully-rendered system/user prompt pair for one assessment run — the single point at which the
 * PII guardrail scans RAG-injected content before any {@link RiskAssessmentAiClient} call.
 */
public record AssembledPrompt(String system, String user) {}
