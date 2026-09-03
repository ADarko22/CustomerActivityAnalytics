package io.github.adarko22.customeractivityanalytics.analytics;

import jakarta.annotation.PostConstruct;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configurable range↔granularity bounds for the analytics endpoint, replacing the values that used
 * to be hardcoded in {@link Granularity}. Bound values are unchanged from before (see {@code
 * application.yml}); only their storage/exposure mechanism moved.
 */
@ConfigurationProperties(prefix = "app.analytics.range-constraints")
public record AnalyticsRangeProperties(Map<Granularity, Bound> bounds) {

  @PostConstruct
  void validateAllGranularitiesConfigured() {
    for (Granularity granularity : Granularity.values()) {
      Bound bound = bounds.get(granularity);
      if (bound == null) {
        throw new IllegalStateException("Missing range constraint for granularity " + granularity);
      }
      LocalDate reference = LocalDate.EPOCH;
      LocalDate minBoundary = reference.plus(bound.minAmount(), bound.minUnit());
      LocalDate maxBoundary = reference.plus(bound.maxAmount(), bound.maxUnit());
      if (maxBoundary.isBefore(minBoundary)) {
        throw new IllegalStateException(
            "Range constraint for granularity "
                + granularity
                + " has a max span smaller than its min span");
      }
    }
  }

  public Bound boundsFor(Granularity granularity) {
    return bounds.get(granularity);
  }

  public record Bound(long minAmount, ChronoUnit minUnit, long maxAmount, ChronoUnit maxUnit) {

    public boolean isValid(LocalDate from, LocalDate to) {
      return !to.isBefore(from.plus(minAmount, minUnit))
          && !to.isAfter(from.plus(maxAmount, maxUnit));
    }
  }
}
