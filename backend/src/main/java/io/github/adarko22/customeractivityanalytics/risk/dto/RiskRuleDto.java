package io.github.adarko22.customeractivityanalytics.risk.dto;

import io.github.adarko22.customeractivityanalytics.risk.persistence.RuleScope;
import java.math.BigDecimal;
import java.util.UUID;

public record RiskRuleDto(
    UUID ruleId, String ruleName, RuleScope appliesTo, String thresholdLogic, BigDecimal weight) {}
