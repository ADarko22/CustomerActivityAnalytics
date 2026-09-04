package io.github.adarko22.customeractivityanalytics.risk.dto;

import io.github.adarko22.customeractivityanalytics.risk.persistence.RuleScope;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record UpdateRiskRuleDto(
    @NotBlank String ruleName,
    @NotNull RuleScope appliesTo,
    @NotBlank String thresholdLogic,
    @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal weight) {}
