package io.github.adarko22.customeractivityanalytics.analytics.dto;

import io.github.adarko22.customeractivityanalytics.analytics.AnalyticsRangeProperties;

/**
 * Wire shape for a configured range↔granularity bound. Uses plain {@code String} unit names (not
 * {@code ChronoUnit} directly) so the JSON contract matches every other enum in this API
 * (upper-case {@code name()}), independent of {@code ChronoUnit}'s own overridden {@code
 * toString()}.
 */
public record RangeConstraintDto(long minAmount, String minUnit, long maxAmount, String maxUnit) {

  public static RangeConstraintDto from(AnalyticsRangeProperties.Bound bound) {
    return new RangeConstraintDto(
        bound.minAmount(), bound.minUnit().name(), bound.maxAmount(), bound.maxUnit().name());
  }
}
