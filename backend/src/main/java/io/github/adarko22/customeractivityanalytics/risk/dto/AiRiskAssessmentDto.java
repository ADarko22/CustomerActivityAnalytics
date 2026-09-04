package io.github.adarko22.customeractivityanalytics.risk.dto;

import io.github.adarko22.customeractivityanalytics.risk.persistence.RiskLevel;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AiRiskAssessmentDto(
    UUID assessmentId,
    UUID transactionId,
    Instant triggeredAt,
    RiskLevel riskLevel,
    BigDecimal riskScore,
    String findings,
    String recommendations,
    List<RuleContributionDto> ruleContributions) {}
