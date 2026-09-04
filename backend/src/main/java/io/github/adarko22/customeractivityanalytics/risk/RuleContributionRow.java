package io.github.adarko22.customeractivityanalytics.risk;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Flat repository projection joining a {@link RiskAssessmentLineItem} to its {@link RiskRule}'s
 * name, one row per (assessment, rule) pair — grouped by {@code assessmentId} by the caller to
 * build each assessment's {@code List<RuleContributionDto>}.
 */
public record RuleContributionRow(
    UUID assessmentId, UUID ruleId, String ruleName, BigDecimal scoreContribution) {}
