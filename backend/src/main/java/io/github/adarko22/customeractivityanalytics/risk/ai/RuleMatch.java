package io.github.adarko22.customeractivityanalytics.risk.ai;

import java.math.BigDecimal;
import java.util.UUID;

/** One rule the model judged as matching, with its relevance in {@code [0.00, 1.00]}. */
public record RuleMatch(UUID ruleId, BigDecimal relevance) {}
