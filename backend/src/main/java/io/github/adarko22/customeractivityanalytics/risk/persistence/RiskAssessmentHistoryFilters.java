package io.github.adarko22.customeractivityanalytics.risk.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** The AI risk-assessment history filter fields, bundled to keep call sites short. */
public record RiskAssessmentHistoryFilters(
    UUID transactionId,
    RiskLevel riskLevel,
    Instant from,
    Instant to,
    BigDecimal minScore,
    BigDecimal maxScore) {}
