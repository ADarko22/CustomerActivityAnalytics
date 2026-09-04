package io.github.adarko22.customeractivityanalytics.risk.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record RuleContributionDto(UUID ruleId, String ruleName, BigDecimal scoreContribution) {}
