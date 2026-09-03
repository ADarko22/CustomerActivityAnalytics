package io.github.adarko22.customeractivityanalytics.analytics;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/**
 * Time-bucket granularity for the analytics endpoint. Each constant owns its own UTC-based
 * bucketing behavior. Range-validity bounds are configurable (see {@link
 * AnalyticsRangeProperties}), not hardcoded here.
 */
public enum Granularity {
  DAY {
    @Override
    public LocalDate bucketStart(LocalDate date) {
      return date;
    }

    @Override
    public LocalDate next(LocalDate bucketStart) {
      return bucketStart.plusDays(1);
    }
  },
  WEEK {
    @Override
    public LocalDate bucketStart(LocalDate date) {
      return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    @Override
    public LocalDate next(LocalDate bucketStart) {
      return bucketStart.plusWeeks(1);
    }
  },
  MONTH {
    @Override
    public LocalDate bucketStart(LocalDate date) {
      return date.withDayOfMonth(1);
    }

    @Override
    public LocalDate next(LocalDate bucketStart) {
      return bucketStart.plusMonths(1);
    }
  },
  YEAR {
    @Override
    public LocalDate bucketStart(LocalDate date) {
      return date.withDayOfYear(1);
    }

    @Override
    public LocalDate next(LocalDate bucketStart) {
      return bucketStart.plusYears(1);
    }
  };

  /** The start of the bucket that {@code date} falls into. */
  public abstract LocalDate bucketStart(LocalDate date);

  /** The start of the bucket immediately following {@code bucketStart}. */
  public abstract LocalDate next(LocalDate bucketStart);
}
